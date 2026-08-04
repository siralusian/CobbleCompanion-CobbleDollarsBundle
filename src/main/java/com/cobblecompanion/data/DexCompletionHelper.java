package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Berechnet für den ToDo-Tab's rechte Hälfte ("Dex-Vervollständigungshilfe"), was einem Spieler
 * noch fehlt, um den Pokédex bzw. Living Dex zu vervollständigen - getrennt in "muss noch gefangen
 * werden" (keine besessene Vorstufe in der Entwicklungslinie) und "kann durch Entwickeln erreicht
 * werden" (Entwicklungslinie ausgehend von tatsächlich besessenen Spezies, rekursiv/mehrstufig).
 *
 * Läuft bewusst über Species.getEvolutions() (statische Registry-Daten), NICHT über
 * Pokemon.getEvolutions() (Instanz-Methode) - siehe die ausführliche Begründung in
 * EvolutionChainRequestPacket: Instanz-Evolutionsdaten sind auf einem echten Dedicated Server für
 * NBT-rekonstruierte bzw. sogar echte netzwerksynchronisierte Client-Pokemon unzuverlässig; hier
 * läuft alles ohnehin serverseitig, aber Species-Level-Daten sind auch hier einfach robuster/
 * konsistenter mit dem Rest der Entwicklungsketten-Logik in dieser Codebase.
 */
public class DexCompletionHelper {

    /**
     * Ein Eintrag der "muss noch gefangen werden"-Liste. Erscheint NUR für "Wurzel"-Arten (keine
     * eigene Vorentwicklung, species.getPreEvolution()==null) - alle weiterentwickelten Stufen
     * werden NIE als eigener Fangen-Eintrag gelistet, sondern über ihre Wurzel abgedeckt (Nutzer-
     * Beispiel: eine komplett unbesessene Kette Raupy->Safcon->Smettbo darf NUR Raupy als Eintrag
     * zeigen, mit extraCopies für Safcon+Smettbo statt 3 getrennten Einträgen).
     * selfNeeded = die Wurzel-Art selbst wurde noch nicht gefangen/registriert.
     * extraCopies&gt;0 NUR im Living-Dex-Modus: es fehlen zusätzlich extraCopies Stufen der eigenen
     * Entwicklungslinie gleichzeitig im Bestand (Schiggy/Schillok/Turtok-Beispiel, aber jetzt auch
     * für den Fall gültig, dass die Wurzel selbst noch gar nicht gefangen wurde).
     * coveredSpeciesIds listet die konkreten noch fehlenden Folgestufen, für die extraCopies steht.
     */
    public static final class CatchEntry {
        public final ResourceLocation speciesId;
        public final boolean selfNeeded;
        public final int extraCopies;
        public final List<ResourceLocation> coveredSpeciesIds;
        /** Living-Dex+-Variante dieses Eintrags: Formname (leer = Standardform) + Shiny-Status. */
        public final String formName;
        public final boolean shiny;

        public CatchEntry(ResourceLocation speciesId, boolean selfNeeded, int extraCopies, List<ResourceLocation> coveredSpeciesIds) {
            this(speciesId, selfNeeded, extraCopies, coveredSpeciesIds, "", false);
        }

        public CatchEntry(ResourceLocation speciesId, boolean selfNeeded, int extraCopies,
                           List<ResourceLocation> coveredSpeciesIds, String formName, boolean shiny) {
            this.speciesId = speciesId;
            this.selfNeeded = selfNeeded;
            this.extraCopies = extraCopies;
            this.coveredSpeciesIds = coveredSpeciesIds;
            this.formName = formName != null ? formName : "";
            this.shiny = shiny;
        }
    }

    /** Unterkategorie einer Entwicklung, wie vom Nutzer vorgegeben. */
    public enum EvolveCategory { LEVEL, STONE, FRIENDSHIP, TRADE, OTHER }

