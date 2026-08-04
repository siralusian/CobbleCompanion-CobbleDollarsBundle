package com.cobblecompanion.cobbledollarscreate.client.data;

import com.cobblecompanion.cobbledollarscreate.network.LinkedMerchantInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für die zuletzt vom Server empfangene Liste verknüpfter CustomNPC-Trader/
 * CobbleMerchants (siehe LinkedMerchantsSyncPacket) - referenziert bewusst KEINE Screen-Klasse,
 * gleiches Muster wie ClientCreateStockTickerHelper (RuntimeDistCleaner-Absicherung).
 */
public class ClientLinkedMerchantsHelper {

    private static List<LinkedMerchantInfo> entries = new ArrayList<>();

    public static void set(List<LinkedMerchantInfo> newEntries) {
        entries = newEntries;
    }

    public static List<LinkedMerchantInfo> getEntries() {
        return entries;
    }
}
