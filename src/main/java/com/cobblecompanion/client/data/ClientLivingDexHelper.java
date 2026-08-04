package com.cobblecompanion.client.data;

import com.cobblecompanion.CobbleCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.*;

public class ClientLivingDexHelper {

    private static Set<String> livingDexSpecies = new HashSet<>();
    private static int livingDexCount = 0;
    private static int caughtCount = 0;
    private static int seenCount = 0;
    private static boolean dataLoaded = false;

    /** Bequemlichkeits-Overload: lädt ohne Regional-Dex-Filter (National/global). */
    public static void loadData() {
        loadData(null);
    }

    /**
     * Lädt die Pokedex-Daten vom ClientPokedexManager, optional gefiltert auf einen
     * Regional-Dex (z.B. Kanto). Muss vom Render-Thread aufgerufen werden.
     *
     * @param region Aktuell im Pokedex-GUI gewählter Dex (Cobblemon's availableRegions-Eintrag),
     *               oder null für ungefiltert (alle Species).
     */
    public static void loadData(ResourceLocation region) {
        try {
            // CobblemonClient ist das echte Kotlin-Singleton (hat INSTANCE),
            // ClientPokedexManager selbst ist nur eine normale Datenklasse ohne INSTANCE-Feld.
            Class<?> clientClass = Class.forName(
                "com.cobblemon.mod.common.client.CobblemonClient");
            java.lang.reflect.Field instanceField = clientClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            Object cobblemonClient = instanceField.get(null);

            Method getPokedexData = clientClass.getMethod("getClientPokedexData");
            Object manager = getPokedexData.invoke(cobblemonClient);
            if (manager == null) {
                CobbleCompanion.LOGGER.warn("[CC] ClientPokedexManager (getClientPokedexData) is null");
                return;
            }

            int newSeenCount;
            int newCaughtCount;
            Set<String> regionSpecies = null;

            if (region != null) {
                regionSpecies = getRegionSpeciesNames(region);
                // Cobblemon berechnet Seen/Caught pro Dex bereits selbst - dieselbe
                // Quelle nutzen, die auch Cobblemon's eigener Original-Counter verwendet.
                newCaughtCount = getDexCalculatedValue(manager, region, "com.cobblemon.mod.common.api.pokedex.CaughtCount");
                newSeenCount = getDexCalculatedValue(manager, region, "com.cobblemon.mod.common.api.pokedex.SeenCount");
            } else {
                Method getRecords = manager.getClass().getMethod("getSpeciesRecords");
                Object recordsMap = getRecords.invoke(manager);
                int[] counts = countFromRecords(recordsMap);
                newSeenCount = counts[0];
                newCaughtCount = counts[1];
            }

            int newLivingCount = regionSpecies != null
                ? (int) livingDexSpecies.stream().filter(regionSpecies::contains).count()
                : livingDexSpecies.size();

            if (newSeenCount != seenCount || newCaughtCount != caughtCount || newLivingCount != livingDexCount) {
                CobbleCompanion.LOGGER.info("[CC] Pokedex data updated: seen=" + newSeenCount
                    + " caught=" + newCaughtCount + " living=" + newLivingCount
                    + " region=" + (region != null ? region : "all"));
            }
            seenCount = newSeenCount;
            caughtCount = newCaughtCount;
            livingDexCount = newLivingCount;
            dataLoaded = true;

        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Failed to load pokedex data", e);
        }
    }

    /** Ruft AbstractPokedexManager.getDexCalculatedValue(region, CALCULATOR.INSTANCE) auf. */
    private static int getDexCalculatedValue(Object manager, ResourceLocation region, String calculatorClassName) throws Exception {
        Class<?> calculatorClass = Class.forName(calculatorClassName);
        java.lang.reflect.Field instanceField = calculatorClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object calculatorInstance = instanceField.get(null);

        Class<?> calculatorInterface = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexValueCalculator");
        Method getDexCalculatedValue = manager.getClass().getMethod(
            "getDexCalculatedValue", ResourceLocation.class, calculatorInterface);
        Object result = getDexCalculatedValue.invoke(manager, region, calculatorInstance);
        return result instanceof Number n ? n.intValue() : 0;
    }

