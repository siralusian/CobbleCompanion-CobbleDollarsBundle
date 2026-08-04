package com.cobblecompanion.cobbledollarscreate.client.data;

import com.cobblecompanion.cobbledollarscreate.network.SaleRecipientSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Reiner Datenhalter für den zuletzt vom Server empfangenen Verkaufserlös-Empfänger-Zustand des
 * gerade bearbeiteten Netzwerks (siehe SaleRecipientSyncPacket) - gleiches Muster wie
 * ClientLinkedMerchantsHelper.
 */
public class ClientSaleRecipientHelper {

    private static String mode = "NONE";
    private static String recipientName = "";
    private static List<SaleRecipientSyncPacket.EntityRecipientEntry> entityRecipients = new ArrayList<>();

    public static void set(SaleRecipientSyncPacket packet) {
        mode = packet.mode();
        recipientName = packet.recipientName();
        entityRecipients = packet.entityRecipients();
    }

    public static String getMode() {
        return mode;
    }

    public static String getRecipientName() {
        return recipientName;
    }

    public static List<SaleRecipientSyncPacket.EntityRecipientEntry> getEntityRecipients() {
        return entityRecipients;
    }
}
