package com.cobblecompanion.client.data;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-seitiger Cache des Living-Dex+-Katalogs (siehe LivingDexPlusRegistry/
 * LivingDexPlusEnumerationRequestPacket) - statische Spieldaten, werden EINMAL pro Session vom
 * Server geholt und danach nur noch lokal gefiltert/sortiert (Kategorie-Auswahl und -Reihenfolge
 * sind reine Client-Settings, kein weiterer Roundtrip nötig).
 */
public class LivingDexPlusHelper {

    public record Entry(int categoryId, String speciesName, String formName, int dexNumber) {}

    private static List<Entry> catalog = null;
    private static boolean requestSent = false;

    /** Löst bei Bedarf die einmalige Server-Anfrage aus; catalogReady() erst danach true. */
    public static void ensureRequested() {
        if (catalog != null || requestSent) return;
        if (!ClientNetworkUtil.canSendToServer(com.cobblecompanion.network.LivingDexPlusEnumerationRequestPacket.TYPE.id())) return;
        requestSent = true;
        PacketDistributor.sendToServer(new com.cobblecompanion.network.LivingDexPlusEnumerationRequestPacket());
    }

    public static boolean isCatalogReady() {
        return catalog != null;
    }

    public static List<Entry> getCatalog() {
        return catalog != null ? catalog : List.of();
    }

    /** Von LivingDexPlusEnumerationResponsePacket aufgerufen. */
    public static void setCatalog(List<String> wireEntries) {
        List<Entry> parsed = new ArrayList<>(wireEntries.size());
        for (String line : wireEntries) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 4) continue;
            try {
                int categoryId = Integer.parseInt(parts[0]);
                int dexNumber = Integer.parseInt(parts[3]);
                parsed.add(new Entry(categoryId, parts[1], parts[2], dexNumber));
            } catch (NumberFormatException ignored) {}
        }
        catalog = parsed;
    }

    /** Alle Katalog-Einträge einer bestimmten Kategorie (siehe VariantCategory), unsortiert. */
    public static List<Entry> byCategory(int categoryId) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : getCatalog()) if (e.categoryId() == categoryId) result.add(e);
        return result;
    }

    /** Katalog-Einträge einer Regionalformen-Unterkategorie (Task "Region/Form-Unterkategorien"). */
    public static List<Entry> byCategoryAndRegion(int categoryId, String region) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : getCatalog()) {
            if (e.categoryId() == categoryId && region.equalsIgnoreCase(e.formName())) result.add(e);
        }
        return result;
    }

    /** Boxen-Bedarf einer einzelnen Region INNERHALB Kategorie 3/4 (30 Slots/Box, aufgerundet). */
    public static int boxesNeededForRegion(int categoryId, String region) {
        int count = byCategoryAndRegion(categoryId, region).size();
        return (int) Math.ceil(count / 30.0);
    }

    /** Katalog-Einträge einer Kosmetisch-Art-Unterkategorie (Task "Kosmetische Formen: Unterkategorien"). */
    public static List<Entry> byCategoryAndSpecies(int categoryId, String species) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : getCatalog()) {
            if (e.categoryId() == categoryId && species.equalsIgnoreCase(e.speciesName())) result.add(e);
        }
        return result;
    }

    /** Boxen-Bedarf einer einzelnen Art INNERHALB Kategorie 5/6 (30 Slots/Box, aufgerundet). */
    public static int boxesNeededForCosmeticSpecies(int categoryId, String species) {
        int count = byCategoryAndSpecies(categoryId, species).size();
        return (int) Math.ceil(count / 30.0);
    }

    /** Nur die Arten aus der Unterkategorien-Whitelist (siehe LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST), alphabetisch sortiert. */
    public static List<String> cosmeticSpeciesNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : getCatalog()) {
            if (e.categoryId() == 5 && com.cobblecompanion.data.LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase())) {
                names.add(e.speciesName());
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Boxen-Bedarf eines Eintrags in der Kategorie-Reihenfolge-Liste (30 Slots/Box, aufgerundet).
     * "id" ist entweder eine echte Kategorie-ID (0/1/2/5/6/7) ODER eine synthetische Pro-Region-ID
     * ODER eine synthetische Pro-Kosmetisch-Art-ID (siehe LivingDexPlusRegistry) - für die
     * gemeinsame Basis-Kategorie 5/6 zählen die Whitelist-Arten NICHT mit (die haben ihre eigene
     * Unterkategorie/Box, siehe COSMETIC_SUBCATEGORY_WHITELIST).
     */
    public static int boxesNeeded(int id) {
        if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyRegionSyntheticId(id)) {
            int realCatId = com.cobblecompanion.data.LivingDexPlusRegistry.realCategoryIdFromSyntheticId(id);
            String region = com.cobblecompanion.data.LivingDexPlusRegistry.regionNameFromSyntheticId(id);
            return boxesNeededForRegion(realCatId, region);
        }
        if (com.cobblecompanion.data.LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id)) {
            int realCatId = com.cobblecompanion.data.LivingDexPlusRegistry.realCategoryIdFromCosmeticSyntheticId(id);
            int idx = com.cobblecompanion.data.LivingDexPlusRegistry.cosmeticSpeciesIndexFromSyntheticId(id);
            List<String> species = cosmeticSpeciesNames();
            if (idx < 0 || idx >= species.size()) return 0; // Katalog hat sich seit dem Speichern geändert
            return boxesNeededForCosmeticSpecies(realCatId, species.get(idx));
        }
        if (id == 5 || id == 6) {
            int count = 0;
            for (Entry e : byCategory(id)) {
                if (!com.cobblecompanion.data.LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase())) count++;
            }
            return (int) Math.ceil(count / 30.0);
        }
        int count = byCategory(id).size();
        return (int) Math.ceil(count / 30.0);
    }

    /**
     * Gesamt-Boxen-Bedarf über alle aktivierten Kategorien - PRO Kategorie einzeln aufgerundet
     * (nicht ein gemeinsames Aufrunden über die Summe), da 2 verschiedene Kategorien sich laut
     * Box-Trennungs-Regel nie eine Box teilen (siehe PCSortHelper.ldpFlatList()/
     * LivingDexPlusLayoutHelper.buildFlatList() - Padding auf volle Boxen pro Kategorie).
     */
    public static int totalBoxesNeeded(java.util.Collection<Integer> enabledCategoryIds) {
        int total = 0;
        for (int catId : enabledCategoryIds) total += boxesNeeded(catId);
        return total;
    }
}