    /** Liefert alle Species-Namen (lowercase Resource-Path) im gewählten Regional-/National-Dex. */
    private static Set<String> getRegionSpeciesNames(ResourceLocation region) throws Exception {
        Class<?> dexesClass = Class.forName("com.cobblemon.mod.common.api.pokedex.Dexes");
        java.lang.reflect.Field instanceField = dexesClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object dexesInstance = instanceField.get(null);

        Method getDexEntryMap = dexesClass.getMethod("getDexEntryMap");
        Object mapObj = getDexEntryMap.invoke(dexesInstance);
        if (!(mapObj instanceof Map<?, ?> map)) return Set.of();

        Object pokedexDef = map.get(region);
        if (pokedexDef == null) return Set.of();

        Method getEntries = pokedexDef.getClass().getMethod("getEntries");
        Object entriesObj = getEntries.invoke(pokedexDef);
        if (!(entriesObj instanceof List<?> entries)) return Set.of();

        Set<String> names = new HashSet<>();
        for (Object entry : entries) {
            Method getSpeciesId = entry.getClass().getMethod("getSpeciesId");
            Object speciesId = getSpeciesId.invoke(entry);
            if (speciesId instanceof ResourceLocation rl) names.add(rl.getPath().toLowerCase());
        }
        return names;
    }

    private static int[] countFromRecords(Object recordsMap) throws Exception {
        if (!(recordsMap instanceof Map<?, ?> map)) return new int[]{0, 0};

        int newSeen = 0, newCaught = 0;
        Class<?> progressClass = Class.forName(
            "com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress");
        Object caughtEnum = null;
        Object encounteredEnum = null;
        for (Object e : progressClass.getEnumConstants()) {
            if (e.toString().equals("CAUGHT")) caughtEnum = e;
            if (e.toString().equals("ENCOUNTERED")) encounteredEnum = e;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object record = entry.getValue();
            Method getKnowledge = record.getClass().getMethod("getKnowledge");
            Object knowledge = getKnowledge.invoke(record);

            if (knowledge == caughtEnum) {
                newCaught++;
                newSeen++;
            } else if (knowledge == encounteredEnum) {
                newSeen++;
            }
        }
        return new int[]{newSeen, newCaught};
    }

    /**
     * Prüft ob eine Species im Living Dex ist (physisch im PC/Party).
     * Wird vom Server via Netzwerkpaket befüllt (später).
     * Vorerst: lokal über Minecraft-Client prüfen.
     */
    public static boolean isInLivingDex(String speciesName) {
        return livingDexSpecies.contains(speciesName.toLowerCase());
    }

    public static void setLivingDexSpecies(Set<String> species) {
        livingDexSpecies = new HashSet<>();
        for (String s : species) livingDexSpecies.add(s.toLowerCase());
        // livingDexCount wird bei jedem loadData()-Aufruf regionsbewusst neu berechnet.
    }

    /**
     * Momentaufnahme der aktuellen Spezies-Menge - für den Professor-Tab, der diese Menge
     * temporär auf die des inspizierten Spielers umschaltet und danach wiederherstellen muss
     * (siehe CompanionScreen.buildProfessorLivingDexScreen()/restoreLocalPokedexIfNeeded()).
     */
    public static Set<String> getLivingDexSpeciesSnapshot() {
        return new HashSet<>(livingDexSpecies);
    }

    public static int getLivingDexCount() { return livingDexCount; }
    public static int getCaughtCount() { return caughtCount; }
    public static int getSeenCount() { return seenCount; }
    public static boolean isDataLoaded() { return dataLoaded; }

    public static void reset() {
        dataLoaded = false;
        livingDexSpecies.clear();
        livingDexCount = 0;
        caughtCount = 0;
        seenCount = 0;
    }
}