    /** Ein Eintrag der "kann durch Entwickeln erreicht werden"-Liste. */
    public static final class EvolveEntry {
        public final EvolveCategory category;
        public final ResourceLocation fromSpeciesId;
        public final ResourceLocation toSpeciesId;
        /** Level-Anforderung (nur LEVEL), sonst -1. */
        public final int requiredLevel;
        /** Aktuelles Level des am nächsten dran liegenden besessenen fromSpeciesId-Exemplars, oder -1 wenn fromSpeciesId nicht besessen wird (Zwischenstufe). */
        public final int ownedLevel;
        /** Item-Pfad (Stein bzw. bei TRADE optional das zusätzlich benötigte getragene Item), leer wenn keins. */
        public final String itemPath;
        /** Aspekte des Entwicklungsergebnisses (z.B. Hokumil/Milcery -> Deko/Creme-Kombination je Item), leer wenn keine Mehrformen-Art. */
        public final String resultAspects;

        public EvolveEntry(EvolveCategory category, ResourceLocation fromSpeciesId, ResourceLocation toSpeciesId,
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

    public static final class Result {
        public final List<CatchEntry> catchEntries = new ArrayList<>();
        public final List<EvolveEntry> evolveEntries = new ArrayList<>();
    }

    /**
     * Gesamt-Stückzahl "noch zu fangen" über alle CatchEntry-Einträge (BUGFIX, Nutzer-Report:
     * Fangen-Gesamtzahl zeigte konstant catchEntries.size() an, unabhängig vom Living-Dex-
     * Mengen-Konzept - 2x benötigte Bisasam zählten dadurch nur als 1). Summiert je Eintrag
     * (selfNeeded?1:0) + extraCopies - im Pokédex-Modus ist extraCopies immer 0 (kein Mengen-
     * Konzept dort, siehe compute()), die Summe entspricht dort also automatisch weiterhin
     * entries.size(), keine Modus-Fallunterscheidung nötig.
     */
    public static int totalCatchQuantity(List<CatchEntry> entries) {
        int sum = 0;
        for (CatchEntry e : entries) sum += (e.selfNeeded ? 1 : 0) + e.extraCopies;
        return sum;
    }

    /** Ergebnis von describeSpecies() - für die Suchfeld-Ansicht (Woher entwickelt, wohin kann es sich entwickeln). */
    public static final class SpeciesInfo {
        public ResourceLocation preEvolutionId;
        /** Die Entwicklung VON der Vorentwicklung ZU der gesuchten Art (samt Bedingung) - null, falls keine Vorentwicklung. */
        public EvolveEntry preEvolutionEdge;
        public final List<EvolveEntry> evolutions = new ArrayList<>();
        /** Status der GESUCHTEN Art selbst beim lokal anfragenden Spieler. */
        public boolean hasCaughtPokedex;
        public boolean ownsLivingDex;
        /** Aufgelöste ResourceLocation der gesuchten Art selbst (für Icon/Namen im Suchmodus-Statusblock). */
        public ResourceLocation speciesId;
    }

    /**
     * Wie compute(), aber ohne Dex-Bezug: liefert für eine EINZELNE gesuchte Spezies ihre direkte
     * Vorentwicklung (species.getPreEvolution(), existiert direkt auf Species) - inkl. der
     * Bedingung dieser Entwicklung (preEvolutionEdge, Live-Test-Nachtrag: die Vorentwicklung
     * allein ohne Bedingung war nicht hilfreich genug) - und alle direkten Entwicklungsziele mit
     * Bedingung, für die Suchfeld-Ansicht des ToDo-Tabs.
     */
    public static SpeciesInfo describeSpecies(ServerPlayer player, String speciesName) {
        SpeciesInfo info = new SpeciesInfo();
        Species species = ServerDataHelper.findSpeciesByName(speciesName);
        if (species == null) return info;

        Set<String> ownedSpeciesLower = new HashSet<>();
        Map<String, Integer> bestLevelPerSpecies = new HashMap<>();
        for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
            String sp = p.getSpecies().getName().toLowerCase();
            ownedSpeciesLower.add(sp);
            bestLevelPerSpecies.merge(sp, p.getLevel(), Math::max);
        }

        info.speciesId = species.getResourceIdentifier();
        info.ownsLivingDex = ownedSpeciesLower.contains(species.getName().toLowerCase());
        try {
            PokedexManager pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player.getUUID());
            info.hasCaughtPokedex = pokedex != null && ServerDataHelper.hasCaughtSpecies(pokedex, species.getResourceIdentifier());
        } catch (Exception ignored) {}

