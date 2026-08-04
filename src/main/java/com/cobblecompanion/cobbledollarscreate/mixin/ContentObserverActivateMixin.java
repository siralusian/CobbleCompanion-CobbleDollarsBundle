package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverRewardBridge;
import com.cobblecompanion.integrations.ModAvailability;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
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

/**
 * Nutzer-Fund (Live-Test): activate(int) feuert NICHT einmal pro erkanntem Item, sondern
 * (mindestens) einmal pro TICK, in dem Create ein passendes Item "sieht" - beim Förderband-Pfad
 * blieb ein langsam bewegtes Item mehrere 6-Tick-Zyklen im 0,45-0,55-Erkennungsfenster und wurde so
 * MEHRFACH belohnt (Live-Test-Beweis: 768 statt 128 erwarteter Cobbledollars = exakt 6x, passend
 * zum turnOffTicks-Zyklus). Förderband UND Trichter-Übergabe (Messingschleuse) haben deshalb jetzt
 * eigene PRÄZISE Erkennungspfade mit echter Stückzahl (siehe ContentObserverBeltRewardMixin /
 * ContentObserverFunnelTransferMixin) - diese Klasse ist nur noch der FALLBACK für den generischen
 * Rest-Fall (reiner Schacht ohne Förderband/Trichter, prüft nur den Block-TYP am Nachbarn, kennt
 * keine echte Stückzahl) und zahlt deshalb nur noch auf der STEIGENDEN FLANKE des Redstone-Signals
 * (POWERED wechselt gerade von falsch auf wahr) statt bei jedem einzelnen Aufruf - deutlich weniger
 * Überzahlung als vorher, auch wenn ohne echte Stückzahl weiterhin nicht 1:1 exakt.
 *
 * Bekannte Einschränkung bei Wildcard-/Tag-Mustern (siehe ContentObserverConfigManager.matches):
 * activate(int) bekommt von Create KEINEN ItemStack übergeben, dieser Fallback-Pfad kann das
 * konfigurierte Muster deshalb nicht selbst prüfen - er verlässt sich weiterhin auf Creates eigene
 * FilteringBehaviour (die bei nicht-konkreten Mustern bewusst LEER bleibt, siehe
 * ContentObserverConfigUpdatePacket). Für "*"/leer (soll ohnehin alles triggern) ist das korrekt;
 * bei Tag-/Namespace-Mustern kann dieser Fallback-Pfad dadurch auch nicht passende Items belohnen -
 * betrifft NUR generische Nachbarn ohne Förderband/Schacht/Trichter (siehe die drei präzisen
 * Reward-Mixins, die das Muster korrekt prüfen).
 */
@Mixin(SmartObserverBlockEntity.class)
public abstract class ContentObserverActivateMixin {

    @Inject(method = "activate(I)V", at = @At("HEAD"))
    private void cobblecompanion$onActivate(int ticks, CallbackInfo ci) {
        if (ticks == 4) return; // Trichter-Übergabe - siehe ContentObserverFunnelTransferMixin (präzise)
        if (!ModAvailability.isCobbleDollarsAvailable()) return;

        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = self.getBlockPos();

        ContentObserverConfigManager.Entry cfg = ContentObserverConfigManager.get(serverLevel.dimension(), pos);
        if (cfg == null) return;

        BlockState state = self.getBlockState();
        Direction targetDir = SmartObserverBlock.getTargetDirection(state);
        BlockPos neighborPos = pos.relative(targetDir);
        // Förderband-Fall wird über ContentObserverBeltRewardMixin präzise mit echter Stückzahl
        // erfasst - hier ausschließen, sonst Doppelzahlung.
        if (BlockEntityBehaviour.get(serverLevel, neighborPos, TransportedItemStackHandlerBehaviour.TYPE) != null) return;
        // Schacht-Fall wird über ContentObserverChuteRewardMixin präzise mit echter Stückzahl
        // erfasst - hier ausschließen, sonst Doppelzahlung.
        if (serverLevel.getBlockEntity(neighborPos) instanceof ChuteBlockEntity) return;

        // Steigende Flanke: nur zahlen, wenn das Signal GERADE ERST aktiv wird, nicht bei jedem
        // "bleibt aktiv, Timer verlängert sich"-Aufruf.
        if (state.getValue(SmartObserverBlock.POWERED)) return;

        ContentObserverRewardBridge.rewardForItems(serverLevel, cfg, 1);
    }
}
