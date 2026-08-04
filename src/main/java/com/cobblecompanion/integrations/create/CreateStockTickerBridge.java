package com.cobblecompanion.integrations.create;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Kleine Helfer-Fassade, damit network/-Pakete (die außerhalb der Integrations-Isolationsgrenze
 * liegen, siehe com.cobblecompanion.integrations.package-info) nicht selbst Create-Klassen
 * importieren müssen, nur um zu prüfen, ob an einer Position wirklich ein Lagerticker steht bzw.
 * welche Items in dessen Logistiknetzwerk aktuell verfügbar sind.
 *
 * Bleibt bewusst in Basis (statt komplett nach CobbleCompanion: CobbleDollars/Create zu wandern) -
 * wird auch von der (noch nicht ausgelagerten) CustomNPCs-Integration direkt referenziert
 * (siehe CustomNpcTraderLinkInteractionHandler). Die CobbleDollars-spezifische Preislisten-Auflösung
 * (ehemals resolveNetworkFreqId/resolveListIdForMerchant) lebt jetzt in CobbleCompanion:
 * CobbleDollars/Create selbst (siehe dortiges CreateStockTickerBridge), da sie
 * CobbleMerchantSellManager/CentralItemPriceManager braucht, die dorthin verschoben wurden.
 */
public final class CreateStockTickerBridge {

    private CreateStockTickerBridge() {}

    public static boolean isStockTicker(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StockTickerBlockEntity;
    }

    /**
     * Item-IDs, die aktuell im an diesem Lagerticker angeschlossenen Logistiknetzwerk verfügbar
     * sind (für den Preis-Editor - Nutzer-Vorgabe: Liste statt manueller Eingabe bei ~21.000
     * Items). Leer, wenn kein Ticker dort steht oder das Netzwerk (noch) leer ist. Nutzt Creates
     * eigenen 1-Sekunden-Cache (getRecentSummary()) statt jedes Mal neu zu scannen - synchron und
     * günstig, nur server-seitig sinnvoll (Netzwerk lebt in server-seitigen Block-Entities).
     */
    public static List<String> getAvailableItemIds(Level level, BlockPos pos) {
        List<String> ids = new ArrayList<>();
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker)) return ids;
        InventorySummary summary = ticker.getRecentSummary();
        if (summary == null || summary.isEmpty()) return ids;
        for (BigItemStack entry : summary.getStacksByCount()) {
            if (entry.stack == null || entry.stack.isEmpty()) continue;
            ids.add(BuiltInRegistries.ITEM.getKey(entry.stack.getItem()).toString());
        }
        return ids;
    }
}
