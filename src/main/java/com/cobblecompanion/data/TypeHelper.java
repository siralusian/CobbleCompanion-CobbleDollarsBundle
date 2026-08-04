package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.*;

public class TypeHelper {

    // Type chart: attacking type -> defending type -> multiplier
    // Key = attacking type, Value = map of defending type -> multiplier
    private static final Map<String, Map<String, Double>> TYPE_CHART = new HashMap<>();

    static {
        // Normal
        addRelation("normal", "rock", 0.5);
        addRelation("normal", "ghost", 0.0);
        addRelation("normal", "steel", 0.5);

        // Fire
        addRelation("fire", "fire", 0.5);
        addRelation("fire", "water", 0.5);
        addRelation("fire", "grass", 2.0);
        addRelation("fire", "ice", 2.0);
        addRelation("fire", "bug", 2.0);
        addRelation("fire", "rock", 0.5);
        addRelation("fire", "dragon", 0.5);
        addRelation("fire", "steel", 2.0);

        // Water
        addRelation("water", "fire", 2.0);
        addRelation("water", "water", 0.5);
        addRelation("water", "grass", 0.5);
        addRelation("water", "ground", 2.0);
        addRelation("water", "rock", 2.0);
        addRelation("water", "dragon", 0.5);

        // Electric
        addRelation("electric", "water", 2.0);
        addRelation("electric", "electric", 0.5);
        addRelation("electric", "grass", 0.5);
        addRelation("electric", "ground", 0.0);
        addRelation("electric", "flying", 2.0);
        addRelation("electric", "dragon", 0.5);

        // Grass
        addRelation("grass", "fire", 0.5);
        addRelation("grass", "water", 2.0);
        addRelation("grass", "grass", 0.5);
        addRelation("grass", "poison", 0.5);
        addRelation("grass", "ground", 2.0);
        addRelation("grass", "flying", 0.5);
        addRelation("grass", "bug", 0.5);
        addRelation("grass", "rock", 2.0);
        addRelation("grass", "dragon", 0.5);
        addRelation("grass", "steel", 0.5);

        // Ice
        addRelation("ice", "water", 0.5);
        addRelation("ice", "grass", 2.0);
        addRelation("ice", "ice", 0.5);
        addRelation("ice", "ground", 2.0);
        addRelation("ice", "flying", 2.0);
        addRelation("ice", "dragon", 2.0);
        addRelation("ice", "steel", 0.5);

        // Fighting
        addRelation("fighting", "normal", 2.0);
        addRelation("fighting", "ice", 2.0);
        addRelation("fighting", "poison", 0.5);
        addRelation("fighting", "flying", 0.5);
        addRelation("fighting", "psychic", 0.5);
        addRelation("fighting", "bug", 0.5);
        addRelation("fighting", "rock", 2.0);
        addRelation("fighting", "ghost", 0.0);
        addRelation("fighting", "dark", 2.0);
        addRelation("fighting", "steel", 2.0);
        addRelation("fighting", "fairy", 0.5);

        // Poison
        addRelation("poison", "grass", 2.0);
        addRelation("poison", "poison", 0.5);
        addRelation("poison", "ground", 0.5);
        addRelation("poison", "rock", 0.5);
        addRelation("poison", "ghost", 0.5);
        addRelation("poison", "steel", 0.0);
        addRelation("poison", "fairy", 2.0);

        // Ground
        addRelation("ground", "fire", 2.0);
        addRelation("ground", "electric", 2.0);
        addRelation("ground", "grass", 0.5);
        addRelation("ground", "poison", 2.0);
        addRelation("ground", "flying", 0.0);
        addRelation("ground", "bug", 0.5);
        addRelation("ground", "rock", 2.0);
        addRelation("ground", "steel", 2.0);

        // Flying
        addRelation("flying", "electric", 0.5);
        addRelation("flying", "grass", 2.0);
        addRelation("flying", "fighting", 2.0);
        addRelation("flying", "bug", 2.0);
        addRelation("flying", "rock", 0.5);
        addRelation("flying", "steel", 0.5);

        // Psychic
        addRelation("psychic", "fighting", 2.0);
        addRelation("psychic", "poison", 2.0);
        addRelation("psychic", "psychic", 0.5);
        addRelation("psychic", "dark", 0.0);
        addRelation("psychic", "steel", 0.5);

        // Bug
        addRelation("bug", "fire", 0.5);
        addRelation("bug", "grass", 2.0);
        addRelation("bug", "fighting", 0.5);
        addRelation("bug", "poison", 0.5);
        addRelation("bug", "flying", 0.5);
        addRelation("bug", "psychic", 2.0);
        addRelation("bug", "ghost", 0.5);
        addRelation("bug", "dark", 2.0);
        addRelation("bug", "steel", 0.5);
        addRelation("bug", "fairy", 0.5);

        // Rock
        addRelation("rock", "fire", 2.0);
        addRelation("rock", "ice", 2.0);
        addRelation("rock", "fighting", 0.5);
        addRelation("rock", "ground", 0.5);
        addRelation("rock", "flying", 2.0);
        addRelation("rock", "bug", 2.0);
        addRelation("rock", "steel", 0.5);

        // Ghost
        addRelation("ghost", "normal", 0.0);
        addRelation("ghost", "psychic", 2.0);
        addRelation("ghost", "ghost", 2.0);
        addRelation("ghost", "dark", 0.5);

        // Dragon
        addRelation("dragon", "dragon", 2.0);
        addRelation("dragon", "steel", 0.5);
        addRelation("dragon", "fairy", 0.0);

        // Dark
        addRelation("dark", "fighting", 0.5);
        addRelation("dark", "psychic", 2.0);
        addRelation("dark", "ghost", 2.0);
        addRelation("dark", "dark", 0.5);
        addRelation("dark", "fairy", 0.5);

        // Steel
        addRelation("steel", "fire", 0.5);
        addRelation("steel", "water", 0.5);
        addRelation("steel", "electric", 0.5);
        addRelation("steel", "ice", 2.0);
        addRelation("steel", "rock", 2.0);
        addRelation("steel", "steel", 0.5);
        addRelation("steel", "fairy", 2.0);

        // Fairy
        addRelation("fairy", "fire", 0.5);
        addRelation("fairy", "fighting", 2.0);
        addRelation("fairy", "poison", 0.5);
        addRelation("fairy", "dragon", 2.0);
        addRelation("fairy", "dark", 2.0);
        addRelation("fairy", "steel", 0.5);
    }