        try {
            var pre = species.getPreEvolution();
            if (pre != null && pre.getSpecies() != null) {
                Species preSpecies = pre.getSpecies();
                info.preEvolutionId = preSpecies.getResourceIdentifier();
                for (Evolution evo : preSpecies.getEvolutions()) {
                    ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
                    if (toId == null) continue;
                    Species toSpecies = resolveSpeciesRobust(toId);
                    if (toSpecies != null && toSpecies.getName().equalsIgnoreCase(species.getName())) {
                        info.preEvolutionEdge = buildEntry(evo, preSpecies, species, ownedSpeciesLower, bestLevelPerSpecies);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        for (Evolution evo : species.getEvolutions()) {
            String resultName = evo.getResult().getSpecies();
            ResourceLocation toId = TodoHelper.resolveSpeciesId(resultName);
            if (toId == null) continue;
            Species toSpecies = resolveSpeciesRobust(toId);
            if (toSpecies == null) continue;
            info.evolutions.add(buildEntry(evo, species, toSpecies, ownedSpeciesLower, bestLevelPerSpecies));
        }
        return info;
    }

    public static Result compute(ServerPlayer player, boolean pokedexMode) {
        return compute(player, pokedexMode, List.of());
    }

    /**
     * ldpCategories: NUR im Living-Dex+-Modus nicht-leer (siehe ClientSettingsHelper.
     * getLivingDexPlusCategoryOrder()) - ergänzt die Fangen-Liste um Kategorien 2/3/4/5/6 (Shiny
     * Living Dex, Regionalformen, Regionalformen Shiny, Kosmetische Formen, Kosmetische Formen
     * Shiny), die die normale pokedexMode-Logik oben gar nicht kennt (BUGFIX, Nutzer-Report:
     * "Living Dex+ mit Regionalformen ausgewählt zeigt trotzdem nur den reinen Living-Dex-Wert").
     * Kategorie 7 (Shiny Pokédex, familien-anker-basiert) bewusst NICHT abgedeckt - eigene
     * Baustelle, da sie dieselbe Familienketten-Komplexität wie Kategorie 0 bräuchte.
     */
    public static Result compute(ServerPlayer player, boolean pokedexMode, List<Integer> ldpCategories) {
        Result result = new Result();

        PokedexManager pokedex = null;
        try {
            pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player.getUUID());
        } catch (Exception ignored) {}

        Set<String> ownedSpeciesLower = new HashSet<>();
        Map<String, Integer> bestLevelPerSpecies = new HashMap<>();
        // Echte STÜCKZAHLEN je Art (nicht nur "besessen ja/nein") - Grundlage für den
        // Familien-weiten Fangen-Bedarf unten (BUGFIX, siehe dort).
        Map<String, Integer> ownedCountPerSpecies = new HashMap<>();
        // Living-Dex+-Varianten-Schlüssel ("art|form|shiny", form leer bei Standardform) - Grundlage
        // für die Kategorie-2/3/4/5/6-Ergänzung unten (addLdpVariantCatchEntries()).
        Set<String> ownedVariantKeys = new HashSet<>();
        for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
            String sp = p.getSpecies().getName().toLowerCase();
            ownedSpeciesLower.add(sp);
            bestLevelPerSpecies.merge(sp, p.getLevel(), Math::max);
            ownedCountPerSpecies.merge(sp, 1, Integer::sum);

            // Berücksichtigt sowohl klassische Formen als auch das Species-Feature-System (z.B.
            // Karpador-Jump-Farben) - siehe LivingDexPlusRegistry.effectiveFormChoice().
            String formChoice = LivingDexPlusRegistry.effectiveFormChoice(p);
            boolean standardForm = formChoice == null;
            String formKey = standardForm ? "" : formChoice.toLowerCase();
            ownedVariantKeys.add(sp + "|" + formKey + "|" + p.getShiny());
        }

        Set<String> reachableViaEvolve = new HashSet<>();
        Set<String> visitedEdges = new HashSet<>();
        Set<String> visitedNodes = new HashSet<>();
        final PokedexManager finalPokedex = pokedex;
        for (String startSpecies : new ArrayList<>(ownedSpeciesLower)) {
            walk(startSpecies, pokedexMode, finalPokedex, ownedSpeciesLower, bestLevelPerSpecies, ownedCountPerSpecies,
                visitedEdges, visitedNodes, reachableViaEvolve, result.evolveEntries);
        }

        // Fangen-Liste: NUR "Wurzel"-Arten (keine eigene Vorentwicklung) werden je zum Fang-Ziel -
        // alles Weiterentwickelte wird über die Wurzel abgedeckt (siehe Nutzer-Beispiel
        // Raupy/Safcon/Smettbo: nur Raupy soll als eigener Fangen-Eintrag auftauchen, NICHT jede
        // Stufe einzeln - der alte "reachableViaEvolve"-Ansatz lief nur ab BESESSENEN Arten los und
        // ließ komplett unbesessene Ketten dadurch fälschlich als mehrere Einzeleinträge durchfallen).
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (!isRootSpecies(s)) continue;
            String rootLower = s.getName().toLowerCase();
            ResourceLocation rootId = s.getResourceIdentifier();

            boolean selfSatisfied = pokedexMode
                ? (finalPokedex != null && ServerDataHelper.hasCaughtSpecies(finalPokedex, rootId))
                : ownedSpeciesLower.contains(rootLower);

            List<ResourceLocation> missingDownstream = new ArrayList<>();
            collectMissingDownstream(rootLower, pokedexMode, finalPokedex, ownedSpeciesLower, missingDownstream, new HashSet<>());

            if (selfSatisfied && missingDownstream.isEmpty()) continue;

            if (!pokedexMode) {
                // Living Dex: Mengen-Konzept - jede Stufe der Familie braucht ein EIGENES Exemplar.
                // BUGFIX (Nutzer-Report: "1x Schiggy fangen für Turtok" trotz 2x Schiggy + 1x
                // Schillok im Bestand): missingDownstream.size() zählte bisher nur, wie viele
                // Folgestufen NICHT besessen sind (hier: Turtok) - unabhängig davon, ob die Familie
                // als GANZES schon genug Einzeltiere hat, um durch Umverteilen per Entwickeln alle
                // Stufen gleichzeitig abzudecken (2 Schiggy + 1 Schillok = 3 Tiere für 3 benötigte
                // Stufen Schiggy/Schillok/Turtok, macht 0 Fangen-Bedarf). Jetzt: echte Stückzahl
                // über die GESAMTE Familie (alle Stufen, nicht nur die fehlenden) gegen die Anzahl
                // benötigter Stufen aufrechnen - nur ein tatsächliches Stückzahl-Defizit ist noch
                // Fangen-Bedarf, alles andere ist reine Entwicklungs-Umverteilung (siehe ToDo-
                // Entwickeln-Liste).
                List<String> allStagesLower = new ArrayList<>();
                allStagesLower.add(rootLower);
                collectAllDownstream(rootLower, allStagesLower, new HashSet<>());
                int totalStagesNeeded = allStagesLower.size();
                int totalOwned = 0;
                for (String stage : allStagesLower) totalOwned += ownedCountPerSpecies.getOrDefault(stage, 0);
                int deficit = Math.max(0, totalStagesNeeded - totalOwned);
                if (deficit == 0) continue; // Familie hat schon genug Einzeltiere, reine Entwicklungssache

                boolean selfNeeded = !selfSatisfied;
                int extraCopies = selfNeeded ? deficit - 1 : deficit;
                result.catchEntries.add(new CatchEntry(rootId, selfNeeded, extraCopies, missingDownstream));
            } else if (!selfSatisfied) {
                // Pokédex: kein Mengen-Konzept (Fortschritt bleibt beim Entwickeln erhalten) - ein
                // einziger Fang der Wurzel reicht, um die ganze Kette per Weiterentwickeln
                // abzudecken; fehlende Downstream-Stufen sind daher hier nie ein eigener Bedarf.
                result.catchEntries.add(new CatchEntry(rootId, true, 0, List.of()));
            }
        }

        addLdpVariantCatchEntries(ldpCategories, ownedVariantKeys, result.catchEntries);

        return result;
    }

