package com.cobblecompanion.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Zentrale, rein clientseitige Registry für {@link CompanionTabExtension}s. Erweiterungsmods rufen
 * {@link #registerTab(int, CompanionTabExtension)} einmalig in ihrem Mod-Konstruktor auf (Client-
 * Dist) - ist das Jar der Erweiterung nicht installiert, läuft dieser Aufruf schlicht nie, ganz
 * ohne ModAvailability-Check nötig.
 *
 * Tab-Indizes sind die bestehenden CompanionScreen.TAB_*-Konstanten (siehe dortige Doku) - diese
 * Klasse kennt deren Bedeutung bewusst nicht, sie ist reine Ablage.
 */
public final class CompanionExtensions {

    private static final Map<Integer, CompanionTabExtension> TABS = new ConcurrentHashMap<>();

    private CompanionExtensions() {}

    public static void registerTab(int tabIndex, CompanionTabExtension extension) {
        TABS.put(tabIndex, extension);
    }

    /** @return die registrierte Extension für diesen Tab, oder null wenn keine installiert ist. */
    public static CompanionTabExtension getTab(int tabIndex) {
        return TABS.get(tabIndex);
    }

    public static boolean hasTab(int tabIndex) {
        return TABS.containsKey(tabIndex);
    }
}
