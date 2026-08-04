package com.cobblecompanion.cobbledollarscreate.client.data;

import net.minecraft.core.BlockPos;

/**
 * Reiner Datenhalter für den zuletzt vom Server empfangenen Schlaue-Beobachter-Editor-Stand (siehe
 * ContentObserverConfigSyncPacket) - referenziert bewusst KEINE Screen-Klasse, gleiches Muster wie
 * ClientCreateStockTickerHelper (siehe dessen Kommentar für das WARUM).
 */
public class ClientContentObserverHelper {

    private static BlockPos pos = null;
    private static String itemId = "";
    private static String targetPlayerName = "";
    private static long amountPerItem = 0;
    private static int version = 0;

    public static void setPending(BlockPos pos, String itemId, String targetPlayerName, long amountPerItem) {
        ClientContentObserverHelper.pos = pos;
        ClientContentObserverHelper.itemId = itemId;
        ClientContentObserverHelper.targetPlayerName = targetPlayerName;
        ClientContentObserverHelper.amountPerItem = amountPerItem;
        version++;
    }

    public static BlockPos getPos() { return pos; }
    public static String getItemId() { return itemId; }
    public static String getTargetPlayerName() { return targetPlayerName; }
    public static long getAmountPerItem() { return amountPerItem; }
    public static int getVersion() { return version; }
}
