package com.cobblecompanion.cobbledollars.client;

import java.util.ArrayList;
import java.util.List;

/** Zuletzt vom Server empfangener Transaktions-Verlauf (Wallet-Tab, siehe com.cobblecompanion.data.TransactionLogManager in CobbleCompanion: Basis). */
public class ClientTransactionLogHelper {

    public record Entry(int type, String amount, String counterpart) {}

    private static List<Entry> entries = List.of();

    public static void setEntries(List<String> raw) {
        List<Entry> parsed = new ArrayList<>();
        for (String line : raw) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) continue;
            try {
                int type = Integer.parseInt(parts[0]);
                parsed.add(new Entry(type, parts[1], parts[2]));
            } catch (NumberFormatException ignored) {}
        }
        entries = parsed;
    }

    public static List<Entry> getEntries() {
        return entries;
    }
}