    /**
     * Ergänzt die Fangen-Liste um Living-Dex+-Varianten-Kategorien (Shiny Living Dex/Regionalformen/
     * Kosmetische Formen, je normal+shiny) - jede fehlende Katalog-Variante wird als eigener,
     * unabhängiger CatchEntry hinzugefügt (kein Familienketten-Mengen-Konzept nötig, jede Variante
     * ist für sich genommen ein einzelnes Fang-/Erhalts-Ziel). Regionalformen (Kategorie 3/4) UND
     * kosmetische Formen (Kategorie 5/6) sind seit dem jeweiligen Umbau "...-Unterkategorien von den
     * Oberkategorien trennen" NIE mehr direkt in ldpCategories enthalten - stattdessen je eine
     * synthetische Pro-Region- bzw. Pro-Art-ID, aus der hier die aktivierten Regionen/Arten
     * abgeleitet werden.
     */
    private static void addLdpVariantCatchEntries(List<Integer> ldpCategories, Set<String> ownedVariantKeys, List<CatchEntry> out) {
        if (ldpCategories == null || ldpCategories.isEmpty()) return;
        Set<Integer> categories = new HashSet<>(ldpCategories);
        Set<String> regionsLower = new HashSet<>();
        Set<String> shinyRegionsLower = new HashSet<>();
        Set<String> cosmeticSpeciesLower = new HashSet<>();
        Set<String> cosmeticShinySpeciesLower = new HashSet<>();
        List<String> allCosmeticSpecies = LivingDexPlusRegistry.cosmeticSpeciesNames();
        for (int id : ldpCategories) {
            if (LivingDexPlusRegistry.isAnyRegionSyntheticId(id)) {
                String regionLower = LivingDexPlusRegistry.regionNameFromSyntheticId(id).toLowerCase();
                if (LivingDexPlusRegistry.isRegionShinySyntheticId(id)) shinyRegionsLower.add(regionLower);
                else regionsLower.add(regionLower);
            } else if (LivingDexPlusRegistry.isAnyCosmeticSyntheticId(id)) {
                int idx = LivingDexPlusRegistry.cosmeticSpeciesIndexFromSyntheticId(id);
                if (idx < 0 || idx >= allCosmeticSpecies.size()) continue;
                String speciesLower = allCosmeticSpecies.get(idx).toLowerCase();
                if (LivingDexPlusRegistry.isCosmeticShinySyntheticId(id)) cosmeticShinySpeciesLower.add(speciesLower);
                else cosmeticSpeciesLower.add(speciesLower);
            }
        }

        for (LivingDexPlusRegistry.Entry e : LivingDexPlusRegistry.getAll()) {
            int catId = e.categoryId();
            // Kategorie 0/1/7 hier bewusst nicht behandelt (siehe Doc-Kommentar oben).
            if (catId != 2 && catId != 3 && catId != 4 && catId != 5 && catId != 6) continue;
            if (catId == 3 && !regionsLower.contains(e.formName().toLowerCase())) continue;
            if (catId == 4 && !shinyRegionsLower.contains(e.formName().toLowerCase())) continue;
            // Kosmetisch: Arten aus der Unterkategorien-Whitelist (siehe LivingDexPlusRegistry.
            // COSMETIC_SUBCATEGORY_WHITELIST) laufen NUR über ihre eigene synthetische ID, alle
            // anderen Arten weiterhin über die gemeinsame Basis-Kategorie 5/6.
            boolean cosmeticWhitelisted = LivingDexPlusRegistry.COSMETIC_SUBCATEGORY_WHITELIST.contains(e.speciesName().toLowerCase());
            if (catId == 5) {
                if (cosmeticWhitelisted) { if (!cosmeticSpeciesLower.contains(e.speciesName().toLowerCase())) continue; }
                else if (!categories.contains(5)) continue;
            }
            if (catId == 6) {
                if (cosmeticWhitelisted) { if (!cosmeticShinySpeciesLower.contains(e.speciesName().toLowerCase())) continue; }
                else if (!categories.contains(6)) continue;
            }
            if (catId == 2 && !categories.contains(catId)) continue;
            boolean shinyCat = catId == 2 || catId == 4 || catId == 6;

            String formKey = e.formName() == null || e.formName().isBlank() ? "" : e.formName().toLowerCase();
            String key = e.speciesName().toLowerCase() + "|" + formKey + "|" + shinyCat;
            if (ownedVariantKeys.contains(key)) continue;

            ResourceLocation speciesId = TodoHelper.resolveSpeciesId(e.speciesName());
            if (speciesId == null) continue;
            out.add(new CatchEntry(speciesId, true, 0, List.of(), formKey.isEmpty() ? "" : e.formName(), shinyCat));
        }
    }

