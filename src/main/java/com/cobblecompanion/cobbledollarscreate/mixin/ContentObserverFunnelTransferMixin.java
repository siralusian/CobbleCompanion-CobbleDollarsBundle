package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverRewardBridge;
import com.cobblecompanion.integrations.ModAvailability;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nutzer-Vorgabe: bei Messingschleusen (Brass Funnels), die ein Item AN einen benachbarten
 * Schlauen Beobachter übergeben, soll die Auszahlung die ECHTE Stapelgröße berücksichtigen (Live-
 * Test-Fund: activate() kennt die Stückzahl nicht, siehe ContentObserverActivateMixin-Kommentar).
 * Block.onFunnelTransfer(Level, BlockPos, ItemStack) wird von JEDEM Trichter aufgerufen, der
 * erfolgreich ein Item transferiert hat - mit dem ECHTEN ItemStack (inkl. echter Anzahl!). Creates
 * eigene Implementierung auf SmartObserverBlock sucht in einem eigenen (privaten Lambda)
 * Nachbar-Scan nach einem passenden Beobachter und ruft nur activate(4) auf, ohne die Stückzahl
 * weiterzureichen - hier wird derselbe Nachbar-Scan (6 Richtungen, siehe javap-Bytecode-Analyse)
 * bewusst NOCHMAL selbst durchlaufen (statt die private synthetische Lambda-Methode fragil per
 * Mixin zu treffen), um an genau dieser Stelle Zugriff auf den echten ItemStack zu haben.
 */
@Mixin(SmartObserverBlock.class)
public abstract class ContentObserverFunnelTransferMixin {

    @Inject(method = "onFunnelTransfer", at = @At("HEAD"))
    private void cobblecompanion$onFunnelTransfer(Level level, BlockPos funnelPos, ItemStack stack, CallbackInfo ci) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (stack.isEmpty()) return;

        for (Direction direction : Iterate.directions) {
            BlockPos neighborPos = funnelPos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof SmartObserverBlock)) continue;
            if (SmartObserverBlock.getTargetDirection(neighborState) != direction.getOpposite()) continue;
            if (!(level.getBlockEntity(neighborPos) instanceof SmartObserverBlockEntity)) continue;

            ContentObserverConfigManager.Entry cfg = ContentObserverConfigManager.get(serverLevel.dimension(), neighborPos);
            if (cfg == null) continue;

            // Nutzer-Vorgabe: Wildcard-/Tag-Muster - siehe ContentObserverBeltRewardMixin-Kommentar.
            if (!ContentObserverConfigManager.matches(cfg.itemId, stack)) continue;

            ContentObserverRewardBridge.rewardForItems(serverLevel, cfg, stack.getCount());
        }
    }
}
