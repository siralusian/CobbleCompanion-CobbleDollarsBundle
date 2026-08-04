package com.cobblecompanion.cobbledollarscreate.mixin;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.CreateStockTickerBridge;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.data.TransactionLogManager;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
import fr.harmex.cobbledollars.common.network.handlers.server.SellHandler;
import fr.harmex.cobbledollars.common.network.packets.c2s.SellPacket;
import fr.harmex.cobbledollars.common.world.inventory.BankMenu;
import fr.harmex.cobbledollars.common.world.item.trading.CobbleDollarsShopHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Einzige Stelle, die Bytecode in CobbleDollars injiziert für den Verkaufsfall (siehe
 * cobblecompanion_cobbledollars_create.mixins.json, requiredMods=["cobbledollars","create"]).
 * Ersetzt CobbleDollars' eigene Verkaufslogik vollständig (Original wird per ci.cancel()
 * unterbunden, BEVOR CobbleDollars irgendetwas aus dem Bank-Container löscht oder bezahlt):
 * Nutzer-Vorgabe "der Merchant muss quasi alles annehmen egal ob es dafür Cobbledollars gibt oder
 * nicht" - JEDES Item im Bank-Container wird angekauft (nicht nur CobbleDollars' eigene
 * getBankableItems-Filterung, die an dessen globaler Bank-Config hängt), Preis pro Item aus der
 * EINEN zentralen Preisliste (CentralItemPriceManager, 0 wenn nicht gelistet statt Ablehnung).
 * Danach Weiterleitung an das mit dem Merchant verknüpfte Lager (CobbleMerchantSellManager), falls
 * vorhanden.
 *
 * Das "Verkauf"-Flag pro Item (CentralItemPriceManager.isSellEnabled) blockiert den Verkauf NICHT
 * - Nutzer-Klarstellung: Items sollen immer an/verkaufbar bleiben, das Flag steuert nur, ob dabei
 * der eingestellte Preis gilt (deaktiviert = 0 Cobbledollars, nicht "abgelehnt").
 *
 * Nutzer-Vorgabe: die ganze Überschreibung greift NUR für Merchants mit einem verknüpften
 * Lagerticker (CobbleMerchantSellManager.getEffectiveBuyTickerLink) - ohne Ticker-Verknüpfung soll
 * CobbleDollars komplett unverändert laufen (dessen eigene Bankable-Items-Prüfung, dessen eigenes
 * Preissystem), als gäbe es unsere Mod für diesen Merchant nicht. Vorher wurde ohne Verknüpfung
 * einfach auf die "default"-Preisliste zurückgefallen - das war der Bug, den dieser Umbau behebt.
 */
@Mixin(SellHandler.class)
public abstract class SellHandlerMixin {

