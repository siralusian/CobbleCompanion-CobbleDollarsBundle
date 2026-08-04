package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

/**
 * Fängt eine Lagerticker-Bestellung ab, BEVOR sie ins Logistiknetzwerk geht: wenn der Ziel-Block
 * als "designiert" konfiguriert ist (siehe CreateStockTickerPriceManager, per eigenem Preis-Editor
 * gepflegt - Strg+Rechtsklick, siehe CreateStockTickerInteractionHandler), wird der berechnete
 * Cobbledollars-Preis abgebucht; bei zu wenig Guthaben wird die Bestellung über
 * CallbackInfo.cancel() unterbunden, bevor Create irgendetwas aus dem Netzwerk entnimmt.
 *
 * Ansatzpunkt bewusst applySettings() (liefert direkt den bestellenden ServerPlayer) statt
 * StockTickerBlockEntity.broadcastPackageRequest() (kennt den Spieler nicht, könnte auch von
 * ComputerCraft/Redstone ausgelöst werden - für Bezahlung ungeeignet ohne bekannten Zahler).
 */
@Mixin(PackageOrderRequestPacket.class)
public abstract class PackageOrderRequestPacketMixin {

    @Shadow @Final private PackageOrderWithCrafts order;

    @Inject(method = "applySettings", at = @At("HEAD"), cancellable = true)
    private void cobblecompanion$checkPayment(ServerPlayer player, StockTickerBlockEntity blockEntity, CallbackInfo ci) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;

        // Nutzer-Vorgabe: Bezahlpflicht gilt pro Netzwerk (freqId), nicht mehr pro Ticker-Block -
        // ein Umschalten an EINEM Ticker wirkt sich auf alle desselben Netzwerks aus.
        if (blockEntity.behaviour == null || !CreateStockTickerPriceManager.isEnabled(blockEntity.behaviour.freqId)) {
            CobbleCompanionDollarsCreate.LOGGER.debug("[CC] Lagerticker-Bestellung bei {} ohne Bezahlpflicht", blockEntity.getBlockPos());
            return;
        }

        String listId = CentralItemPriceManager.getListIdForNetwork(blockEntity.behaviour.freqId);
        long totalCost = 0L;
        if (this.order != null) {
            for (BigItemStack entry : this.order.stacks()) {
                if (entry.stack == null || entry.stack.isEmpty() || entry.count <= 0) continue;
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).toString();
                // Nutzer-Vorgabe: das Item bleibt IMMER bestellbar, das "Ankauf"-Flag steuert nur,
                // ob dabei der eingestellte Preis gilt (deaktiviert = 0, nicht "verboten").
                if (!CentralItemPriceManager.isBuyEnabled(listId, itemId)) continue;
                long unitPrice = CentralItemPriceManager.buyPriceForItem(listId, itemId);
                if (unitPrice <= 0) continue;
                totalCost += unitPrice * entry.count;
            }
        }
        CobbleCompanionDollarsCreate.LOGGER.info("[CC] Lagerticker-Bestellung bei {} von {}: berechneter Preis={}",
            blockEntity.getBlockPos(), player.getName().getString(), totalCost);
        if (totalCost <= 0) return;

        if (!CobbleDollarsBridge.charge(player, BigInteger.valueOf(totalCost))) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.stockticker_insufficient", "Not enough Cobbledollars (%s needed).",
                CobbleDollarsScale.formatRaw(BigInteger.valueOf(totalCost))));
            ci.cancel();
            return;
        }
        TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.TICKER_PAID, String.valueOf(totalCost), null);
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.stockticker_paid", "%s Cobbledollars paid for this order.",
            CobbleDollarsScale.formatRaw(BigInteger.valueOf(totalCost))));
    }
}
