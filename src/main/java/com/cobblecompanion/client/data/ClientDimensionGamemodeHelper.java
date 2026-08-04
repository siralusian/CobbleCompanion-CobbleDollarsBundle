package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hält den zuletzt vom Server empfangenen Stand der Dimension-Gamemode-Regeln (Settings > Gamemodes Listeneditor). */
public class ClientDimensionGamemodeHelper {

    private static Map<String, String> rules = new LinkedHashMap<>();
    private static List<String> availableDimensions = new ArrayList<>();

    // Transiente Auswahl für die "Neue Regel"-Zeile im Listeneditor (Settings > Gamemodes) - rein
    // clientseitige UI-Komposition, nicht persistiert, nicht mit dem Server synchronisiert.
    private static int newRuleDimensionIndex = 0;
    private static int newRuleModeIndex = 0;

    public static int getNewRuleDimensionIndex() { return newRuleDimensionIndex; }
    public static void setNewRuleDimensionIndex(int index) { newRuleDimensionIndex = index; }
    public static int getNewRuleModeIndex() { return newRuleModeIndex; }
    public static void setNewRuleModeIndex(int index) { newRuleModeIndex = index; }

    public static void setStatus(List<String> ruleEntries, List<String> dimensions) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String entry : ruleEntries) {
            int idx = entry.indexOf('=');
            if (idx < 0) continue;
            parsed.put(entry.substring(0, idx), entry.substring(idx + 1));
        }
        rules = parsed;
        availableDimensions = new ArrayList<>(dimensions);
    }

    /** Sortierte Ansicht (stabile Reihenfolge für Zeilen-Index <-> Entfernen-Button, siehe CompanionScreen). */
    public static List<Map.Entry<String, String>> getSortedRules() {
        List<Map.Entry<String, String>> list = new ArrayList<>(rules.entrySet());
        list.sort(Map.Entry.comparingByKey());
        return list;
    }

    public static List<String> getAvailableDimensions() {
        return availableDimensions;
    }
}
