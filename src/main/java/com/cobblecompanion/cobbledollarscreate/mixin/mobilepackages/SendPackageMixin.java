package com.cobblecompanion.cobbledollarscreate.mixin.mobilepackages;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.SendPackage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;

import java.math.BigInteger;

/**
 * Nutzer-Vorgabe: der mobile Lagerticker (nicht platzierbar) nutzt IMMER die zentrale Preisliste -
 * anders als beim normalen Lagerticker gibt es hier keinen "verlangt Bezahlung"-Schalter (kein
 * Strg+Rechtsklick-Editor möglich ohne festen Block-Standort). Fängt die Bestellung genau wie
 * PackageOrderRequestPacketMixin bei Create selbst ab: applySettings() läuft erst NACH diesem
 * Inject weiter, broadcastPackageRequest() (das Item tatsächlich aus dem Netzwerk entnimmt) also
 * erst nachdem bezahlt wurde bzw. abgebrochen wird.
 */
@Mixin(SendPackage.class)
public abstract class SendPackageMixin {

    @Shadow @Final private GenericOrder order;

    @Inject(method = "applySettings", at = @At("HEAD"), cancellable = true)
    private void cobblecompanion$checkPayment(ServerPlayer player, CallbackInfo ci) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        if (this.order == null || this.order.isEmpty()) return;

        long totalCost = 0L;
        for (GenericStack entry : this.order.stacks()) {
            if (entry == null || entry.amount() <= 0) continue;
            GenericKey key = entry.key();
            if (!(key instanceof ItemKey itemKey)) continue; // nur Item-Preise, keine Fluid-Bestellungen
            if (itemKey.stack() == null || itemKey.stack().isEmpty()) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(itemKey.stack().getItem()).toString();
            // Kein Block, keine Position, kein Netzwerk (siehe Klassenkommentar) -> immer "default".
            if (!CentralItemPriceManager.isBuyEnabled(CentralItemPriceManager.DEFAULT_LIST_ID, itemId)) continue;
            long unitPrice = CentralItemPriceManager.buyPriceForItem(CentralItemPriceManager.DEFAULT_LIST_ID, itemId);
            if (unitPrice <= 0) continue;
            totalCost += unitPrice * entry.amount();
        }
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
        CobbleCompanionDollarsCreate.LOGGER.info("[CC] Mobiler Lagerticker-Bestellung von {}: berechneter Preis={}",
            player.getName().getString(), totalCost);
    }
}
