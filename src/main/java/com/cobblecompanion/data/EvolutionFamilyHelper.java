package com.cobblecompanion.data;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serverseitig: berechnet für eine Art die "effektive" Pokédex-Nummer, die der Pokédex-Sortiermodus
 * für die Box/Slot-Zuordnung verwenden soll - NICHT die eigene Pokédex-Nummer der Art, sondern die
 * eines gemeinsamen Familien-Repräsentanten, damit eine ganze Entwicklungsfamilie (Glumanda/Glutexo/
 * Glurak) EINEN Slot belegt statt drei. Ausnahme (Nutzer-Vorgabe, Live-Test-Bugreport "Evoli"):
 * verzweigt sich eine Familie AN DER WURZEL in mehrere gleichzeitige Entwicklungsmöglichkeiten
 * (z.B. Evoli -> Aquana/Blitza/Flamara/...), bekommt JEDER Zweig seinen eigenen Slot, plus die
 * Wurzel-Art selbst (unentwickeltes Evoli) einen weiteren.
 *
 * Muss serverseitig laufen: Species.getEvolutions()/getPreEvolution() sind auf einem echten
 * Dedicated Server clientseitig unzuverlässig (siehe EvolutionChainRequestPacket-Kommentar) -
 * gleiche Begründung wie dort, deshalb auch hier ein Server-Roundtrip nötig (siehe
 * FamilySlotRequestPacket/ResponsePacket).
 *
 * Kollisionsfreiheit: jeder zurückgegebene Wert ist die ECHTE Pokédex-Nummer einer echten Art
 * (Minimum innerhalb des jeweiligen Teilbaums) - nie eine künstlich verschobene "virtuelle" Nummer.
 * Dadurch kann kein Zweig einer Familie versehentlich in den Slot einer völlig anderen, unrelated
 * Familie fallen (was bei einem festen Offset-Schema von der Wurzel aus passieren könnte, wenn
 * Zweige - wie bei Evoli - über weit auseinanderliegende Pokédex-Bereiche verteilt sind).
 */
public class EvolutionFamilyHelper {

    private static final int GUARD_CAP = 50;

    public static int effectiveAnchorDexNumber(Species species) {
        try {
            Species root = familyRoot(species);
            List<Species> rootChildren = sortedDirectEvolutionTargets(root);

            if (rootChildren.size() <= 1) {
                // Lineare Familie (keine Verzweigung an der Wurzel) -> kollabiert komplett auf
                // die niedrigste Pokédex-Nummer innerhalb der gesamten Familie.
                return minDexNumberInClosure(root);
            }

            if (sameSpecies(species, root)) {
                return root.getNationalPokedexNumber();
            }

            for (Species child : rootChildren) {
                if (sameSpecies(species, child) || isInClosure(species, child)) {
                    return minDexNumberInClosure(child);
                }
            }

            // Sollte bei korrekten Entwicklungsdaten nie passieren - sichere Rückfallebene.
            return species.getNationalPokedexNumber();
        } catch (Exception e) {
            return species.getNationalPokedexNumber();
        }
    }

    /** Läuft über PreEvolution-Kette nach oben bis zur Wurzel-Art der Familie. */
    private static Species familyRoot(Species species) {
        Species current = species;
        int guard = 0;
        while (guard++ < 20) {
            PreEvolution pre = current.getPreEvolution();
            if (pre == null) break;
            Species preSpecies = pre.getSpecies();
            if (preSpecies == null || sameSpecies(preSpecies, current)) break;
            current = preSpecies;
        }
        return current;
    }

    /** Direkte Entwicklungsziele einer Art, dedupliziert + deterministisch sortiert (Pfad-Name). */
    private static List<Species> sortedDirectEvolutionTargets(Species species) {
        List<Species> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Evolution evo : species.getEvolutions()) {
            Species target = resolveEvolutionTarget(evo);
            if (target == null) continue;
            String key = target.getResourceIdentifier().getPath().toLowerCase();
            if (seen.add(key)) result.add(target);
        }
        result.sort(Comparator.comparing(s -> s.getResourceIdentifier().getPath().toLowerCase()));
        return result;
    }

    /** true, wenn `species` irgendwo in der Entwicklungs-BFS ausgehend von `subtreeRoot` liegt (subtreeRoot selbst eingeschlossen). */
    private static boolean isInClosure(Species species, Species subtreeRoot) {
        Set<String> visited = new HashSet<>();
        Deque<Species> queue = new ArrayDeque<>();
        String targetKey = species.getResourceIdentifier().getPath().toLowerCase();
        visited.add(subtreeRoot.getResourceIdentifier().getPath().toLowerCase());
        queue.add(subtreeRoot);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < GUARD_CAP) {
            Species current = queue.poll();
            if (current.getResourceIdentifier().getPath().equalsIgnoreCase(targetKey)) return true;
            for (Evolution evo : current.getEvolutions()) {
                Species next = resolveEvolutionTarget(evo);
                if (next == null) continue;
                String key = next.getResourceIdentifier().getPath().toLowerCase();
                if (visited.add(key)) queue.add(next);
            }
        }
        return false;
    }

    /** Minimale Pokédex-Nummer über subtreeRoot + alle per Entwicklung erreichbaren Arten. */
    private static int minDexNumberInClosure(Species subtreeRoot) {
        int min = subtreeRoot.getNationalPokedexNumber();
        Set<String> visited = new HashSet<>();
        Deque<Species> queue = new ArrayDeque<>();
        visited.add(subtreeRoot.getResourceIdentifier().getPath().toLowerCase());
        queue.add(subtreeRoot);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < GUARD_CAP) {
            Species current = queue.poll();
            int dex = current.getNationalPokedexNumber();
            if (dex > 0 && (min <= 0 || dex < min)) min = dex;
            for (Evolution evo : current.getEvolutions()) {
                Species next = resolveEvolutionTarget(evo);
                if (next == null) continue;
                String key = next.getResourceIdentifier().getPath().toLowerCase();
                if (visited.add(key)) queue.add(next);
            }
        }
        return min;
    }

    private static Species resolveEvolutionTarget(Evolution evo) {
        try {
            var toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
            if (toId == null) return null;
            Species direct = PokemonSpecies.INSTANCE.getByIdentifier(toId);
            if (direct != null) return direct;
            for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
                if (s.getResourceIdentifier().getPath().equalsIgnoreCase(toId.getPath())) return s;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameSpecies(Species a, Species b) {
        return a.getResourceIdentifier().getPath().equalsIgnoreCase(b.getResourceIdentifier().getPath());
    }
}
