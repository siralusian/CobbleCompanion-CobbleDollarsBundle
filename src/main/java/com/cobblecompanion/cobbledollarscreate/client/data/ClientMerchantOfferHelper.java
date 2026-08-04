package com.cobblecompanion.cobbledollarscreate.client.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reiner Datenhalter (siehe ClientCreateStockTickerHelper-Klassenkommentar für den Grund, warum
 * Packets nie direkt Screen-Klassen referenzieren dürfen) für den MerchantOfferEditScreen-
 * Anfrage/Antwort-Zyklus: requestEdit() merkt sich den Kontext (Ticker-Position zum Zurückkehren,
 * Merchant-Name, bereits client-seitig bekanntes Netzwerk-Angebot - beides muss NICHT vom Server
 * erneut gesendet werden, siehe MerchantOfferEditScreen-Klassenkommentar) VOR dem Versand von
 * MerchantOfferRequestPacket; onSync() trägt die Server-Antwort nach und erhöht den
 * Versionszähler, den ClientEventHandler abfragt.
 */
public class ClientMerchantOfferHelper {

    private static BlockPos tickerPos = null;
    private static UUID merchantUuid = null;
    private static String merchantName = "";
    private static List<String> networkOfferItemIds = new ArrayList<>();
    private static Map<String, String> networkCategoriesByItemId = Map.of();

    private static boolean active = false;
    private static List<String> customOfferItemIds = new ArrayList<>();
    private static int version = 0;

    public static void requestEdit(BlockPos tickerPos, UUID merchantUuid, String merchantName,
            List<String> networkOfferItemIds, Map<String, String> networkCategoriesByItemId) {
        ClientMerchantOfferHelper.tickerPos = tickerPos;
        ClientMerchantOfferHelper.merchantUuid = merchantUuid;
        ClientMerchantOfferHelper.merchantName = merchantName;
        ClientMerchantOfferHelper.networkOfferItemIds = networkOfferItemIds;
        ClientMerchantOfferHelper.networkCategoriesByItemId = networkCategoriesByItemId;
    }

    public static void onSync(UUID syncedMerchantUuid, boolean active, List<String> customOfferItemIds) {
        // Antwort gehört nicht (mehr) zur aktuell angefragten Bearbeitung (z.B. Screen zwischenzeitlich
        // geschlossen und ein anderer Merchant angefragt) - ignorieren.
        if (merchantUuid == null || !merchantUuid.equals(syncedMerchantUuid)) return;
        ClientMerchantOfferHelper.active = active;
        ClientMerchantOfferHelper.customOfferItemIds = customOfferItemIds;
        version++;
    }

    public static BlockPos getTickerPos() { return tickerPos; }
    public static UUID getMerchantUuid() { return merchantUuid; }
    public static String getMerchantName() { return merchantName; }
    public static List<String> getNetworkOfferItemIds() { return networkOfferItemIds; }
    public static Map<String, String> getNetworkCategoriesByItemId() { return networkCategoriesByItemId; }
    public static boolean isActive() { return active; }
    public static List<String> getCustomOfferItemIds() { return customOfferItemIds; }
    public static int getVersion() { return version; }
}
