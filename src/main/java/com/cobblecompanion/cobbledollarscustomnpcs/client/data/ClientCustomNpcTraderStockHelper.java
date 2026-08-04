package com.cobblecompanion.cobbledollarscustomnpcs.client.data;

import java.util.List;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen Netzwerk-Lagerbestand pro Verkaufsslot eines CustomNPCs-Traders (siehe CustomNpcTraderStockSyncPacket). */
public class ClientCustomNpcTraderStockHelper {

    private static List<Integer> available = List.of();

    public static void set(List<Integer> available) {
        ClientCustomNpcTraderStockHelper.available = available;
    }

    /** -1, wenn kein Item in diesem Slot bzw. keine Daten vorhanden. */
    public static int get(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= available.size()) return -1;
        return available.get(slotIndex);
    }
}