    private static void addRelation(String attackType, String defenseType, double multiplier) {
        TYPE_CHART.computeIfAbsent(attackType, k -> new HashMap<>()).put(defenseType, multiplier);
    }

    /**
     * Berechnet Effektivität von attackType gegen ein Pokemon mit defenderTypes.
     * Berücksichtigt Dual-Typ (z.B. Gras/Gift gegen Feuer = 0.5 * 1.0 = 0.5)
     */
    public static double getEffectiveness(String attackType, List<String> defenderTypes) {
        String atk = attackType.toLowerCase();
        Map<String, Double> relations = TYPE_CHART.getOrDefault(atk, Collections.emptyMap());
        double multiplier = 1.0;
        for (String def : defenderTypes) {
            multiplier *= relations.getOrDefault(def.toLowerCase(), 1.0);
        }
        return multiplier;
    }

    /**
     * Gibt alle Angriffs-Typen zurück die gegen defenderTypes effektiv sind (>= 2x).
     */
    public static List<String> getTypesStrongAgainst(List<String> defenderTypes) {
        List<String> strong = new ArrayList<>();
        for (String attackType : TYPE_CHART.keySet()) {
            double eff = getEffectiveness(attackType, defenderTypes);
            if (eff >= 2.0) {
                String display = capitalize(attackType);
                if (eff >= 4.0) display += " (4x)";
                strong.add(display);
            }
        }
        // Sortiere: 4x vor 2x
        strong.sort((a, b) -> {
            boolean a4x = a.contains("4x");
            boolean b4x = b.contains("4x");
            if (a4x && !b4x) return -1;
            if (!a4x && b4x) return 1;
            return a.compareTo(b);
        });
        return strong;
    }

    public static class TypeMultiplier {
        public final String typeName;
        public final double multiplier;

        public TypeMultiplier(String typeName, double multiplier) {
            this.typeName = typeName;
            this.multiplier = multiplier;
        }
    }

