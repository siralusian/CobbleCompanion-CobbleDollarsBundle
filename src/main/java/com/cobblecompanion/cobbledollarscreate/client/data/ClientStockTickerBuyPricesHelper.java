package com.cobblecompanion.cobbledollarscreate.client.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen Stand der Ankaufspreise (siehe StockTickerBuyPricesSyncPacket). */
public class ClientStockTickerBuyPricesHelper {

    private static boolean paymentRequired = false;
    private static Map<String, Long> prices = new HashMap<>();

    public static void set(boolean paymentRequired, List<String> entries) {
        ClientStockTickerBuyPricesHelper.paymentRequired = paymentRequired;
        Map<String, Long> parsed = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq < 0) continue;
            try {
                parsed.put(entry.substring(0, eq), Long.parseLong(entry.substring(eq + 1)));
            } catch (NumberFormatException ignored) {}
        }
        prices = parsed;
    }

    public static boolean isPaymentRequired() {
        return paymentRequired;
    }

    /** null, wenn kein Preis bekannt (Item nicht in der Liste oder Ankauf für dieses Item deaktiviert). */
    public static Long priceFor(String itemId) {
        return prices.get(itemId);
    }
}