    /** true, falls "species" keine eigene Vorentwicklung hat (Basis-Form ihrer Entwicklungslinie). */
    private static boolean isRootSpecies(Species species) {
        try {
            var pre = species.getPreEvolution();
            return pre == null || pre.getSpecies() == null;
        } catch (Exception e) {
            return true;
        }
    }

    /** Sammelt rekursiv ALLE Folgestufen (unabhängig vom Besitzstatus) - Grundlage für die Familien-weite Stückzahl-Bilanz oben. */
    private static void collectAllDownstream(String fromLower, List<String> out, Set<String> visitedEdges) {
        Species from = ServerDataHelper.findSpeciesByName(fromLower);
        if (from == null) return;
        for (Evolution evo : from.getEvolutions()) {
            ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
            if (toId == null) continue;
            Species toSpecies = resolveSpeciesRobust(toId);
            if (toSpecies == null) continue;
            String toLower = toSpecies.getName().toLowerCase();
            String edgeKey = fromLower + "->" + toLower;
            if (!visitedEdges.add(edgeKey)) continue;
            out.add(toLower);
            collectAllDownstream(toLower, out, visitedEdges);
        }
    }

    /** Sammelt rekursiv alle Folgestufen der Entwicklungslinie ab "fromLower", die noch NICHT erfüllt sind (Modus-abhängig). */
    private static void collectMissingDownstream(String fromLower, boolean pokedexMode, PokedexManager pokedex,
                                                   Set<String> ownedSpeciesLower, List<ResourceLocation> out, Set<String> visitedEdges) {
        Species from = ServerDataHelper.findSpeciesByName(fromLower);
        if (from == null) return;
        for (Evolution evo : from.getEvolutions()) {
            ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
            if (toId == null) continue;
            Species toSpecies = resolveSpeciesRobust(toId);
            if (toSpecies == null) continue;
            String toLower = toSpecies.getName().toLowerCase();
            String edgeKey = fromLower + "->" + toLower;
            if (!visitedEdges.add(edgeKey)) continue;
            ResourceLocation toRealId = toSpecies.getResourceIdentifier();
            boolean satisfied = pokedexMode
                ? (pokedex != null && ServerDataHelper.hasCaughtSpecies(pokedex, toRealId))
                : ownedSpeciesLower.contains(toLower);
            if (!satisfied) out.add(toRealId);
            collectMissingDownstream(toLower, pokedexMode, pokedex, ownedSpeciesLower, out, visitedEdges);
        }
    }