    /**
     * Wie getTypesStrongAgainst(), aber als strukturierte Daten (Typname + Multiplikator
     * getrennt) statt vorformatiertem Anzeigetext - für den Type-Tab im GUI, der davor noch ein
     * Typ-Icon zeichnen will.
     */
    public static List<TypeMultiplier> getStrongTypesStructured(List<String> defenderTypes) {
        List<TypeMultiplier> strong = new ArrayList<>();
        for (String attackType : TYPE_CHART.keySet()) {
            double eff = getEffectiveness(attackType, defenderTypes);
            if (eff >= 2.0) strong.add(new TypeMultiplier(attackType, eff));
        }
        strong.sort((a, b) -> {
            boolean a4x = a.multiplier >= 4.0;
            boolean b4x = b.multiplier >= 4.0;
            if (a4x && !b4x) return -1;
            if (!a4x && b4x) return 1;
            return a.typeName.compareTo(b.typeName);
        });
        return strong;
    }

    public static double getBestEffectivenessForPokemon(Pokemon p, List<String> defenderTypes) {
    double best = 0.0;

    // Prüfe eigenen Pokemon-Typ
    for (Object typeObj : p.getTypes()) {
        String typeName = extractTypeName(typeObj);
        if (typeName == null) continue;
        double eff = getEffectiveness(typeName, defenderTypes);
        if (eff > best) best = eff;
    }

    // Prüfe gelernte Moves (getMoveSet().getMoves())
    try {
        Object moveSet = p.getClass().getMethod("getMoveSet").invoke(p);
        @SuppressWarnings("unchecked")
        List<Object> moves = (List<Object>) moveSet.getClass().getMethod("getMoves").invoke(moveSet);
        for (Object move : moves) {
            if (move == null) continue;
            Object typeObj = move.getClass().getMethod("getType").invoke(move);
            String typeName = extractTypeName(typeObj);
            if (typeName == null) continue;
            double eff = getEffectiveness(typeName, defenderTypes);
            if (eff > best) best = eff;
        }
    } catch (Exception ignored) {}

    return best;
}

// Nur eigener Pokemon-Typ, keine Moves
public static double getPokemonTypeEffectiveness(Pokemon p, List<String> defenderTypes) {
    double best = 0.0;
    for (Object typeObj : p.getTypes()) {
        String typeName = extractTypeName(typeObj);
        if (typeName == null) continue;
        double eff = getEffectiveness(typeName, defenderTypes);
        if (eff > best) best = eff;
    }
    return best;
}

/** Eigene Typen (primär+sekundär) eines Pokemon als Kleinbuchstaben-Liste, für Team-Builder-Defensiv-Score. */
public static List<String> getPokemonOwnTypes(Pokemon p) {
    List<String> result = new ArrayList<>();
    for (Object typeObj : p.getTypes()) {
        String typeName = extractTypeName(typeObj);
        if (typeName != null) result.add(typeName);
    }
    return result;
}

/** Alle 18 bekannten Typnamen (Kleinbuchstaben) - jeder Typ hat mindestens eine Relation als Angreifer in TYPE_CHART. */
public static java.util.Set<String> getAllTypeNames() {
    return TYPE_CHART.keySet();
}

/**
 * Findet die stärkste gelernte Attacke, die (a) Schaden verursacht (power > 0, Status-Attacken
 * mit power=0 zählen nicht) und (b) mindestens 2x effektiv gegen defenderTypes ist. Bei
 * mehreren qualifizierenden Attacken gewinnt die mit der höchsten Basis-Power. Gibt die
 * interne Move-Id zurück (z.B. "watergun", passend zu Cobblemons "cobblemon.move.<id>"
 * Sprachschlüssel - der Client übersetzt daraus den Anzeigenamen), oder null wenn keine
 * passende Attacke gelernt ist. Bewusst die Id statt getDisplayName(): Letzteres würde
 * serverseitig in Server-Sprache aufgelöst statt in der Sprache des Clients.
 */
private static String getBestDamagingMoveId(Pokemon p, List<String> defenderTypes) {
    String bestId = null;
    double bestPower = -1;
    try {
        for (Move move : p.getMoveSet().getMoves()) {
            if (move == null || move.getPower() <= 0) continue;
            String typeName = extractTypeName(move.getType());
            if (typeName == null) continue;
            double eff = getEffectiveness(typeName, defenderTypes);
            if (eff >= 2.0 && move.getPower() > bestPower) {
                bestPower = move.getPower();
                bestId = move.getName();
            }
        }
    } catch (Exception ignored) {}
    return bestId;
}

private static String extractTypeName(Object typeObj) {
    // Versuche getName() aufzurufen
    try {
        java.lang.reflect.Method m = typeObj.getClass().getMethod("getName");
        Object val = m.invoke(typeObj);
        if (val != null) return val.toString().toLowerCase();
    } catch (Exception ignored) {}

    // Fallback: displayName
    try {
        java.lang.reflect.Method m = typeObj.getClass().getMethod("getDisplayName");
        Object val = m.invoke(typeObj);
        if (val != null) return val.toString().toLowerCase();
    } catch (Exception ignored) {}

    // Fallback: name Feld
    try {
        java.lang.reflect.Field f = typeObj.getClass().getDeclaredField("name");
        f.setAccessible(true);
        Object val = f.get(typeObj);
        if (val != null) return val.toString().toLowerCase();
    } catch (Exception ignored) {}

    return null;
}

