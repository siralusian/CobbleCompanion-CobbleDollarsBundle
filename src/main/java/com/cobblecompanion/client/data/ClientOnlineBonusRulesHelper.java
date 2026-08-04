package com.cobblecompanion.client.data;

import java.util.ArrayList;
import java.util.List;

/** Reiner Datenhalter für den zuletzt vom Server empfangenen Stand der Online-Belohnung-Boni (siehe OnlineBonusRulesSyncPacket). */
public class ClientOnlineBonusRulesHelper {

    private static List<String> entries = new ArrayList<>();

    public static void setEntries(List<String> entries) {
        ClientOnlineBonusRulesHelper.entries = entries;
    }

    public static List<String> getEntries() {
        return entries;
    }
}
