package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält die zuletzt im Such-Tab verwendeten Suchbegriffe (neueste zuerst, älteste fallen beim
 * Nachrücken raus). Rein clientseitig/im Speicher - kein Server-Sync und keine Persistenz über
 * Sitzungen hinweg nötig (Nutzer-Wunsch: "verfallen die ältesten Suchen").
 */
public class ClientSearchHistoryHelper {

    private static final int MAX_ENTRIES = 10;
    private static final List<String> history = new ArrayList<>();

    /** Fügt einen Suchbegriff vorne ein (dedupliziert case-insensitiv, verschiebt Duplikate nach vorne). */
    public static void addSearch(String term) {
        if (term == null || term.isBlank()) return;
        String trimmed = term.trim();
        history.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
        history.add(0, trimmed);
        while (history.size() > MAX_ENTRIES) {
            history.remove(history.size() - 1);
        }
    }

    public static List<String> getHistory() {
        return history;
    }
}
