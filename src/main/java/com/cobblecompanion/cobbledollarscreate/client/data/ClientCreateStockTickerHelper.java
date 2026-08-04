package com.cobblecompanion.cobbledollarscreate.client.data;

import com.cobblecompanion.cobbledollarscreate.network.PriceListPayload;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für den zuletzt vom Server empfangenen Lagerticker-Preis-Editor-Stand (siehe
 * CreateStockTickerPricesSyncPacket) - referenziert bewusst KEINE Screen-Klasse. Packets dürfen
 * niemals direkt net.minecraft.client.gui.screens.Screen (oder eine Unterklasse davon)
 * importieren, sonst versucht der RuntimeDistCleaner beim Laden der Payload-Klasse auf dem
 * Dedicated Server (passiert schon bei der Paket-TYPE-Registrierung, nicht erst beim Ausführen)
 * eine client-only Klasse zu laden und der Server crasht (bereits einmal live erlebt). Das
 * eigentliche Öffnen des Screens passiert stattdessen in ClientEventHandler (dort schon korrekt
 * per @EventBusSubscriber(Dist.CLIENT) abgesichert), die per Versionszähler pollt.
 *
 * Nutzer-Vorgabe: mehrere Preislisten - `lists` enthält ALLE bekannten Listen komplett, `currentListId`
 * die diesem Ticker-Netzwerk aktuell zugewiesene (Dropdown wechselt rein client-seitig zwischen ihnen).
 */
public class ClientCreateStockTickerHelper {

    private static BlockPos pos = null;
    private static boolean enabled = false;
    private static String currentListId = "default";
    private static List<PriceListPayload> lists = new ArrayList<>();
    private static List<String> availableItemIds = new ArrayList<>();
    private static int version = 0;

    public static void setPendingPrices(BlockPos pos, boolean enabled, String currentListId,
                                         List<PriceListPayload> lists, List<String> availableItemIds) {
        ClientCreateStockTickerHelper.pos = pos;
        ClientCreateStockTickerHelper.enabled = enabled;
        ClientCreateStockTickerHelper.currentListId = currentListId;
        ClientCreateStockTickerHelper.lists = lists;
        ClientCreateStockTickerHelper.availableItemIds = availableItemIds;
        version++;
    }

    public static BlockPos getPos() { return pos; }
    public static boolean isEnabled() { return enabled; }
    public static String getCurrentListId() { return currentListId; }
    public static List<PriceListPayload> getLists() { return lists; }
    public static List<String> getAvailableItemIds() { return availableItemIds; }
    public static int getVersion() { return version; }
}