    @Inject(method = "handle(Lfr/harmex/cobbledollars/common/network/packets/c2s/SellPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"), cancellable = true)
    private void cobblecompanion$sellEverything(SellPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!(player.containerMenu instanceof BankMenu bankMenu)) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Verkauf von {} abgefangen, aber kein BankMenu offen ({})",
                player.getName().getString(), player.containerMenu);
            return;
        }
        CobbleDollarsShopHolder holder = bankMenu.getCobbleMerchant();
        if (holder == null) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Verkauf von {}: BankMenu hat keinen CobbleMerchant gesetzt", player.getName().getString());
            return;
        }
        UUID merchantUuid = holder.getMerchantUUID();
        if (merchantUuid == null) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Verkauf von {}: Merchant hat keine merchantUUID", player.getName().getString());
            return;
        }

        // Nutzer-Vorgabe: ohne verknüpften Lagerticker (oder ohne installiertes Create, dann kann es
        // ohnehin keinen geben) greift unsere Überschreibung gar nicht erst - CobbleDollars' eigene
        // Verkaufslogik läuft unverändert weiter (kein ci.cancel()).
        CobbleMerchantSellManager.Target tickerLink = CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid);
        if (tickerLink == null) {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Verkauf von {} an Merchant {}: kein Lagerticker verknüpft - CobbleDollars-Standardverhalten (keine Überschreibung)",
                player.getName().getString(), merchantUuid);
            return;
        }

        var dropPos = (holder instanceof Entity entity) ? entity.blockPosition() : player.blockPosition();
        SimpleContainer bankContainer = bankMenu.getBankContainer();

        // Nutzer-Vorgabe: mehrere Preislisten, wählbar pro Lagerticker-Netzwerk - der Merchant nutzt
        // die seinem verknüpften Netzwerk zugewiesene Liste.
        String listId = CreateStockTickerBridge.resolveListIdForMerchant(server, merchantUuid);

        List<ItemStack> toDeliver = new ArrayList<>();
        BigInteger total = BigInteger.ZERO;

        var items = bankContainer.getItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            // Nutzer-Vorgabe: das Item wird IMMER angenommen/verkauft, das "Verkauf"-Flag steuert
            // nur, ob dabei der eingestellte Preis gilt (deaktiviert = 0, nicht "verboten").
            long unitPrice = CentralItemPriceManager.isSellEnabled(listId, itemId) ? CentralItemPriceManager.sellPriceForItem(listId, itemId) : 0L;
            total = total.add(BigInteger.valueOf(unitPrice).multiply(BigInteger.valueOf(stack.getCount())));
            toDeliver.add(stack.copy());
            bankContainer.setItem(i, ItemStack.EMPTY);
        }

        CobbleCompanionDollarsCreate.LOGGER.info("[CC] Ankauf von {} an Merchant {}: {} Stack(s), Gesamtpreis={}",
            player.getName().getString(), merchantUuid, toDeliver.size(), total);
        ci.cancel();
        if (toDeliver.isEmpty()) return;

        if (total.signum() > 0) {
            CobbleDollarsBridge.grant(player, total);
            TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.MERCHANT_SOLD, total.toString(), null);
        }

        // Drei Fälle (Nutzer-Vorgabe): Lager verknüpft -> immer dorthin liefern (unabhängig von
        // einer Ticker-Verknüpfung). Kein Lager, aber ein Ticker verknüpft -> Items am Merchant
        // fallen lassen statt sie zu verwerfen. Weder Lager noch Ticker verknüpft -> Items sind
        // an dieser Stelle bereits aus dem Bank-Container entfernt, also einfach gelöscht (Altverhalten).
        //
        // Bugfix: "effective"-Varianten statt der rohen CobbleMerchantSellManager-Verknüpfung
        // verwenden - sonst wird eine per CustomNPCs-Trader-Verknüpfung (CustomNpcTraderLinkManager,
        // z.B. wenn dieser Merchant eigentlich ein im CobbleMerchant-Modus handelnder CustomNPC
        // ist) gesetzte Kisten-/Ticker-Verknüpfung hier nie gefunden, obwohl die Ticker-Prüfung
        // weiter oben (getEffectiveBuyTickerLink) exakt dieselbe Verknüpfung bereits akzeptiert hat.
        if (CobbleMerchantSellManager.getEffectiveLink(merchantUuid) != null) {
            boolean delivered = CobbleMerchantSellManager.deliver(server, merchantUuid, toDeliver, dropPos);
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Weiterleitung für Merchant {}: verknüpft={}", merchantUuid, delivered);
        } else if (CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid) != null) {
            CobbleMerchantSellManager.dropOnGround(player.serverLevel(), toDeliver, dropPos);
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Merchant {}: kein Lager verknüpft, aber Ticker - Items bei {} fallen gelassen",
                merchantUuid, dropPos);
        } else {
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Merchant {}: weder Lager noch Ticker verknüpft - Items gelöscht", merchantUuid);
        }

        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.merchant_sold", "Sold for %s Cobbledollars.", CobbleDollarsScale.formatRaw(total)));
    }
}
