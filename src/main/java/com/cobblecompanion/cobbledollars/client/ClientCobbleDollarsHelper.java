package com.cobblecompanion.cobbledollars.client;

import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;

import java.math.BigInteger;

/** Hält den zuletzt vom Server empfangenen Cobbledollars-Kontostand des lokalen Spielers (Rohwert, siehe CobbleDollarsScale). */
public class ClientCobbleDollarsHelper {

    private static String balance = "0";

    public static void setBalance(String value) {
        balance = value;
    }

    public static String getBalance() {
        return balance;
    }

    /** Ein-Nachkommastellen-Anzeige (z.B. "1234.5") - robust gegen kaputte/leere Werte. */
    public static String getFormattedBalance() {
        try {
            return CobbleDollarsScale.formatRaw(new BigInteger(balance));
        } catch (NumberFormatException e) {
            return balance;
        }
    }
}
