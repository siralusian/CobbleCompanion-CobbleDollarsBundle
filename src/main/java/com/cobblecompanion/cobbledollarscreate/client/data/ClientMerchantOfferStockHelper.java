package com.cobblecompanion.cobbledollarscreate.client.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen Netzwerk-Lagerbestand pro Angebot eines CobbleMerchant-Shops (siehe MerchantOfferStockSyncPacket). */
public class ClientMerchantOfferStockHelper {

    private static Map<Long, Integer> available = new HashMap<>();

    public static void set(List<Integer> categoryIndices, List<Integer> offerIndices, List<Integer> values) {
        Map<Long, Integer> parsed = new HashMap<>();
        for (int i = 0; i < categoryIndices.size() && i < offerIndices.size() && i < values.size(); i++) {
            parsed.put(key(categoryIndices.get(i), offerIndices.get(i)), values.get(i));
        }
        available = parsed;
    }

    /** null, wenn kein Netzwerk-Bestand für dieses Angebot bekannt ist (nicht verknüpft). */
    public static Integer get(int categoryIndex, int offerIndex) {
        return available.get(key(categoryIndex, offerIndex));
    }

    private static long key(int categoryIndex, int offerIndex) {
        return (long) categoryIndex * 100000L + offerIndex;
    }
}
