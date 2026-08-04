package com.cobblecompanion.client.data;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen ausstehenden Cobbledollars-Betrag (siehe PendingCobbleDollarsSyncPacket). */
public class ClientPendingCobbleDollarsHelper {

    private static long pending = 0L;

    public static void set(long amount) {
        pending = amount;
    }

    public static long get() {
        return pending;
    }
}