    private static void walk(String fromSpeciesLower, boolean pokedexMode, PokedexManager pokedex,
                              Set<String> ownedSpeciesLower, Map<String, Integer> bestLevelPerSpecies,
                              Map<String, Integer> ownedCountPerSpecies,
                              Set<String> visitedEdges, Set<String> visitedNodes, Set<String> reachableViaEvolve,
                              List<EvolveEntry> out) {
        if (!visitedNodes.add(fromSpeciesLower)) return;
        Species fromSpecies = ServerDataHelper.findSpeciesByName(fromSpeciesLower);
        if (fromSpecies == null) return;

        for (Evolution evo : fromSpecies.getEvolutions()) {
            String resultName = evo.getResult().getSpecies();
            ResourceLocation toId = TodoHelper.resolveSpeciesId(resultName);
            if (toId == null) continue;
            Species toSpecies = resolveSpeciesRobust(toId);
            if (toSpecies == null) continue;
            String toLower = toSpecies.getName().toLowerCase();
            ResourceLocation toRealId = toSpecies.getResourceIdentifier();

            String edgeKey = fromSpeciesLower + "->" + toLower;
            if (visitedEdges.add(edgeKey)) {
                reachableViaEvolve.add(toLower);
                boolean satisfied = pokedexMode
                    ? (pokedex != null && ServerDataHelper.hasCaughtSpecies(pokedex, toRealId))
                    : ownedSpeciesLower.contains(toLower);
                if (!satisfied) {
                    out.add(buildEntry(evo, fromSpecies, toSpecies, ownedSpeciesLower, bestLevelPerSpecies));
                    // Task "Zwischenstufe-für-LivingDex-Erhalt": im Living-Dex-Modus braucht JEDE
                    // Stufe ihr eigenes Exemplar. Besitzt der Spieler von "fromSpecies" nur EIN
                    // Exemplar, würde das Entwickeln zu "toSpecies" den eigenen Living-Dex-Slot von
                    // "fromSpecies" wieder leeren. Existiert eine Vorstufe mit einem ECHTEN Ersatz-
                    // Exemplar (mind. 2 besessen - 1 bleibt für die Vorstufe selbst, 1 wird zu
                    // "fromSpecies" nachgezogen), wird das als ZUSÄTZLICHER Vorschlag ergänzt (Nutzer-
                    // Beispiel: "Schiggy zu Schillok entwickeln", damit ein Schillok für den Living
                    // Dex übrig bleibt, bevor das einzige Schillok zu Turtok weiterentwickelt wird).
                    if (!pokedexMode && ownedCountPerSpecies.getOrDefault(fromSpeciesLower, 0) <= 1) {
                        maybeAddReplenishEntry(fromSpecies, ownedCountPerSpecies, ownedSpeciesLower,
                            bestLevelPerSpecies, visitedEdges, out);
                    }
                }
            }

            walk(toLower, pokedexMode, pokedex, ownedSpeciesLower, bestLevelPerSpecies, ownedCountPerSpecies,
                visitedEdges, visitedNodes, reachableViaEvolve, out);
        }
    }

