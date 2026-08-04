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
 */
@Mixin(SmartObserverBlockEntity.class)
public abstract class ContentObserverChuteRewardMixin {

    private static final Map<ItemStack, Boolean> cobblecompanion$rewarded = new WeakHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblecompanion$onTick(CallbackInfo ci) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = self.getBlockPos();

        ContentObserverConfigManager.Entry cfg = ContentObserverConfigManager.get(serverLevel.dimension(), pos);
        if (cfg == null) return;

        BlockState state = self.getBlockState();
        Direction targetDir = SmartObserverBlock.getTargetDirection(state);
        BlockPos neighborPos = pos.relative(targetDir);
        if (!(serverLevel.getBlockEntity(neighborPos) instanceof ChuteBlockEntity chute)) return;

        ItemStack current = chute.getItem();
        if (current.isEmpty()) return;

        // Nutzer-Vorgabe: Wildcard-/Tag-Muster - siehe ContentObserverBeltRewardMixin-Kommentar.
        if (!ContentObserverConfigManager.matches(cfg.itemId, current)) return;

        if (cobblecompanion$rewarded.putIfAbsent(current, Boolean.TRUE) == null) {
            ContentObserverRewardBridge.rewardForItems(serverLevel, cfg, current.getCount());
        }
    }
}
