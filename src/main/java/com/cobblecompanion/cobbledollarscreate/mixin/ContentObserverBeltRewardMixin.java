package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverRewardBridge;
import com.cobblecompanion.integrations.ModAvailability;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nutzer-Vorgabe: bei Förderband-Beobachtung soll die echte Stapelgröße jedes Items GENAU EINMAL
 * gezählt werden (Live-Test-Fund: Creates eigener activate()-Aufruf für den Förderband-Fall feuert
 * mehrfach pro physischem Item, solange es im 0,45-0,55-Fenster liegt - siehe
 * ContentObserverActivateMixin-Kommentar). Läuft die IDENTISCHE öffentliche API
 * (handleCenteredProcessingOnAllItems) wie Creates eigener Tick NOCHMAL selbst, aber mit einem
 * WeakHashMap-basierten "schon belohnt"-Merker pro TransportedItemStack-OBJEKT (Objekt-Identität
 * bleibt stabil, solange das Item dieses eine Förderband-Segment durchquert; WeakHashMap räumt den
 * Eintrag automatisch weg, sobald das Item das Segment verlässt und keine andere Referenz mehr
 * existiert - kein manuelles Aufräumen nötig). @At("HEAD") statt TAIL/RETURN: tick() hat mehrere
 * frühe Rückgabepunkte je nach erkanntem Nachbar-Typ, HEAD feuert garantiert bei jedem Tick.
 */
@Mixin(SmartObserverBlockEntity.class)
public abstract class ContentObserverBeltRewardMixin {

    private static final Map<TransportedItemStack, Boolean> cobblecompanion$rewarded = new WeakHashMap<>();

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
        TransportedItemStackHandlerBehaviour belt = BlockEntityBehaviour.get(serverLevel, neighborPos, TransportedItemStackHandlerBehaviour.TYPE);
        if (belt == null) return; // kein Förderband am Nachbarn - siehe ContentObserverActivateMixin für den generischen Fallback

        // Nutzer-Vorgabe: Wildcard-/Tag-Muster (siehe ContentObserverConfigManager.matches-
        // Kommentar) - Creates eigene FilteringBehaviour kennt nur konkrete Einzel-Items, deshalb
        // hier direkt gegen das konfigurierte Muster statt gegen die FilteringBehaviour geprüft.
        belt.handleCenteredProcessingOnAllItems(0.45f, transported -> {
            if (ContentObserverConfigManager.matches(cfg.itemId, transported.stack)
                    && cobblecompanion$rewarded.putIfAbsent(transported, Boolean.TRUE) == null) {
                ContentObserverRewardBridge.rewardForItems(serverLevel, cfg, transported.stack.getCount());
            }
            return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
        });
    }
}
