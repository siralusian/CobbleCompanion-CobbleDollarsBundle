package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Hält den zuletzt vom Server empfangenen Type-Report (Titel + Typ-Icons + Slot-Ergebnisse) für den Type-Tab. */
public class ClientTypeHelper {

    public static class TypeResultEntry {
        public final boolean party; // true = eigenes Team, false = PC
        public final ResourceLocation speciesId;
        public final int level;
        public final Set<String> aspects;
        public final String colorCode; // Minecraft-Formatierungscode, z.B. "§a"
        public final List<String> keywords; // z.B. "Attacke:Feuerodem", "Typ 2x", "Top Lvl"

        public TypeResultEntry(boolean party, ResourceLocation speciesId, int level,
                                Set<String> aspects, String colorCode, List<String> keywords) {
            this.party = party;
            this.speciesId = speciesId;
            this.level = level;
            this.aspects = aspects;
            this.colorCode = colorCode;
            this.keywords = keywords;
        }
    }

    /** Ein effektiver Angriffstyp gegen die Suchanfrage, mit Multiplikator (2 oder 4) fürs "Nx"-Label. */
    public static class StrongType {
        public final String typeName;
        public final int multiplier;

        public StrongType(String typeName, int multiplier) {
            this.typeName = typeName;
            this.multiplier = multiplier;
        }
    }

    private static List<String> defenderTypes = List.of();
    private static ResourceLocation querySpeciesId = null;
    private static List<StrongType> strongTypes = new ArrayList<>();
    private static List<TypeResultEntry> results = new ArrayList<>();
    private static String lastQuery = "";
    /** Ungültige Suchanfrage (Query, die weder Typ noch Pokemon war), oder null wenn kein Fehler. */
    private static String errorQuery = null;

    public static void setEntries(List<String> lines) {
        List<String> defTypes = List.of();
        ResourceLocation qSpecies = null;
        List<StrongType> strong = new ArrayList<>();
        List<TypeResultEntry> parsed = new ArrayList<>();
        String error = null;

        for (String line : lines) {
            if (line.startsWith("H|")) {
                error = line.substring(2);
            } else if (line.startsWith("D|")) {
                String[] parts = line.substring(2).split(",", -1);
                List<String> types = new ArrayList<>();
                if (parts.length > 0 && !parts[0].isEmpty()) types.add(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) types.add(parts[1]);
                defTypes = types;
            } else if (line.startsWith("Q|")) {
                try {
                    qSpecies = ResourceLocation.parse(line.substring(2));
                } catch (Exception ignored) {
                    // ungültige ResourceLocation -> kein Slot anzeigen
                }
            } else if (line.startsWith("S|")) {
                String[] parts = line.substring(2).split("\\|", -1);
                if (parts.length == 2) {
                    try {
                        strong.add(new StrongType(parts[0], Integer.parseInt(parts[1])));
                    } catch (Exception ignored) {
                        // Eintrag defekt -> überspringen
                    }
                }
            } else if (line.startsWith("E|")) {
                String[] parts = line.substring(2).split("\\|", -1);
                if (parts.length != 6) continue;
                try {
                    boolean isParty = parts[0].equals("party");
                    ResourceLocation speciesId = ResourceLocation.parse(parts[1]);
                    int level = Integer.parseInt(parts[2]);
                    Set<String> aspects = parts[3].isEmpty() ? Collections.emptySet() : Set.copyOf(Arrays.asList(parts[3].split(",")));
                    String color = parts[4];
                    List<String> keywords = parts[5].isEmpty() ? List.of() : Arrays.asList(parts[5].split(";"));
                    parsed.add(new TypeResultEntry(isParty, speciesId, level, aspects, color, keywords));
                } catch (Exception ignored) {
                    // Eintrag defekt -> überspringen
                }
            }
        }

        defenderTypes = defTypes;
        querySpeciesId = qSpecies;
        strongTypes = strong;
        results = parsed;
        errorQuery = error;
    }

    /** Ungültige Suchanfrage (weder Typ noch Pokemon), oder null wenn die letzte Antwort gültig war. */
    public static String getErrorQuery() {
        return errorQuery;
    }

    public static List<String> getDefenderTypes() {
        return defenderTypes;
    }

    /** Nur gesetzt, wenn die Suche ein Pokemon (statt eines reinen Typs) war. */
    public static ResourceLocation getQuerySpeciesId() {
        return querySpeciesId;
    }

    public static List<StrongType> getStrongTypes() {
        return strongTypes;
    }

    public static List<TypeResultEntry> getResults() {
        return results;
    }

    public static void setLastQuery(String query) {
        lastQuery = query != null ? query : "";
    }

    public static String getLastQuery() {
        return lastQuery;
    }
}
