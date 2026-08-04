package com.cobblecompanion.data;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serverseitiges Gegenstück zu PCSortHelper.ldpFlatList()/ldpIndexForPokemon() (Client) - baut
 * dieselbe flache, kategorie-geordnete Liste und ordnet ihr Pokemon zu, aber OHNE Client-
 * Roundtrip (der Server hat Species/Evolution-Daten immer direkt verfügbar). Wird von
 * PCSlotCheckHelper (Home-Tab-Zähler) und AutoNameBoxesPacket (Box-Beschriftung) geteilt, damit
 * es EINE Quelle der Wahrheit für "welcher Index gehört zu diesem Pokemon" gibt.
 */
public class LivingDexPlusLayoutHelper {

    private static final int BOX_SIZE = 30;

    /**
     * Nutzer-Vorgabe: 2 verschiedene Kategorien ("Dex") teilen sich nie eine Box, auch wenn das
     * Slots verschwendet - jede Kategorie wird deshalb mit null-Platzhaltern auf ein Vielfaches
     * von BOX_SIZE aufgefüllt, bevor die nächste Kategorie beginnt. Box/Zeile/Slot-Arithmetik
     * (Index/30 usw.) bleibt dadurch unverändert; null-Einträge matchen einfach nie ein Pokemon
     * (siehe indexForPokemon()) und bleiben als leere Slots sichtbar.
     *
     * "order" enthält NEBEN den echten Kategorie-IDs (0-7) auch synthetische Pro-Region-IDs (siehe
     * LivingDexPlusRegistry.regionSyntheticId(), Umbau "Regionalformen-Unterkategorien von den
     * Oberkategorien trennen") - jede Region ist ein vollwertiges, frei einordenbares Listenglied,
     * kein Gruppen-Marker-Sonderfall mehr. Eine synthetische ID wird direkt zu (echte Kategorie
     * 3/4, Region-Filter) aufgelöst und wie jede andere Kategorie separat auf BOX_SIZE aufgefüllt.
     */
    public static List<LivingDexPlusRegistry.Entry> buildFlatList(List<Integer> order) {
        Map<Integer, List<LivingDexPlusRegistry.Entry>> byCategory = new HashMap<>();
        for (LivingDexPlusRegistry.Entry e : LivingDexPlusRegistry.getAll()) {
            byCategory.computeIfAbsent(e.categoryId(), k -> new ArrayList<>()).add(e);
        }
        for (List<LivingDexPlusRegistry.Entry> list : byCategory.values()) {
            list.sort(Comparator.comparingInt(LivingDexPlusRegistry.Entry::dexNumber)
                .thenComparing(LivingDexPlusRegistry.Entry::formName));
        }

        List<LivingDexPlusRegistry.Entry> flat = new ArrayList<>();
        java.util.Set<Integer> consumedCosmeticIds = new java.util.HashSet<>();
        for (int id : order) {
            if (LivingDexPlusRegistry.isAnyRegionSyntheticId(id)) {
                int realCatId = LivingDexPlusRegistry.realCategoryIdFromSyntheticId(id);
                String region = LivingDexPlusRegistry.regionNameFromSyntheticId(id);
                List<LivingDexPlusRegistry.Entry> catAll = byCategory.getOrDefault(realCatId, List.of());
                List<LivingDexPlusRegistry.Entry> regionEntries = new ArrayList<>();
                for (LivingDexPlusRegistry.Entry e : catAll) {
                    if (region.equalsIgnoreCase(e.formName())) regionEntries.add(e);
                }
                appendPadded(flat, regionEntries);
                continue;
            }
            // Umbau "Kosmetische Formen: Unterkategorien" (analog zu Regionen, Artenliste
            // dynamisch aus dem Katalog - siehe LivingDexPlusRegistry.cosmeticSpeciesNames()).
            // Nutzer-Vorgabe: Karpador+Garados teilen sich - falls beide aktiv - eine gemeinsame
            // Box-Sequenz (siehe LivingDexPlusRegistry.resolveCosmeticBoxGroup()).
            if (LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id)) {
                if (consumedCosmeticIds.contains(id)) continue;
                int realCatId = LivingDexPlusRegistry.realCategoryIdFromCosmeticSyntheticId(id);
                List<String> cosmeticSpecies = LivingDexPlusRegistry.cosmeticSpeciesNames();
                var group = LivingDexPlusRegistry.resolveCosmeticBoxGroup(id, order, cosmeticSpecies);
                consumedCosmeticIds.addAll(group.consumedSyntheticIds());
                if (group.speciesNames().isEmpty()) continue;
                List<LivingDexPlusRegistry.Entry> catAll = byCategory.getOrDefault(realCatId, List.of());
                List<LivingDexPlusRegistry.Entry> speciesEntries = new ArrayList<>();
                for (LivingDexPlusRegistry.Entry e : catAll) {
                    for (String species : group.speciesNames()) {
                        if (species.equalsIgnoreCase(e.speciesName())) { speciesEntries.add(e); break; }
                    }
                }
                appendPadded(flat, speciesEntries);
                continue;
            }
            if (id == 5 || id == 6) {
                // Basis-Kategorie "Kosmetische Formen" deckt bewusst NICHT die Arten aus der
                // Unterkategorien-Whitelist ab (siehe LivingDexPlusRegistry.
                // COSMETIC_SUBCATEGORY_WHITELIST) - die haben ihre eigene Box/Unterkategorie und
                // würden sonst doppelt gezählt.
                List<LivingDexPlusRegistry.Entry> filtered = new ArrayList<>();
                for (LivingDexPlusRegistry.Entry e : byCategory.getOrDefault(id, List.of())) {
                    if (!LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase())) filtered.add(e);
                }
                appendPadded(flat, filtered);
                continue;
            }
            appendPadded(flat, byCategory.getOrDefault(id, List.of()));
        }
        return flat;
    }

    private static void appendPadded(List<LivingDexPlusRegistry.Entry> flat, List<LivingDexPlusRegistry.Entry> entries) {
        if (entries.isEmpty()) return;
        flat.addAll(entries);
        int remainder = flat.size() % BOX_SIZE;
        if (remainder != 0) {
            for (int i = 0; i < BOX_SIZE - remainder; i++) flat.add(null);
        }
    }

    /** -1, falls das Pokemon zu keiner aktivierten Kategorie gehört. */
    public static int indexForPokemon(Pokemon pokemon, List<LivingDexPlusRegistry.Entry> flat, List<Integer> categoryOrder) {
        Species species = pokemon.getSpecies();
        String speciesName = species.getName();
        // Berücksichtigt sowohl klassische Formen (Pokemon.getForm()) als auch das Species-
        // Feature-System (Pokemon.getAspects(), z.B. Karpador-Jump-Farben) - siehe
        // LivingDexPlusRegistry.effectiveFormChoice()/FEATURE_SPECIES.
        String formChoice = LivingDexPlusRegistry.effectiveFormChoice(pokemon);
        boolean standardForm = formChoice == null;
        String formName = standardForm ? speciesName : formChoice;
        boolean shiny = pokemon.getShiny();

        Integer familyAnchor = (categoryOrder.contains(0) || categoryOrder.contains(7))
            ? EvolutionFamilyHelper.effectiveAnchorDexNumber(species) : null;

        for (int i = 0; i < flat.size(); i++) {
            LivingDexPlusRegistry.Entry e = flat.get(i);
            if (e == null) continue; // Box-Trennungs-Padding zwischen Kategorien
            boolean match = switch (e.categoryId()) {
                case 0 -> !shiny && familyAnchor != null && e.dexNumber() == familyAnchor;
                case 1 -> standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName);
                case 2 -> standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName);
                case 3 -> !standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName);
                case 4 -> !standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName);
                case 5 -> !standardForm && !shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName);
                case 6 -> !standardForm && shiny && e.speciesName().equalsIgnoreCase(speciesName) && e.formName().equalsIgnoreCase(formName);
                case 7 -> shiny && familyAnchor != null && e.dexNumber() == familyAnchor;
                default -> false;
            };
            if (match) return i;
        }
        return -1;
    }

    /** Kurzes Kürzel je Kategorie für Box-Namen (siehe AutoNameBoxesPacket). */
    public static String categoryAbbreviation(int categoryId) {
        return switch (categoryId) {
            case 0 -> "PD";
            case 1 -> "LD";
            case 2 -> "SH-LD";
            case 3 -> "REG";
            case 4 -> "REGSH";
            case 5 -> "COS";
            case 6 -> "COSSH";
            case 7 -> "SH-PD";
            default -> "?";
        };
    }
}
