package com.cobblecompanion.cobbledollarscreate.mixin.create;

import com.cobblecompanion.cobbledollarscreate.MerchantStockSyncHelper;
import com.cobblecompanion.integrations.create.CreateNetworkStockHelper;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantPayoutManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.data.PendingCobbleDollarsManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import fr.harmex.cobbledollars.common.CobbleDollars;
import fr.harmex.cobbledollars.common.network.handlers.server.BuyHandler;
import fr.harmex.cobbledollars.common.network.packets.c2s.BuyPacket;
import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import fr.harmex.cobbledollars.common.world.item.trading.CobbleDollarsShopHolder;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Category;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Offer;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Shop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Nutzer-Vorgabe: "das Verkauf-System vom CustomNPC auch für den CobbleMerchant einbauen - nur
 * verkaufen wenn mit Lagerticker verbunden, nur wenn das Item im Lager ist". Betrifft echte
 * CobbleMerchant-Entities (UND per EntityNPCInterfaceMerchantMixin - CC:CobbleDollars/CustomNPCs -
 * auch CustomNPCs im CobbleMerchant-Modus, beide laufen über denselben BuyHandler). Verknüpfung:
 * NICHT neu, sondern die bereits bestehende CobbleMerchantSellManager.buyTickerLink (Strg+Rechtsklick
 * auf einen Lagerticker, siehe CobbleMerchantLinkInteractionHandler) - bisher nur ein Verhaltens-
 * Schalter für die Verkaufsrichtung (Item am Merchant fallen lassen statt löschen), jetzt zusätzlich
 * Pflicht-Lager für die Kaufrichtung, auf Nutzer-Wunsch (keine zweite Verknüpfung nötig).
 *
 * Ansatzpunkt: BuyHandler.handle(BuyPacket, MinecraftServer, ServerPlayer) - per javap verifiziert.
 * Injiziert an HEAD und dupliziert bewusst NUR den Teil von CobbleDollars' eigener Mengen-
 * Berechnung, der für unseren Bestands-Check nötig ist (getMaxAmountObtainable-Deckelung + Offer.
 * stock-Deckelung inkl. dessen "stock==0 -> gar kein Kauf"-Sonderfall) - damit unsere Netzwerk-
 * Entnahme GARANTIERT dieselbe Menge entnimmt, die CobbleDollars gleich darauf tatsächlich
 * herausgibt (sonst Dupe-/Verlust-Risiko durch abweichende Mengen). Kein Ticker verknüpft ->
 * unverändertes CobbleDollars-Verhalten (Feature nur für explizit verknüpfte Merchants aktiv).
 */
@Mixin(BuyHandler.class)
public abstract class BuyHandlerMixin {

    // Von HEAD nach TAIL durchgereicht (siehe cobblecompanion$afterBuy) - BuyHandler ist ein
    // Singleton, aber der Server verarbeitet Pakete sequenziell auf dem Hauptthread, daher hier
    // unproblematisch als einfaches Instanzfeld statt ThreadLocal.
    @Unique
    private BigInteger cobblecompanion$lastPurchasePrice;

    // Nutzer-Vorgabe: Verkaufserlöse an den konfigurierten Netzwerk-/Entity-Empfänger auszahlen
    // (siehe CobbleMerchantPayoutManager) - ebenfalls von HEAD nach TAIL durchgereicht, null wenn
    // kein Empfänger konfiguriert ist (Altverhalten: Geld verschwindet einfach).
    @Unique
    private UUID cobblecompanion$recipientUuid;
    @Unique
    private int cobblecompanion$purchaseAmount;
    @Unique
    private String cobblecompanion$purchaseItemName;