    /**
     * Sucht die Vorstufe von "species" und fügt - falls dort ein echtes Ersatz-Exemplar (mind. 2
     * besessen) vorhanden ist - einen Entwickeln-Vorschlag Vorstufe->species hinzu, obwohl dieser
     * Übergang technisch "erfüllt" ist (species wird ja schon besessen). Nur EINE Stufe nach oben
     * (kein rekursives Hochwandern) - bewusst einfach gehalten (Nutzer-Vorgabe: "nice-to-have").
     */
    private static void maybeAddReplenishEntry(Species species, Map<String, Integer> ownedCountPerSpecies,
                                                 Set<String> ownedSpeciesLower, Map<String, Integer> bestLevelPerSpecies,
                                                 Set<String> visitedEdges, List<EvolveEntry> out) {
        try {
            var pre = species.getPreEvolution();
            if (pre == null || pre.getSpecies() == null) return;
            Species preSpecies = pre.getSpecies();
            String preLower = preSpecies.getName().toLowerCase();
            if (ownedCountPerSpecies.getOrDefault(preLower, 0) < 2) return;

            String replenishEdgeKey = "replenish:" + preLower + "->" + species.getName().toLowerCase();
            if (!visitedEdges.add(replenishEdgeKey)) return;

            for (Evolution evo : preSpecies.getEvolutions()) {
                ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
                if (toId == null) continue;
                Species toSpecies = resolveSpeciesRobust(toId);
                if (toSpecies == null || !toSpecies.getName().equalsIgnoreCase(species.getName())) continue;
                out.add(buildEntry(evo, preSpecies, toSpecies, ownedSpeciesLower, bestLevelPerSpecies));
                break;
            }
        } catch (Exception ignored) {}
    }

