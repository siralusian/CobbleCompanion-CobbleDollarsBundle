package com.cobblecompanion.client.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Hält die zuletzt vom Server empfangene Dex-Vervollständigungsliste (ToDo-Tab, rechte Hälfte). */
public class ClientDexCompletionHelper {

    public enum Category { LEVEL, STONE, FRIENDSHIP, TRADE, OTHER }

    public static final class CatchEntry {
        public final ResourceLocation speciesId;
        public final boolean selfNeeded;
        /** &gt;0 NUR Living-Dex: es fehlen zusätzlich extraCopies Folgestufen gleichzeitig im Bestand. */
        public final int extraCopies;
        public final List<ResourceLocation> coveredSpeciesIds;
        /** Living-Dex+-Variante: Formname (leer = Standardform) + Shiny-Status. */
        public final String formName;
        public final boolean shiny;

        public CatchEntry(ResourceLocation speciesId, boolean selfNeeded, int extraCopies,
                           List<ResourceLocation> coveredSpeciesIds, String formName, boolean shiny) {
            this.speciesId = speciesId;
            this.selfNeeded = selfNeeded;
            this.extraCopies = extraCopies;
            this.coveredSpeciesIds = coveredSpeciesIds;
            this.formName = formName;
            this.shiny = shiny;
        }
    }

    public static final class EvolveEntry {
        public final Category category;
        public final ResourceLocation fromSpeciesId;
        public final ResourceLocation toSpeciesId;
        public final int requiredLevel;
        public final int ownedLevel;
        public final String itemPath;
        /** Aspekte des Entwicklungsergebnisses (Hokumil/Milcery & Co), leer wenn keine Mehrformen-Art. */
        public final String resultAspects;

        public EvolveEntry(Category category, ResourceLocation fromSpeciesId, ResourceLocation toSpeciesId,
                            int requiredLevel, int ownedLevel, String itemPath, String resultAspects) {
            this.category = category;
            this.fromSpeciesId = fromSpeciesId;
            this.toSpeciesId = toSpeciesId;
            this.requiredLevel = requiredLevel;
            this.ownedLevel = ownedLevel;
            this.itemPath = itemPath;
            this.resultAspects = resultAspects;
        }
    }

    private static List<CatchEntry> catchEntries = new ArrayList<>();
    private static List<EvolveEntry> evolveEntries = new ArrayList<>();
    private static int dataVersion = 0;

    /** Parst eine "cat|fromId|toId|level|ownedLevel|itemPath|resultAspects"-Zeile, oder null bei ungültigem Format. */
    private static EvolveEntry parseEvolveEntry(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 7) return null;
        try {
            Category category = switch (parts[0]) {
                case "L" -> Category.LEVEL;
                case "S" -> Category.STONE;
                case "F" -> Category.FRIENDSHIP;
                case "T" -> Category.TRADE;
                default -> Category.OTHER;
            };
            return new EvolveEntry(category,
                ResourceLocation.parse(parts[1]), ResourceLocation.parse(parts[2]),
                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5], parts[6]);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void setEntries(List<String> catchLines, List<String> evolveLines) {
        List<CatchEntry> newCatch = new ArrayList<>();
        for (String line : catchLines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 6) continue;
            try {
                List<ResourceLocation> covered = new ArrayList<>();
                if (!parts[3].isBlank()) {
                    for (String coveredId : parts[3].split(",")) {
                        if (!coveredId.isBlank()) covered.add(ResourceLocation.parse(coveredId));
                    }
                }
                newCatch.add(new CatchEntry(ResourceLocation.parse(parts[0]), Boolean.parseBoolean(parts[1]),
                    Integer.parseInt(parts[2]), covered, parts[4], Boolean.parseBoolean(parts[5])));
            } catch (Exception ignored) {}
        }

        List<EvolveEntry> newEvolve = new ArrayList<>();
        for (String line : evolveLines) {
            EvolveEntry e = parseEvolveEntry(line);
            if (e != null) newEvolve.add(e);
        }

        catchEntries = newCatch;
        evolveEntries = newEvolve;
        dataVersion++;
    }

    public static List<CatchEntry> getCatchEntries() { return catchEntries; }
    public static List<EvolveEntry> getEvolveEntries() { return evolveEntries; }
    public static int getDataVersion() { return dataVersion; }

    // ===== Suchergebnis (Vorentwicklung + direkte Entwicklungsziele einer gesuchten Spezies) =====
    private static String searchSpeciesName = "";
    private static ResourceLocation searchPreEvolutionId = null;
    /** Die Entwicklung VON der Vorentwicklung ZU der gesuchten Art (samt Bedingung) - null, falls keine Vorentwicklung. */
    private static EvolveEntry searchPreEvolutionEdge = null;
    private static List<EvolveEntry> searchEvolutions = new ArrayList<>();
    private static boolean searchHasCaughtPokedex = false;
    private static boolean searchOwnsLivingDex = false;
    private static ResourceLocation searchSpeciesId = null;
    private static int searchVersion = 0;

    /** selfStatus = "hasCaughtPokedex,ownsLivingDex,speciesId" - ein Feld statt 3 eigene, da StreamCodec.composite auf 5 Feld-Paare gedeckelt ist. */
    public static void setSearchResult(String speciesName, String preEvolutionId, String preEvolutionEdgeLine,
                                        List<String> evolveLines, String selfStatus) {
        searchSpeciesName = speciesName;
        searchPreEvolutionId = null;
        if (preEvolutionId != null && !preEvolutionId.isBlank()) {
            try { searchPreEvolutionId = ResourceLocation.parse(preEvolutionId); } catch (Exception ignored) {}
        }
        searchPreEvolutionEdge = (preEvolutionEdgeLine != null && !preEvolutionEdgeLine.isBlank())
            ? parseEvolveEntry(preEvolutionEdgeLine) : null;

        List<EvolveEntry> parsed = new ArrayList<>();
        for (String line : evolveLines) {
            EvolveEntry e = parseEvolveEntry(line);
            if (e != null) parsed.add(e);
        }
        searchEvolutions = parsed;

        searchHasCaughtPokedex = false;
        searchOwnsLivingDex = false;
        searchSpeciesId = null;
        if (selfStatus != null) {
            String[] parts = selfStatus.split(",", -1);
            if (parts.length == 3) {
                searchHasCaughtPokedex = Boolean.parseBoolean(parts[0]);
                searchOwnsLivingDex = Boolean.parseBoolean(parts[1]);
                if (!parts[2].isBlank()) {
                    try { searchSpeciesId = ResourceLocation.parse(parts[2]); } catch (Exception ignored) {}
                }
            }
        }
        searchVersion++;
    }

    public static String getSearchSpeciesName() { return searchSpeciesName; }
    public static ResourceLocation getSearchPreEvolutionId() { return searchPreEvolutionId; }
    public static EvolveEntry getSearchPreEvolutionEdge() { return searchPreEvolutionEdge; }
    public static List<EvolveEntry> getSearchEvolutions() { return searchEvolutions; }
    public static boolean isSearchHasCaughtPokedex() { return searchHasCaughtPokedex; }
    public static boolean isSearchOwnsLivingDex() { return searchOwnsLivingDex; }
    public static ResourceLocation getSearchSpeciesId() { return searchSpeciesId; }
    public static int getSearchVersion() { return searchVersion; }
}
