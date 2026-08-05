package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverRewardBridge;
import com.cobblecompanion.integrations.ModAvailability;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nutzer-Vorgabe: reiner Schacht (ohne Förderband/Trichter) soll ebenfalls exakt gezählt werden
 * (Live-Test-Fund: "Schacht ohne Schleuse" gab nur 1 statt 128, "Schacht mit Schleuse" nur 2 statt
 * 128 - die generische activate()-Flanken-Erkennung in ContentObserverActivateMixin sieht einen
 * dauerhaften Item-Strom als EIN einziges "Signal wird aktiv"-Ereignis). Bytecode-Analyse von
 * ChuteBlockEntity#handleDownwardOutput zeigt: beim Weiterreichen an den NAECHSTEN Schacht wird das
 * ItemStack-Objekt per Referenz durchgereicht (targetChute.setItem(this.item, ...), keine Kopie) -
 * exakt wie beim Foerderband ist die Objekt-Identitaet also stabil, solange der Stack im
 * beobachteten Schacht liegt. Deshalb hier: direkter Zugriff auf ChuteBlockEntity#getItem() des
 * Nachbar-Schachts, WeakHashMap-Merker pro ItemStack-Objekt (wie ContentObserverBeltRewardMixin),
 * Belohnung mit echter Stueckzahl genau einmal pro Stack-Objekt.
 *
 * Bugfix (Nutzer-Fund, 4. Live-Test - der eigentliche Grund, warum Abziehen NIE funktionierte):
 * {@code cobblecompanion$rewarded} war fälschlich STATISCH - eine einzige, über ALLE Schlauen
 * Beobachter im ganzen Spiel GETEILTE Map. Da dasselbe ItemStack-OBJEKT (siehe oben, per Referenz
 * durchgereicht) beim Herabfallen durch mehrere Schächte MEHRERE Beobachter passiert (z.B. Zähler
 * an Schacht 2, Abzieher an Schacht 4), markierte der ZÄHLER das Objekt bereits als "belohnt" -
 * der Abzieher weiter unten sah dasselbe Objekt, fand es schon in der (geteilten!) Map und rief
 * ContentObserverRewardBridge.handleDetectedItems NIE auf. Jetzt eine Instanz-Map PRO Beobachter-
 * Block (kein "static" mehr) - jeder Block führt seine eigene, unabhängige Buchführung.
 */
@Mixin(SmartObserverBlockEntity.class)
public abstract class ContentObserverChuteRewardMixin {

    private final Map<ItemStack, Boolean> cobblecompanion$rewarded = new WeakHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblecompanion$onTick(CallbackInfo ci) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = self.getBlockPos();

        ContentObserverConfigManager.BlockConfig cfg = ContentObserverConfigManager.get(serverLevel.dimension(), pos);
        if (cfg == null) return;

        BlockState state = self.getBlockState();
        Direction targetDir = SmartObserverBlock.getTargetDirection(state);
        BlockPos neighborPos = pos.relative(targetDir);
        if (!(serverLevel.getBlockEntity(neighborPos) instanceof ChuteBlockEntity chute)) return;

        ItemStack current = chute.getItem();
        if (current.isEmpty()) return;

        // Nutzer-Vorgabe (mehrere Items pro Block, geteilte Zähler/Abzieher-Liste): siehe
        // ContentObserverBeltRewardMixin-Kommentar - Regel-Matching läuft zentral in
        // ContentObserverRewardBridge.
        if (cobblecompanion$rewarded.putIfAbsent(current, Boolean.TRUE) == null) {
            ContentObserverRewardBridge.handleDetectedItems(serverLevel, pos, cfg, current, current.getCount());
        }
    }
}
