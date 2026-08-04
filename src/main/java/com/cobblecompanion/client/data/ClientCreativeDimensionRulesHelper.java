package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.List;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen Stand der Creative-Kauf-Dimensionsregeln (siehe CreativeDimensionRulesSyncPacket). */
public class ClientCreativeDimensionRulesHelper {

    private static List<String> entries = new ArrayList<>();

    public static void setEntries(List<String> entries) {
        ClientCreativeDimensionRulesHelper.entries = entries;
    }

    public static List<String> getEntries() {
        return entries;
    }
}
