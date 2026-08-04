package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.cobblecompanion.cobbledollarscreate.network.StockTickerBuyPricesSyncPacket;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperCategoryMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nutzer-Vorgabe: "Preisschild im Lagerticker-Bestellmenü" - sobald ein Spieler einen Lagerticker
 * öffnet, bekommt er einmalig die aktuelle Ankaufs-Preisliste UND ob DIESER Ticker Bezahlung
 * verlangt gesendet (siehe StockTickerBuyPricesSyncPacket) - für die client-seitige
 * Preisanzeige-Overlay (StockTickerPriceOverlay) auf Create's Bestell-Screen.
 *
 * Live-Diagnose (siehe run/logs): das serverseitig tatsächlich geöffnete Menü ist
 * StockKeeperCategoryMenu, nicht StockKeeperRequestMenu - die Kategorie-Auswahl (die zum
 * StockKeeperRequestScreen mit itemsToOrder führt) läuft rein clientseitig als Screen-Swap ab,
 * ohne ein zweites serverseitiges openMenu. Beide Menü-Typen erben contentHolder identisch von
 * MenuBase<StockTickerBlockEntity>.
 *
 * Bewusst über PlayerContainerEvent.Open statt Mixin in Create's Menü-Öffnen-Logik - vermeidet
 * einen weiteren Mixin, das Event feuert für JEDES geöffnete Menü ohnehin schon.
 */
public class StockTickerOrderScreenPriceHandler {

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof StockKeeperCategoryMenu menu)) return;
        if (!(menu.contentHolder instanceof StockTickerBlockEntity blockEntity)) return;
        if (!(blockEntity.getLevel() instanceof ServerLevel)) return;

        // Nutzer-Vorgabe: Bezahlpflicht gilt pro Netzwerk (freqId), nicht mehr pro Ticker-Block.
        boolean paymentRequired = blockEntity.behaviour != null && CreateStockTickerPriceManager.isEnabled(blockEntity.behaviour.freqId);

        // Nutzer-Vorgabe: mehrere Preislisten, wählbar pro Netzwerk (siehe CentralItemPriceManager).
        String listId = CentralItemPriceManager.getListIdForNetwork(blockEntity.behaviour != null ? blockEntity.behaviour.freqId : null);
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> price : CentralItemPriceManager.getBuyPrices(listId).entrySet()) {
            if (!CentralItemPriceManager.isBuyEnabled(listId, price.getKey())) continue;
            entries.add(price.getKey() + "=" + price.getValue());
        }

        PacketDistributor.sendToPlayer(player, new StockTickerBuyPricesSyncPacket(paymentRequired, entries));
    }
}