    @Inject(method = "handle(Lfr/harmex/cobbledollars/common/network/packets/c2s/BuyPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"), cancellable = true)
    private void cobblecompanion$checkStockAndExtract(BuyPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!packet.getHasMerchant()) return;
        UUID merchantUuid = packet.getMerchantUUID();
        if (merchantUuid == null) return;

        CobbleMerchantSellManager.Target tickerTarget = CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid);
        if (tickerTarget == null) return; // Nicht verknüpft -> Feature inaktiv, Vanilla-Verhalten.

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tickerTarget.dimension()));
        ServerLevel tickerLevel = server.getLevel(dimensionKey);
        BlockPos tickerPos = new BlockPos(tickerTarget.x(), tickerTarget.y(), tickerTarget.z());
        if (tickerLevel == null || !(tickerLevel.getBlockEntity(tickerPos) instanceof StockTickerBlockEntity ticker) || ticker.behaviour == null) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_trade_ticker_missing", "The linked stock ticker is no longer present."));
            ci.cancel();
            return;
        }

        ServerLevel playerLevel = player.serverLevel();
        Entity entity = playerLevel.getEntity(merchantUuid);
        if (!(entity instanceof CobbleDollarsShopHolder holder)) return;

        // Exakt CobbleDollars' eigene Mengen-Berechnung nachbilden (siehe javap-Analyse von
        // BuyHandler.handle), damit unsere Entnahme und die tatsächlich herausgegebene Menge
        // garantiert übereinstimmen.
        ItemStack offerItem = packet.getOffer().getItem();
        int amount = Math.min(packet.getAmount(), PlayerExtensionKt.getMaxAmountObtainable(player, offerItem));

        Shop shop = holder.getShop();
        if (shop == null || shop.isEmpty()) shop = CobbleDollars.INSTANCE.getShopConfig().getDefaultShop();
        Offer offer = null;
        if (shop != null && packet.getCategoryIndex() >= 0 && packet.getCategoryIndex() < shop.size()) {
            Category category = shop.get(packet.getCategoryIndex());
            if (category != null) {
                var offers = category.getOffers();
                if (offers != null && packet.getOfferIndex() >= 0 && packet.getOfferIndex() < offers.size()) {
                    offer = offers.get(packet.getOfferIndex());
                }
            }
        }
        if (offer == null || !offer.equals(packet.getOffer())) return; // Mismatch -> Vanilla-Ablauf entscheidet selbst.

        if (offer.getStock() == 0) return; // Ausverkauft laut eigenem Bestandssystem - Vanilla bricht selbst ab.
        if (offer.getStock() > 0) amount = Math.min(amount, offer.getStock());
        if (amount <= 0) return;

        int available = ticker.getRecentSummary().getCountOf(offerItem);
        if (available < amount) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_trade_out_of_stock", "Not enough stock in the linked network (%s needed, %s available).",
                String.valueOf(amount), String.valueOf(available)));
            ci.cancel();
            return;
        }

        if (!CreateNetworkStockHelper.extract(ticker.behaviour.freqId, offerItem, amount)) {
            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.merchant_trade_out_of_stock", "Not enough stock in the linked network (%s needed, %s available).",
                String.valueOf(amount), String.valueOf(available)));
            ci.cancel();
            return;
        }

        // Entnahme erfolgreich - der Original-Methodenkörper läuft jetzt weiter und übergibt die
        // Items/bucht das Geld ab. Menge+Preis für Wallet-Log/Bestands-Refresh in TAIL merken.
        cobblecompanion$lastPurchasePrice = offer.getPrice().multiply(BigInteger.valueOf(amount));
        cobblecompanion$recipientUuid = CobbleMerchantPayoutManager.resolveRecipient(ticker.behaviour.freqId, merchantUuid);
        cobblecompanion$purchaseAmount = amount;
        cobblecompanion$purchaseItemName = offerItem.getHoverName().getString();
    }

    @Inject(method = "handle(Lfr/harmex/cobbledollars/common/network/packets/c2s/BuyPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("TAIL"))
    private void cobblecompanion$afterBuy(BuyPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        // Nutzer-Vorgabe: Kaufinterface soll sich sofort aktualisieren (kein Schließen/Neuöffnen
        // nötig) + Käufe sollen im Wallet-Log auftauchen - beides nur für tatsächlich über unser
        // Netzwerk-System abgewickelte Käufe (lastPurchasePrice nur bei Erfolg in HEAD gesetzt).
        if (cobblecompanion$lastPurchasePrice == null) return;
        BigInteger price = cobblecompanion$lastPurchasePrice;
        UUID recipientUuid = cobblecompanion$recipientUuid;
        int purchaseAmount = cobblecompanion$purchaseAmount;
        String itemName = cobblecompanion$purchaseItemName;
        cobblecompanion$lastPurchasePrice = null;
        cobblecompanion$recipientUuid = null;

        UUID merchantUuid = packet.getMerchantUUID();
        if (merchantUuid == null) return;
        Entity entity = player.serverLevel().getEntity(merchantUuid);
        if (entity instanceof CobbleDollarsShopHolder holder) {
            MerchantStockSyncHelper.sync(player, holder);
        }

        TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.MERCHANT_BOUGHT, price.toString(), null);

        // Nutzer-Vorgabe: Verkaufserlöse gehen an den konfigurierten Shop-Betreiber statt einfach
        // zu verschwinden (siehe CobbleMerchantPayoutManager). Online -> sofort gutschreiben,
        // offline -> Warteschlange (direktes Editieren des Offline-Kontostands wird von
        // CobbleDollars beim nächsten Login überschrieben, siehe PendingCobbleDollarsManager).
        if (recipientUuid != null) {
            ServerPlayer recipient = player.server.getPlayerList().getPlayer(recipientUuid);
            if (recipient != null) {
                CobbleDollarsBridge.grant(recipient, price);
            } else {
                PendingCobbleDollarsManager.addPending(recipientUuid, price.longValue());
            }
            String counterpart = player.getGameProfile().getName() + TransactionLogManager.SALE_DETAIL_DELIMITER + itemName
                + TransactionLogManager.SALE_DETAIL_DELIMITER + purchaseAmount;
            TransactionLogManager.addEntry(recipientUuid, TransactionLogManager.SALE_DETAIL, price.toString(), counterpart);
        }
    }
}
