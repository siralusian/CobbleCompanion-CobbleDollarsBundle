package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für den zuletzt vom Server empfangenen Stand der Gamemode-Inventar-Trennung
 * (Ein/Aus-Status für den Settings-Toggle, siehe GamemodeInventorySyncStatusPacket) und der
 * eigenen ausstehenden Abhol-Warteschlange (siehe GamemodeInventoryReclaimSyncPacket).
 */
public class ClientGamemodeInventoryHelper {

    private static boolean enabled = false;
    private static List<ReclaimEntryView> reclaimEntries = new ArrayList<>();

    public record ReclaimEntryView(String gameTypeName, int itemCount) {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setReclaimEntries(List<ReclaimEntryView> entries) {
        reclaimEntries = entries;
    }

    public static List<ReclaimEntryView> getReclaimEntries() {
        return reclaimEntries;
    }
}
