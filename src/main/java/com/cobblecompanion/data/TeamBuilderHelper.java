package com.cobblecompanion.data;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Berechnet Team-Vorschläge (bis zu 6 eigene Pokemon) für den Team-Builder-Tab - drei Modi:
 * "Allgemein" (breite Typ-Abdeckung, Greedy-Set-Cover), "Type" (bester Konter gegen einen
 * gewählten Typ) und "Team" (bester Konter gegen ein eingegebenes Gegner-Team, bis zu 6 Einträge).
 *
 * Nutzt TypeHelper.getBestEffectivenessForPokemon() (eigene Typen UND gelernte Attacken) für die
 * OFFENSIVE Bewertung und TypeHelper.getEffectiveness() gegen die eigenen Typen für die DEFENSIVE
 * Bewertung - dieselbe Typtabelle wie der Types-Tab, keine eigene Kopie.
 *
 * Jedes Ergebnis (TeamResult) enthält jetzt zusätzlich eine ALTERNATIV-Liste (die nächstbesten
 * bis zu 6 Kandidaten) sowie pro Kandidat strukturierte "Gründe" (Reason-Codes wie "OFF:fire"/
 * "SE:water" - wird clientseitig wie beim Types-Tab in lesbaren, übersetzten Text aufgelöst).
 */
public class TeamBuilderHelper {

    public static final class Candidate {
        public final ResourceLocation speciesId;
        public final String aspects;
        public final int level;
        public final List<String> reasons;
        public Candidate(ResourceLocation speciesId, String aspects, int level, List<String> reasons) {
            this.speciesId = speciesId;
            this.aspects = aspects;
            this.level = level;
            this.reasons = reasons;
        }
    }

    public static final class TeamResult {
        public final List<Candidate> primary;
        public final List<Candidate> alternates;
        public TeamResult(List<Candidate> primary, List<Candidate> alternates) {
            this.primary = primary;
            this.alternates = alternates;
        }
    }

    public static final class OpponentEntry {
        public final List<String> types;
        public OpponentEntry(List<String> types) {
            this.types = types;
        }
    }

    /** Pro besessener Spezies das Exemplar mit dem höchsten Level als Kandidat (keine doppelten Spezies im Vorschlag). */
    private static List<Pokemon> ownedRepresentatives(ServerPlayer player) {
        Map<String, Pokemon> bestPerSpecies = new LinkedHashMap<>();
        for (Pokemon p : PlayerDataHelper.getAllPokemon(player)) {
            String key = p.getSpecies().getName().toLowerCase();
            Pokemon existing = bestPerSpecies.get(key);
            if (existing == null || p.getLevel() > existing.getLevel()) bestPerSpecies.put(key, p);
        }
        return new ArrayList<>(bestPerSpecies.values());
    }

    /**
     * "Allgemein": Greedy-Set-Cover über alle 18 Typen - in jeder Runde wird das Pokemon gewählt,
     * das die meisten bisher NICHT abgedeckten Typen zusätzlich abdeckt (offensiv: trifft diesen
     * Typ super-effektiv; defensiv: resistiert/ist immun gegen diesen Typ). Level ist Tie-Breaker
     * (höheres Level gewinnt bei Punktegleichstand), nicht der Haupttreiber - passt zum Nutzer-
     * Wunsch "hohe Level + möglichst große Defender-/Attacke-Typen-Auswahl". Die Alternativ-Liste
     * führt denselben Greedy-Algorithmus für 6 WEITERE Slots fort (derselbe Abdeckungs-Zustand),
     * liefert also die nächstbesten, komplementären Kandidaten statt einer Wiederholung.
     */
    public static TeamResult computeGeneral(ServerPlayer player) {
        List<Pokemon> remaining = ownedRepresentatives(player);
        Set<String> allTypes = TypeHelper.getAllTypeNames();
        Set<String> coveredOffense = new java.util.HashSet<>();
        Set<String> coveredDefense = new java.util.HashSet<>();

        List<Candidate> primary = greedyRound(remaining, allTypes, coveredOffense, coveredDefense, 6);
        List<Candidate> alternates = greedyRound(remaining, allTypes, coveredOffense, coveredDefense, 6);
        return new TeamResult(primary, alternates);
    }