    public static boolean isValidType(String typeName) {
        return TYPE_CHART.containsKey(typeName.toLowerCase()) ||
               typeName.equalsIgnoreCase("normal");
    }

    public static List<String> getAllTypes() {
        List<String> types = new ArrayList<>(TYPE_CHART.keySet());
        if (!types.contains("normal")) types.add("normal");
        return types;
    }

    public static boolean isInBattle(ServerPlayer player) {
        try {
            Object battleRegistry = Cobblemon.INSTANCE.getBattleRegistry();
            for (Method m : battleRegistry.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("getbattle") &&
                    m.getParameterCount() == 1) {
                    Object battle = m.invoke(battleRegistry, player.getUUID());
                    return battle != null;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static Species findSpeciesByName(String name) {
        for (Species s : PokemonSpecies.INSTANCE.getSpecies()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    /**
     * Baut den kompletten "Strong against ..." Report (Effektive Typen + eigenes Team/PC
     * sortiert nach Punkte-System). Wird von /companion type und vom Type-Tab im GUI
     * genutzt. Gibt null zurück, wenn query weder ein bekannter Typ noch ein Pokemon-Name ist.
     */
    public static String buildTypeReport(ServerPlayer player, String query) {
        List<String> defenderTypes = new ArrayList<>();
        String queryDisplay;

        Species querySpecies = findSpeciesByName(query);
        if (querySpecies != null) {
            querySpecies.getTypes().forEach(t -> defenderTypes.add(t.getName().toLowerCase()));
            queryDisplay = querySpecies.getName() + " (" +
                String.join("/", defenderTypes.stream().map(TypeHelper::capitalize).toList()) + ")";
        } else if (isValidType(query)) {
            defenderTypes.add(query.toLowerCase());
            queryDisplay = capitalize(query);
        } else {
            return null;
        }

        List<String> strongTypes = getTypesStrongAgainst(defenderTypes);

        StringBuilder msg = new StringBuilder();
        msg.append("§6=== Strong against ").append(queryDisplay).append(" ===\n");

        if (strongTypes.isEmpty()) {
            msg.append("§7No types are super effective.\n");
        } else {
            msg.append("§7Effective types: §e")
               .append(String.join("§7, §e", strongTypes)).append("\n");
        }

        boolean inBattle = isInBattle(player);

        List<Pokemon> partyPokemon = PlayerDataHelper.getParty(player);
        List<PokemonTypeResult> partyResults = evaluatePokemon(partyPokemon, defenderTypes);

        if (!partyResults.isEmpty()) {
            msg.append("§7Your team:\n");
            for (PokemonTypeResult r : partyResults) {
                msg.append(formatPokemonResult(r)).append("\n");
            }
        } else {
            msg.append("§8No effective Pokemon in your party.\n");
        }

        List<Pokemon> pcPokemon = PlayerDataHelper.getPC(player);
        List<PokemonTypeResult> pcResults = evaluatePokemon(pcPokemon, defenderTypes);

        if (!pcResults.isEmpty()) {
            int limit = inBattle ? 5 : 10;
            msg.append("§7Your PC (top ").append(limit).append("):\n");
            for (int i = 0; i < Math.min(limit, pcResults.size()); i++) {
                msg.append(formatPokemonResult(pcResults.get(i))).append("\n");
            }
        }

        return msg.toString().trim();
    }

    private static String formatPokemonResult(PokemonTypeResult r) {
        String effText;
        if (r.typeEffectiveness >= 4.0) effText = "4x";
        else if (r.typeEffectiveness >= 2.0) effText = "2x";
        else effText = "1x";
        return "  " + r.color + r.name + " §7(Lv." + r.level + " §f" + effText + "§7)";
    }

    /**
     * Baut die strukturierten Daten für den Type-Tab im GUI (Slot-Layout statt Fließtext).
     * Bewusst OHNE fertig formulierten Text (weder Titel noch Stichworte) - der Client baut
     * daraus über Sprachschlüssel den übersetzten Anzeigetext, damit das GUI komplett
     * lokalisierbar ist. Zeilenformate:
     *   D|<defTyp1>,<defTyp2 oder leer> - Typ(en) des Suchbegriffs (auch fürs Icon/den Titel)
     *   Q|<speciesId> - nur vorhanden, wenn nach einem Pokemon (nicht einem Typ) gesucht wurde
     *   S|<typ>|<multiplikator "2" oder "4"> - ein Eintrag je effektivem Angriffstyp
     *   E|section|speciesId|level|aspects|color|keywords - section = "party"/"pc", keywords
     *       sind Marker (kein Fließtext): "M:<moveId>" (Attacke, Id für cobblemon.move.<id>),
     *       "T2"/"T4" (Typ 2x/4x effektiv), "TOP" (Top-Level)
     * Gibt null zurück, wenn query weder ein bekannter Typ noch ein Pokemon-Name ist.
     */
    public static List<String> buildTypeResultsForGui(ServerPlayer player, String query) {
        List<String> defenderTypes = new ArrayList<>();
        ResourceLocation querySpeciesId = null;

        Species querySpecies = findSpeciesByName(query);
        if (querySpecies != null) {
            querySpecies.getTypes().forEach(t -> defenderTypes.add(t.getName().toLowerCase()));
            querySpeciesId = querySpecies.getResourceIdentifier();
        } else if (isValidType(query)) {
            defenderTypes.add(query.toLowerCase());
        } else {
            return null;
        }

        List<String> lines = new ArrayList<>();
        lines.add("D|" + defenderTypes.get(0) + "," + (defenderTypes.size() > 1 ? defenderTypes.get(1) : ""));
        if (querySpeciesId != null) lines.add("Q|" + querySpeciesId);

        for (TypeMultiplier tm : getStrongTypesStructured(defenderTypes)) {
            lines.add("S|" + tm.typeName + "|" + (tm.multiplier >= 4.0 ? "4" : "2"));
        }

        boolean inBattle = isInBattle(player);

        List<PokemonTypeResult> partyResults = evaluatePokemon(PlayerDataHelper.getParty(player), defenderTypes);
        for (PokemonTypeResult r : partyResults) lines.add(encodeTypeResult("party", r));

        List<PokemonTypeResult> pcResults = evaluatePokemon(PlayerDataHelper.getPC(player), defenderTypes);
        int limit = inBattle ? 5 : 10;
        for (int i = 0; i < Math.min(limit, pcResults.size()); i++) lines.add(encodeTypeResult("pc", pcResults.get(i)));

        return lines;
    }

    private static String encodeTypeResult(String section, PokemonTypeResult r) {
        List<String> keywords = new ArrayList<>();
        if (r.bestMoveId != null) keywords.add("M:" + r.bestMoveId);
        if (r.typeOnlyEffectiveness >= 2.0) keywords.add(r.typeOnlyEffectiveness >= 4.0 ? "T4" : "T2");
        if (r.isTopLevel) keywords.add("TOP");

        ResourceLocation speciesId = r.pokemon.getSpecies().getResourceIdentifier();
        return "E|" + section + "|" + speciesId + "|" + r.level + "|" +
            String.join(",", r.pokemon.getAspects()) + "|" + r.color + "|" + String.join(";", keywords);
    }

    // ===== Punkte-System =====

    public static class PokemonTypeResult implements Comparable<PokemonTypeResult> {
        public Pokemon pokemon;
        public String name;
        public int level;
        public double typeEffectiveness; // beste verfügbare Effektivität (eigener Typ ODER Attacke)
        public double typeOnlyEffectiveness; // nur eigener Pokemon-Typ vs Defender
        public String bestMoveId; // interne Id der stärksten schadenverursachenden Attacke des effektiven Typs, oder null
        public boolean isTopLevel;
        public int points;
        public String color; // ANSI/Minecraft color code

        public PokemonTypeResult(Pokemon p, double typeEff) {
            this.pokemon = p;
            this.name = p.getSpecies().getName();
            this.level = p.getLevel();
            this.typeEffectiveness = typeEff;
        }

        @Override
        public int compareTo(PokemonTypeResult other) {
            // Mehr Punkte zuerst, bei Gleichstand höheres Level
            if (other.points != this.points) return other.points - this.points;
            return other.level - this.level;
        }
    }

    /**
     * Bewertet Pokemon nach dem Punkte-System und gibt sortierte Liste zurück.
     * Nur Pokemon mit mindestens 1x Effektivität (eigenem Typ) werden berücksichtigt.
     *
     * Punkte:
     * +1 wenn Pokemon-Typ >= 1x effektiv gegen Defender
     * +1 wenn Pokemon-Typ >= 2x effektiv gegen Defender (höchste verfügbare Effektivität)
     * +1 wenn Level im Top-Bereich (höchstes Level - 5)
     * +1 wenn höchste Effektivität aller verfügbaren Pokemon
     */
    public static List<PokemonTypeResult> evaluatePokemon(
            List<Pokemon> pokemonList,
            List<String> defenderTypes) {

        List<PokemonTypeResult> results = new ArrayList<>();

        // Erst alle bewerten
        double maxEff = 0.0;
        int maxLevel = 0;

        for (Pokemon p : pokemonList) {
            double eff = getBestEffectivenessForPokemon(p, defenderTypes);
            if (eff >= 1.0) { // mindestens neutral - nur dann relevant
                PokemonTypeResult r = new PokemonTypeResult(p, eff);
                results.add(r);
                if (eff > maxEff) maxEff = eff;
                if (p.getLevel() > maxLevel) maxLevel = p.getLevel();
            }
        }

        // Nur Pokemon die mindestens 2x Effektivität haben anzeigen
        // (bei reinem Typ-Command: nur 2x+, bei Pokemon-Command: auch 2x+)
        results.removeIf(r -> r.typeEffectiveness < 2.0);

        if (results.isEmpty()) return results;

        // Neu berechnen nach Filter
        maxEff = 0.0;
        maxLevel = 0;
        for (PokemonTypeResult r : results) {
            if (r.typeEffectiveness > maxEff) maxEff = r.typeEffectiveness;
            if (r.level > maxLevel) maxLevel = r.level;
        }

        final double finalMaxEff = maxEff;
        final int finalMaxLevel = maxLevel;

        // Punkte vergeben
        for (PokemonTypeResult r : results) {
    int pts = 0;

    // +1: Pokemon-Typ ist effektiv (>= 2x)
    double typeOnlyEff = getPokemonTypeEffectiveness(r.pokemon, defenderTypes);
    r.typeOnlyEffectiveness = typeOnlyEff;
    if (typeOnlyEff >= 2.0) pts += 1;

    // +1: Hat eine gelernte, schadenverursachende Attacke des effektiven Typs
    r.bestMoveId = getBestDamagingMoveId(r.pokemon, defenderTypes);
    if (r.bestMoveId != null) pts += 1;

    // +1: Höchste verfügbare Effektivität aller Pokemon
    if (r.typeEffectiveness >= finalMaxEff) pts += 1;

    // +1: Level im Top-Bereich (max - 5)
    r.isTopLevel = r.level >= finalMaxLevel - 5;
    if (r.isTopLevel) pts += 1;

    r.points = pts;

    if (pts >= 4) r.color = "\u00a7a";
    else if (pts >= 3) r.color = "\u00a7e";
    else if (pts >= 2) r.color = "\u00a7c";
    else r.color = "\u00a78";
}

        Collections.sort(results);
        return results;
    }
}