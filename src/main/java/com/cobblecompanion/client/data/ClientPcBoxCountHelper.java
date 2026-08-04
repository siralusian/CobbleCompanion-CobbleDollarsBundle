package com.cobblecompanion.client.data;

/**
 * Client-seitiger Spiegel von Cobblemons konfigurierter PC-Box-Anzahl (per PcBoxCountSyncPacket
 * empfangen). canEdit gibt an, ob der lokale Spieler OP ist und die Zahl im Settings-Tab ändern
 * darf. Rein flüchtig - wird bei jedem Login/Änderung neu vom Server gesetzt, nicht persistiert.
 */
public class ClientPcBoxCountHelper {

    private static int boxCount = 0;
    private static boolean canEdit = false;

    public static void apply(int boxCount, boolean canEdit) {
        ClientPcBoxCountHelper.boxCount = boxCount;
        ClientPcBoxCountHelper.canEdit = canEdit;
    }

    public static int getBoxCount() { return boxCount; }
    public static boolean canEdit() { return canEdit; }
}