    private static EvolveEntry buildEntry(Evolution evo, Species from, Species to,
                                           Set<String> ownedSpeciesLower, Map<String, Integer> bestLevelPerSpecies) {
        ResourceLocation fromId = from.getResourceIdentifier();
        ResourceLocation toId = to.getResourceIdentifier();
        String fromLower = from.getName().toLowerCase();
        int ownedLevel = ownedSpeciesLower.contains(fromLower) ? bestLevelPerSpecies.getOrDefault(fromLower, -1) : -1;
        // Hokumil/Milcery & Co: dieselbe Zielspezies kann je nach verwendetem Item zu
        // unterschiedlichen Deko/Creme-Formen führen - ohne die Aspekte hier mitzugeben zeigt die
        // Suchfeld-Vorschau immer die Standardform (Bug-Report), genau wie früher bei
        // TodoHelper.getTodoEntries() vor dessen MULTI_FORM_SPECIES-Fix (siehe dort).
        String resultAspects = TodoHelper.allowsMultiForm(fromLower) ? TodoHelper.getResultAspectsString(evo) : "";

        if (TodoHelper.isTradeEvolution(evo)) {
            String heldItem = TodoHelper.getHeldItemRequirementContext(evo);
            String itemPath = heldItem != null ? resolveItemPath(heldItem) : "";
            return new EvolveEntry(EvolveCategory.TRADE, fromId, toId, -1, ownedLevel, itemPath, resultAspects);
        }

        for (Object req : evo.getRequirements()) {
            String reqClass = req.getClass().getSimpleName().toLowerCase();
            if (reqClass.contains("friendship")) {
                return new EvolveEntry(EvolveCategory.FRIENDSHIP, fromId, toId, -1, ownedLevel, "", resultAspects);
            }
            if (reqClass.contains("level")) {
                int level = TodoHelper.extractLevel(req);
                return new EvolveEntry(EvolveCategory.LEVEL, fromId, toId, level, ownedLevel, "", resultAspects);
            }
        }

        String context = TodoHelper.getRequiredContext(evo);
        if (context != null && !context.isBlank()) {
            return new EvolveEntry(EvolveCategory.STONE, fromId, toId, -1, ownedLevel, resolveItemPath(context), resultAspects);
        }

        String heldItem = TodoHelper.getHeldItemRequirementContext(evo);
        if (heldItem != null) {
            return new EvolveEntry(EvolveCategory.OTHER, fromId, toId, -1, ownedLevel, resolveItemPath(heldItem), resultAspects);
        }

        return new EvolveEntry(EvolveCategory.OTHER, fromId, toId, -1, ownedLevel, "", resultAspects);
    }

    private static String resolveItemPath(String rawContext) {
        ResourceLocation id = TodoHelper.resolveItemId(rawContext);
        if (id != null) return id.getPath();
        return TodoHelper.cleanItemName(rawContext).toLowerCase().replace(" ", "_");
    }

    /** Case-insensitive Pfad-Fallback wie überall sonst in dieser Codebase (Id-Mismatches sind ein wiederkehrendes Problem). */
    private static Species resolveSpeciesRobust(ResourceLocation id) {
        Species direct = PokemonSpecies.INSTANCE.getByIdentifier(id);
        if (direct != null) return direct;
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getResourceIdentifier().getPath().equalsIgnoreCase(id.getPath())) return s;
        }
        return null;
    }
}
