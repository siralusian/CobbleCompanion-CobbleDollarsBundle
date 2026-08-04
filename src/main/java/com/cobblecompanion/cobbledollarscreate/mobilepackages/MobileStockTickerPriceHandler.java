package com.cobblecompanion.cobbledollarscreate.mobilepackages;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.network.StockTickerBuyPricesSyncPacket;
import com.cobblecompanion.integrations.ModAvailability;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preisschild-Gegenstück zu StockTickerOrderScreenPriceHandler, aber für den mobilen Lagerticker
 * (siehe mixin.mobilepackages.SendPackageMixin - dort bereits die Bezahlpflicht umgesetzt). Kein
 * Block, keine Position, deshalb kein CreateStockTickerPriceManager.isEnabled()-Check nötig: der
 * mobile Ticker verlangt IMMER Bezahlung über die zentrale Preisliste, sobald CobbleDollars
 * installiert ist (exakt dieselbe Bedingung wie im SendPackageMixin).
 *
 * Eigenes Unterpaket statt cobbledollarscreate.client/cobbledollarscreate, weil diese Klasse
 * PortableStockTickerMenu direkt importiert (nur registriert, wenn Create UND
 * create_mobile_packages installiert sind, siehe CobbleCompanionDollarsCreate.onServerStarting).
 */
public class MobileStockTickerPriceHandler {

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof PortableStockTickerMenu)) return;
        if (!ModAvailability.isCobbleDollarsAvailable()) return;

        // Kein Block, keine Position, kein Netzwerk (siehe Klassenkommentar) -> immer "default".
        String listId = CentralItemPriceManager.DEFAULT_LIST_ID;
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Long> price : CentralItemPriceManager.getBuyPrices(listId).entrySet()) {
            if (!CentralItemPriceManager.isBuyEnabled(listId, price.getKey())) continue;
            entries.add(price.getKey() + "=" + price.getValue());
        }

        PacketDistributor.sendToPlayer(player, new StockTickerBuyPricesSyncPacket(true, entries));
    }
}