    private static List<Candidate> greedyRound(List<Pokemon> remaining, Set<String> allTypes,
                                                 Set<String> coveredOffense, Set<String> coveredDefense, int slots) {
        List<Pokemon> team = new ArrayList<>();
        List<List<String>> reasonsPerPick = new ArrayList<>();
        for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
            Pokemon best = null;
            int bestGain = -1;
            for (Pokemon p : remaining) {
                int gain = coverageGain(p, allTypes, coveredOffense, coveredDefense);
                if (best == null || gain > bestGain || (gain == bestGain && p.getLevel() > best.getLevel())) {
                    bestGain = gain;
                    best = p;
                }
            }
            if (best == null || bestGain <= 0) break; // keine weitere Abdeckung mehr möglich -> keine sinnvolle Alternative
            team.add(best);
            remaining.remove(best);
            reasonsPerPick.add(applyCoverage(best, allTypes, coveredOffense, coveredDefense));
        }
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < team.size(); i++) result.add(toCandidate(team.get(i), reasonsPerPick.get(i)));
        return result;
    }

    private static int coverageGain(Pokemon p, Set<String> allTypes, Set<String> coveredOffense, Set<String> coveredDefense) {
        List<String> myTypes = TypeHelper.getPokemonOwnTypes(p);
        int gain = 0;
        for (String t : allTypes) {
            if (!coveredOffense.contains(t) && TypeHelper.getBestEffectivenessForPokemon(p, List.of(t)) >= 2.0) gain++;
            if (!coveredDefense.contains(t) && TypeHelper.getEffectiveness(t, myTypes) <= 0.5) gain++;
        }
        return gain;
    }

    /** Wendet die Abdeckung an UND liefert die dabei NEU abgedeckten Typen als Reason-Codes zurück. */
    private static List<String> applyCoverage(Pokemon p, Set<String> allTypes, Set<String> coveredOffense, Set<String> coveredDefense) {
        List<String> myTypes = TypeHelper.getPokemonOwnTypes(p);
        List<String> reasons = new ArrayList<>();
        for (String t : allTypes) {
            if (TypeHelper.getBestEffectivenessForPokemon(p, List.of(t)) >= 2.0 && coveredOffense.add(t)) reasons.add("OFF:" + t);
            if (TypeHelper.getEffectiveness(t, myTypes) <= 0.5 && coveredDefense.add(t)) reasons.add("DEF:" + t);
        }
        return reasons;
    }

    /** "Type": bester Konter gegen einen einzelnen gewählten Typ, plus Alternativ-Liste. */
    public static TeamResult computeAgainstType(ServerPlayer player, String type) {
        return computeAgainstOpponents(player, List.of(new OpponentEntry(List.of(type.toLowerCase()))));
    }

    /**
     * "Team": bester Konter gegen ein eingegebenes Gegner-Team (bis zu 6 Einträge) - Score je
     * eigenem Kandidaten ist die SUMME der Einzel-Scores gegen jeden Gegner (resistiert diesen
     * Gegner + trifft diesen Gegner super-effektiv), damit ein Kandidat der gegen MEHRERE Gegner
     * gut steht bevorzugt wird gegenüber einem der nur gegen einen Gegner extrem stark ist.
     * Alternativ-Liste = die nächstbesten 6 Kandidaten aus demselben sortierten Pool.
     */
    public static TeamResult computeAgainstOpponents(ServerPlayer player, List<OpponentEntry> opponents) {
        List<Pokemon> pool = ownedRepresentatives(player);
        Comparator<Pokemon> byScoreDescThenLevel = (a, b) -> {
            double sa = totalScoreAgainst(a, opponents);
            double sb = totalScoreAgainst(b, opponents);
            if (sa != sb) return Double.compare(sb, sa);
            return Integer.compare(b.getLevel(), a.getLevel());
        };
        pool.sort(byScoreDescThenLevel);

        List<Pokemon> primaryPool = pool.stream().limit(6).collect(Collectors.toList());
        List<Pokemon> alternatePool = pool.stream().skip(6).limit(6).collect(Collectors.toList());
        List<Candidate> primary = new ArrayList<>();
        for (Pokemon p : primaryPool) primary.add(toCandidate(p, reasonsAgainst(p, opponents)));
        List<Candidate> alternates = new ArrayList<>();
        for (Pokemon p : alternatePool) alternates.add(toCandidate(p, reasonsAgainst(p, opponents)));
        return new TeamResult(primary, alternates);
    }

    private static double totalScoreAgainst(Pokemon p, List<OpponentEntry> opponents) {
        double total = 0;
        for (OpponentEntry o : opponents) total += scoreAgainst(p, o.types);
        return total;
    }

    /** +2 falls p den Gegnertyp resistiert/ist immun, +2 falls p den Gegnertyp super-effektiv trifft. */
    private static double scoreAgainst(Pokemon p, List<String> opponentTypes) {
        List<String> myTypes = TypeHelper.getPokemonOwnTypes(p);
        double score = 0;
        for (String t : opponentTypes) {
            if (TypeHelper.getEffectiveness(t, myTypes) <= 0.5) score += 2;
            if (TypeHelper.getBestEffectivenessForPokemon(p, List.of(t)) >= 2.0) score += 2;
        }
        return score;
    }

    /** Reason-Codes für "Type"/"Team"-Modus: pro Gegner-Typ, den p resistiert bzw. super-effektiv trifft. */
    private static List<String> reasonsAgainst(Pokemon p, List<OpponentEntry> opponents) {
        List<String> myTypes = TypeHelper.getPokemonOwnTypes(p);
        List<String> reasons = new ArrayList<>();
        Set<String> seenTypes = new java.util.HashSet<>();
        for (OpponentEntry o : opponents) {
            for (String t : o.types) {
                if (!seenTypes.add(t)) continue;
                if (TypeHelper.getEffectiveness(t, myTypes) <= 0.5) reasons.add("RES:" + t);
                if (TypeHelper.getBestEffectivenessForPokemon(p, List.of(t)) >= 2.0) reasons.add("SE:" + t);
            }
        }
        return reasons;
    }

    /** Löst einen (bereits auf den internen englischen Namen aufgelösten) Gegner-Spezies-Namen in seine Typen auf. */
    public static OpponentEntry resolveOpponentTypes(String speciesName) {
        Species species = ServerDataHelper.findSpeciesByName(speciesName);
        if (species == null) return null;
        List<String> types = new ArrayList<>();
        species.getTypes().forEach(t -> types.add(t.getName().toLowerCase()));
        return new OpponentEntry(types);
    }

    private static Candidate toCandidate(Pokemon p, List<String> reasons) {
        String aspects = String.join(",", p.getAspects());
        return new Candidate(p.getSpecies().getResourceIdentifier(), aspects, p.getLevel(), reasons);
    }
}
