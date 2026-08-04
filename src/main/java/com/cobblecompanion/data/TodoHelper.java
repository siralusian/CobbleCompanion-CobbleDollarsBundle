package com.cobblecompanion.data;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionController;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TodoHelper {

    /** Alternative zu echtem Tausch für Tausch-Entwicklungen (LinkCableItem wendet Tausch-
     * Evolutionen direkt aufs Pokemon an, siehe Kommentar in getTodoEntries()). */
    public static final String LINK_CABLE_PATH = "link_cable";

    /**
     * Dedup-Schlüssel für getTodoEntries(): Zielspezies + Zielaspekte. Reine Zielspezies reicht
     * NICHT als Schlüssel, weil z.B. Hokumil/Milcery gleichzeitig zu mehreren unterschiedlichen
     * Formen derselben Zielspezies bereit sein kann (ein Sweet kann laut Cobblemon mehrere
     * gültige Deko/Creme-Kombinationen gleichzeitig freischalten) - jede davon braucht eine
     * eigene Zeile, sonst verschwindet eine gültige Option aus der Liste.
     */
    private record TargetKey(ResourceLocation toId, String aspects) {}

    /**
     * Arten (Ausgangsspezies, lowercase Pfad), bei denen dieselbe Zielspezies gleichzeitig zu
     * MEHREREN unterschiedlichen Formen bereit sein kann und deshalb als separate Zeilen
     * angezeigt werden sollen (siehe TargetKey). Bei allen anderen Arten werden Zielaspekte
     * ignoriert (Schlüssel nur nach Zielspezies, wie vor dem Hokumil-Fix) - sonst tauchen z.B.
     * bei Pikachu fälschlich zwei Zeilen auf ("Raichu" + "Raichu Alola", beide durch denselben
     * Donnerstein, aber durch eine Biom-Anforderung eigentlich exklusiv - diese Anforderung
     * prüfen wir im Hinweis-Zweig bewusst nicht mit). Neue Ausnahmen einfach hier ergänzen.
     */
    private static final Set<String> MULTI_FORM_SPECIES = Set.of("milcery");

    /** Öffentlicher Zugriff auf MULTI_FORM_SPECIES für andere Server-Helfer (z.B. DexCompletionHelper). */
    public static boolean allowsMultiForm(String speciesPathLower) {
        return MULTI_FORM_SPECIES.contains(speciesPathLower);
    }

    /**
     * Strukturierte ToDo-Einträge für den ToDo-Tab im GUI (Slot-Layout mit 3D-Modell statt
     * reinem Text). Nutzt dieselbe Evolution-Scan-Logik wie getTodoSummary(), baut daraus
     * aber Pipe-getrennte Zeilen mit den Rohdaten, die der Client zum Rendern (und für den
     * Entwickeln-Button an EvolvePokemonRequestPacket) braucht:
     * pokemonUuid|fromSpeciesId|fromLevel|fromAspects(,-getrennt)|toSpeciesId|toAspects(,-getrennt)|canEvolveNow|itemId(oder leer)|needsPokedex|needsLivingDex|isParty|pcBox|pcBoxName|nickname(oder leer)|caughtBallId(oder leer)
     *
     * needsPokedex = Ziel-Spezies noch nie gefangen; needsLivingDex = Ziel-Spezies aktuell
     * nicht im Besitz. isParty/pcBox/pcBoxName erlauben dem Client die Party/PC-Trennung samt
     * Box-Unterüberschriften (pcBox = -1, pcBoxName = "" bei Party-Pokemon). nickname/
     * caughtBallId gehören zum AUSGANGS-Pokemon (fromSpeciesId) - fürs GUI: Spitzname neben dem
     * Namen, Fangball-Icon oben rechts am Slot. Damit kann der Client die Liste nach dem
     * Setting "Show evolutions" filtern. Keine Deckelung mehr (die Liste ist im GUI scrollbar).
     */
    public static List<String> getTodoEntries(ServerPlayer player) {
        List<String> entries = new ArrayList<>();
        List<String> seen = new ArrayList<>();

        List<Pokemon> party = PlayerDataHelper.getParty(player);
        List<Pokemon> pcList = PlayerDataHelper.getPC(player);
        java.util.Set<java.util.UUID> partyUuids = new HashSet<>();
        for (Pokemon p : party) partyUuids.add(p.getUuid());
        java.util.Map<java.util.UUID, Integer> pcBoxes = PlayerDataHelper.getPCBoxNumbers(player);
        java.util.Map<Integer, String> pcBoxNames = PlayerDataHelper.getPCBoxNames(player);

        List<Pokemon> owned = new ArrayList<>(party.size() + pcList.size());
        owned.addAll(party);
        owned.addAll(pcList);

        Set<String> ownedSpeciesLower = new HashSet<>();
        for (Pokemon p : owned) ownedSpeciesLower.add(p.getSpecies().getName().toLowerCase());

        PokedexManager pokedex = null;
        try {
            pokedex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player.getUUID());
        } catch (Exception ignored) {
            // Pokédex nicht ladbar -> Ziel konservativ als "noch nicht gefangen" behandeln
        }

        for (Pokemon pokemon : owned) {
            String uuid = pokemon.getUuid().toString();
            ResourceLocation fromId = pokemon.getSpecies().getResourceIdentifier();
            int level = pokemon.getLevel();
            String aspects = String.join(",", pokemon.getAspects());
            boolean isParty = partyUuids.contains(pokemon.getUuid());
            int pcBox = isParty ? -1 : pcBoxes.getOrDefault(pokemon.getUuid(), -1);
            // Pipe-Zeichen im Box-Namen wären ein Parsing-Problem - defensiv rausfiltern.
            String pcBoxName = isParty ? "" : pcBoxNames.getOrDefault(pcBox, "").replace("|", "");
            String nickname = pokemon.getNickname() != null ? pokemon.getNickname().getString().replace("|", "") : "";
            String caughtBallId = pokemon.getCaughtBall() != null ? pokemon.getCaughtBall().getName().toString() : "";
            boolean allowMultiForm = MULTI_FORM_SPECIES.contains(fromId.getPath().toLowerCase());

            // Manche Pokemon haben MEHRERE Evolution-Objekte, die auf dasselbe Zielspezies
            // führen (z.B. verschiedene Auslöser-Varianten derselben Item-Entwicklung) - ohne
            // Zusammenfassung nach Zielspezies entstanden dadurch zwei widersprüchliche Zeilen
            // für dasselbe Pokemon (einmal "kann jetzt entwickeln", einmal "braucht noch den
            // Stein"). Deshalb hier pro Zielspezies+Zielaspekte (siehe TargetKey) auf GENAU eine
            // Zeile verdichten: "kann jetzt entwickeln" gewinnt immer, sonst der Item-Hinweis,
            // sonst gar nichts. Die Aspekte gehören mit zum Schlüssel, weil manche Pokemon (z.B.
            // Hokumil/Milcery -> Pati-Kuchen-Reihe) gleichzeitig zu MEHREREN unterschiedlichen
            // Formen derselben Zielspezies bereit sein können (z.B. ein Item löst 2 verschiedene
            // Creme-Varianten gleichzeitig aus) - diese müssen als eigene Zeilen erscheinen,
            // sonst würde eine der gültigen Formen verschwinden.
            java.util.Map<TargetKey, Boolean> canEvolveByTarget = new java.util.LinkedHashMap<>();
            java.util.Map<TargetKey, ResourceLocation> hintItemByTarget = new java.util.LinkedHashMap<>();

            for (Evolution evo : pokemon.getEvolutions()) {
                String resultName = evo.getResult().getSpecies();
                ResourceLocation toId = resolveSpeciesId(resultName);
                if (toId == null) continue;

                // WICHTIG (per Bytecode-Analyse verifiziert, u.a. Evolution.evolve()-Default-
                // Implementierung): Bei "optionalen" Evolutionen (Steine UND Tausch-Evolutionen,
                // z.B. per Linkkabel statt echtem Tausch) prüft evo.test(pokemon) NUR die
                // allgemeinen Anforderungen (Level/Freundschaft) - das "wurde der Stein/das
                // Linkkabel schon angewendet"-Flag landet stattdessen in
                // Pokemon.getEvolutionProxy().server() (ein persistentes Set<Evolution> aller
                // gerade "bereiten" Evolutionen, siehe ServerEvolutionController.class - überlebt
                // sogar einen Server-Neustart, genau wie berichtet). Tausch-Evolutionen wurden
                // früher hier komplett übersprungen (isTradeEvolution()-Check) - das war falsch,
                // sobald der Spieler ein Linkkabel angewendet hat landen sie GENAUSO in diesem
                // Set wie Stein-Evolutionen (siehe LinkCableItem.processInteraction(), das
                // TradeEvolution.evolve(pokemon) aufruft).
                boolean isTrade = isTradeEvolution(evo);
                String context = getRequiredContext(evo);
                boolean needsContextItem = context != null && !context.isEmpty();
                boolean checkViaQueue = needsContextItem || isTrade;
                // Hokumil/Milcery & Co: keine eigene Item-Entwicklung, sondern normale Level-Up-
                // Evolution mit HeldItemRequirement in requirements[] (siehe getHeldItemRequirementContext()).
                String heldItemContext = (!needsContextItem && !isTrade) ? getHeldItemRequirementContext(evo) : null;
                boolean canEvolveNow;
                if (checkViaQueue) {
                    canEvolveNow = isEvolutionQueued(pokemon, evo);
                    // Manche Item-Entwicklungen (z.B. Apfel-Reihe) funktionieren NICHT über
                    // "einmal anwenden -> verbrauchen -> queuen" wie Steine/Linkkabel, sondern
                    // klassisch über dauerhaft GEHALTENE Items (Pokemon trägt den Apfel bis zum
                    // Entwickeln) - dafür zusätzlich direkt prüfen, ob das Pokemon den nötigen
                    // Gegenstand gerade hält.
                    if (!canEvolveNow && needsContextItem && evo.test(pokemon) && pokemonHoldsRequiredItem(pokemon, context)) {
                        canEvolveNow = true;
                    }
                } else if (heldItemContext != null) {
                    // Hokumil/Milcery & Co: das Süßigkeits-Item wird beim Geben SOFORT verbraucht
                    // (verhält sich also genau wie Stein-/Tausch-Entwicklungen), sobald die
                    // übrigen Anforderungen (z.B. Tageszeit) zu diesem Zeitpunkt erfüllt waren -
                    // danach steckt das "bereit"-Flag ebenfalls im EvolutionProxy-Set, nicht mehr
                    // im gehaltenen Item. evo.test() bleibt zusätzlich gültig, solange das Item
                    // noch tatsächlich gehalten wird (z.B. direkt nach dem Geben, bevor verbraucht).
                    canEvolveNow = isEvolutionQueued(pokemon, evo) || evo.test(pokemon);
                } else {
                    canEvolveNow = evo.test(pokemon);
                }

                TargetKey key = new TargetKey(toId, allowMultiForm ? getResultAspectsString(evo) : "");

                if (canEvolveNow) {
                    canEvolveByTarget.put(key, true);
                    continue;
                }
                canEvolveByTarget.putIfAbsent(key, false);

                if (needsContextItem && playerHasItemByPath(player, context)) {
                    ResourceLocation itemId = resolveItemId(context);
                    if (itemId != null) hintItemByTarget.putIfAbsent(key, itemId);
                } else if (isTrade && playerHasItemByPath(player, LINK_CABLE_PATH)) {
                    ResourceLocation linkCableId = resolveItemId(LINK_CABLE_PATH);
                    if (linkCableId != null) hintItemByTarget.putIfAbsent(key, linkCableId);
                } else if (heldItemContext != null
                        && (playerHasItemByPath(player, heldItemContext) || pokemonHoldsRequiredItem(pokemon, heldItemContext))) {
                    ResourceLocation itemId = resolveItemId(heldItemContext);
                    if (itemId != null) hintItemByTarget.putIfAbsent(key, itemId);
                }
            }

            for (java.util.Map.Entry<TargetKey, Boolean> target : canEvolveByTarget.entrySet()) {
                TargetKey key = target.getKey();
                ResourceLocation toId = key.toId();
                String toAspects = key.aspects();
                boolean needsPokedex = targetNeedsPokedex(pokedex, toId);
                boolean needsLivingDex = !ownedSpeciesLower.contains(toId.getPath().toLowerCase());
                String suffix = "|" + needsPokedex + "|" + needsLivingDex + "|" + isParty + "|" + pcBox + "|" + pcBoxName
                    + "|" + nickname + "|" + caughtBallId;

                String line;
                if (target.getValue()) {
                    line = uuid + "|" + fromId + "|" + level + "|" + aspects + "|" + toId + "|" + toAspects + "|true|" + suffix;
                } else {
                    ResourceLocation itemId = hintItemByTarget.get(key);
                    if (itemId == null) continue; // weder evolvierbar noch Item vorhanden -> gar nicht anzeigen
                    line = uuid + "|" + fromId + "|" + level + "|" + aspects + "|" + toId + "|" + toAspects + "|false|" + itemId + suffix;
                }

                if (!seen.contains(line)) {
                    seen.add(line);
                    entries.add(line);
                }
            }
        }

        return entries;
    }

    /** true, wenn die Ziel-Spezies laut Pokédex noch nie gefangen wurde (oder Pokédex fehlt). */
    private static boolean targetNeedsPokedex(PokedexManager pokedex, ResourceLocation toId) {
        if (pokedex == null) return true;
        try {
            return pokedex.getKnowledgeForSpecies(toId) != PokedexEntryProgress.CAUGHT;
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * Löst den Entwickeln-Button im ToDo-Tab aus: sucht das Pokemon (Party+PC) per UUID, sucht
     * darauf die (nicht-Tausch-)Evolution, deren Ergebnis toSpeciesId entspricht, prüft die
     * Bedingungen serverseitig erneut (evo.test) und führt sie bei Erfolg sofort aus
     * (Evolution.forceEvolve() - überspringt bewusst Cobblemons "optional"-Bestätigungsdialog,
     * da der Spieler die Entwicklung hier schon aktiv per Klick bestätigt hat).
     * sendOut = true ruft das Pokémon vorher aus (Setting "Send out before evolving"), damit
     * die Entwicklungsanimation in der Welt sichtbar ist statt nur im Menü stattzufinden -
     * aber NUR für Party-Pokemon. PC-Pokemon dürfen zwar weiterhin entwickelt werden, aber
     * nicht "in die Welt gerufen" werden (kein Sinn, sie sind nicht beim Spieler dabei).
     * toAspects muss dieselben Zielaspekte tragen, die im ToDo-Tab angezeigt wurden (siehe
     * TargetKey in getTodoEntries()) - manche Pokemon (z.B. Hokumil/Milcery) können gleichzeitig
     * zu MEHREREN unterschiedlichen Formen derselben Zielspezies bereit sein; ohne diesen
     * Abgleich würde immer nur die zufällig erste passende Evolution ausgeführt, nicht
     * zwingend die, die der Spieler in der angeklickten Zeile gesehen hat.
     */
    public static boolean tryEvolve(ServerPlayer player, java.util.UUID pokemonUuid, ResourceLocation toSpeciesId, String toAspects, boolean sendOut) {
        List<Pokemon> party = PlayerDataHelper.getParty(player);
        boolean isPartyPokemon = party.stream().anyMatch(p -> p.getUuid().equals(pokemonUuid));
        Pokemon pokemon = isPartyPokemon
            ? party.stream().filter(p -> p.getUuid().equals(pokemonUuid)).findFirst().orElse(null)
            : PlayerDataHelper.getAllPokemon(player).stream().filter(p -> p.getUuid().equals(pokemonUuid)).findFirst().orElse(null);
        if (pokemon == null) return false;
        boolean allowMultiForm = MULTI_FORM_SPECIES.contains(pokemon.getSpecies().getResourceIdentifier().getPath().toLowerCase());

        for (Evolution evo : pokemon.getEvolutions()) {
            ResourceLocation toId = resolveSpeciesId(evo.getResult().getSpecies());
            if (toId == null || !toId.equals(toSpeciesId)) continue;
            // Aspekte nur bei MULTI_FORM_SPECIES abgleichen (siehe getTodoEntries()/TargetKey) -
            // bei allen anderen Arten hat der Client sowieso "" geschickt (Aspekte dort ignoriert),
            // ein strikter Abgleich würde dort z.B. Pikachus Alola-Raichu-Variante nie matchen.
            if (allowMultiForm && toAspects != null && !normalizeAspects(toAspects).equals(getResultAspectsString(evo))) continue;
            // Muss dieselbe Prüfung wie getTodoEntries() nutzen (siehe dortiger Kommentar) -
            // sonst kann ein Pokemon über den ToDo-Tab entwickelt werden, dessen Stein-/Tausch-
            // Entwicklung in Cobblemons eigenem UI noch gar nicht "angewendet" wurde.
            boolean isTrade = isTradeEvolution(evo);
            String context = getRequiredContext(evo);
            boolean needsContextItem = context != null && !context.isEmpty();
            boolean checkViaQueue = needsContextItem || isTrade;
            String heldItemContext = (!needsContextItem && !isTrade) ? getHeldItemRequirementContext(evo) : null;
            boolean canEvolveNow;
            if (checkViaQueue) {
                canEvolveNow = isEvolutionQueued(pokemon, evo);
                if (!canEvolveNow && needsContextItem && evo.test(pokemon) && pokemonHoldsRequiredItem(pokemon, context)) {
                    canEvolveNow = true;
                }
            } else if (heldItemContext != null) {
                canEvolveNow = isEvolutionQueued(pokemon, evo) || evo.test(pokemon);
            } else {
                canEvolveNow = evo.test(pokemon);
            }
            if (!canEvolveNow) continue;

            if (sendOut && isPartyPokemon) trySendOut(player, pokemon);
            evo.forceEvolve(pokemon);
            return true;
        }
        return false;
    }

    /**
     * true, wenn diese Evolution für dieses Pokemon gerade "bereit" ist - bei Stein-/Tausch-
     * (Linkkabel-)Entwicklungen heißt das: der Spieler hat den Stein/das Kabel bereits in
     * Cobblemons eigenem UI auf das Pokemon angewendet (Item wird dabei sofort verbraucht).
     * Dieser Zustand steckt in Pokemon.getEvolutionProxy().server(), einem persistenten (NBT-
     * gesicherten, überlebt Logout/Neustart) Set<Evolution> aller aktuell ausgelösten
     * Evolutionen (siehe ServerEvolutionController.class). Manche Item-Entwicklungen (z.B.
     * Apfel-Reihe) laufen NICHT über diesen Queue-Mechanismus, sondern über dauerhaft
     * GEHALTENE Items - dafür siehe pokemonHoldsRequiredItem() als zweiten, alternativen Check.
     */
    private static boolean isEvolutionQueued(Pokemon pokemon, Evolution evo) {
        try {
            EvolutionController<?, ?> serverController = pokemon.getEvolutionProxy().server();
            return serverController.contains(evo);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * true, wenn das Pokemon aktuell den für die Entwicklung nötigen Gegenstand hält (z.B.
     * Apfel-Reihe: Item wird dauerhaft gehalten statt sofort verbraucht/gequeued, siehe
     * Kommentar bei isEvolutionQueued()). pokemon.heldItem() ist Cobblemons echter Kotlin-
     * Getter (per Bytecode-Analyse verifiziert).
     */
    private static boolean pokemonHoldsRequiredItem(Pokemon pokemon, String context) {
        ItemStack stack = pokemon.heldItem();
        if (stack == null || stack.isEmpty()) return false;
        String stackPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase().trim();
        String search = cleanItemName(context).toLowerCase().replace(" ", "_");
        return stackPath.equals(search);
    }

    /**
     * Manche Evolutionen (z.B. Hokumil/Milcery -> Pati-Kuchen-Reihe) sind gar keine eigene
     * Item-Entwicklung ("item_interact"/Tausch), sondern normale Level-Up-Evolutionen mit einer
     * HeldItemRequirement in requirements[] (zusätzlich meist noch an eine Tageszeit gekoppelt).
     * getRequiredContext() liefert hier null, weshalb solche Evolutionen bisher nie einen
     * Item-Hinweis bekamen. Sucht die HeldItemRequirement per Reflection und liefert deren
     * benötigtes Item (roher requiredContext-String, wie von getRequiredContext()), sonst null.
     */
    /**
     * Aspekte des Evolutionsergebnisses (z.B. bei Hokumil/Milcery -> Pati-Kuchen: Deko/Creme-
     * Kombination), damit die ToDo-Vorschau die tatsächlich resultierende Form zeigt statt
     * immer der Standardform. updateAspects() stellt sicher, dass auch aus den custom
     * properties (z.B. "decoration=strawberry") abgeleitete Aspekte enthalten sind.
     */
    public static String getResultAspectsString(Evolution evo) {
        try {
            PokemonProperties result = evo.getResult();
            if (result == null) return "";
            try {
                result.updateAspects();
            } catch (Exception ignored) {}
            Set<String> aspects = result.getAspects();
            if (aspects == null || aspects.isEmpty()) return "";
            return normalizeAspects(String.join(",", aspects));
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Aspekte sortiert+kommagetrennt zurückgeben, damit der String unabhängig von der
     * (undefinierten) Set-Iterationsreihenfolge immer identisch ist. Nötig, weil toAspects auf
     * dem Client durch einen Set&lt;String&gt; läuft (siehe ClientTodoHelper.TodoEntry) - ohne
     * diese Normalisierung würde der beim Entwickeln-Klick zurückgeschickte String nicht mehr
     * exakt zum serverseitig in getTodoEntries() erzeugten String passen und tryEvolve() würde
     * die passende Evolution nie finden (genau der Bug, den Hokumil zeigte: Vorschau korrekt,
     * Entwickeln-Button tut nichts).
     */
    private static String normalizeAspects(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        List<String> parts = new ArrayList<>(java.util.Arrays.asList(raw.split(",")));
        parts.removeIf(String::isEmpty);
        java.util.Collections.sort(parts);
        return String.join(",", parts);
    }

    public static String getHeldItemRequirementContext(Evolution evo) {
        try {
            for (Object req : evo.getRequirements()) {
                if (!req.getClass().getSimpleName().toLowerCase().contains("helditem")) continue;
                try {
                    Method m = req.getClass().getMethod("getItemCondition");
                    Object val = m.invoke(req);
                    if (val != null) return extractNameFromObject(val);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Ruft das Pokémon aus, falls es nicht schon in der Welt ist. Bewusst per Reflection, weil
     * Cobblemons sendOut(ServerLevel, Vec3, IllusionEffect, Function1) Kotlin-Default-Parameter
     * und uneindeutige Nullability hat - ein falscher Compile-Typ würde die Mod brechen. Hier
     * best-effort: passende sendOut-Methode suchen, Argumente nach Parametertyp füllen, alles
     * abfangen. Schlägt es fehl, wird trotzdem normal entwickelt (nur Warnung im Log).
     */
    private static void trySendOut(ServerPlayer player, Pokemon pokemon) {
        try {
            // Schon draußen? Dann nichts tun.
            try {
                Object entity = pokemon.getClass().getMethod("getEntity").invoke(pokemon);
                if (entity != null) return;
            } catch (Exception ignored) {}

            // 7 Blöcke vor dem Spieler in Blickrichtung (horizontal, Pitch ignoriert - sonst
            // würde das Pokemon beim Hoch-/Runterschauen in der Luft oder im Boden landen).
            double yawRad = Math.toRadians(player.getYRot());
            double dx = -Math.sin(yawRad);
            double dz = Math.cos(yawRad);
            Vec3 pos = new Vec3(player.getX() + dx * 7.0, player.getY(), player.getZ() + dz * 7.0);
            for (Method m : pokemon.getClass().getMethods()) {
                if (!m.getName().equals("sendOut")) continue;
                Class<?>[] params = m.getParameterTypes();
                // Nur die "echte" Überladung ohne Kotlin-Default-Maske (kein trailing int + Object).
                if (params.length == 0) continue;
                Object[] args = new Object[params.length];
                boolean usable = true;
                for (int i = 0; i < params.length; i++) {
                    Class<?> pt = params[i];
                    if (pt.isAssignableFrom(net.minecraft.server.level.ServerLevel.class)) {
                        args[i] = player.serverLevel();
                    } else if (pt.isAssignableFrom(Vec3.class)) {
                        args[i] = pos;
                    } else if (kotlin.jvm.functions.Function1.class.isAssignableFrom(pt)) {
                        args[i] = (kotlin.jvm.functions.Function1<Object, kotlin.Unit>) o -> kotlin.Unit.INSTANCE;
                    } else if (!pt.isPrimitive()) {
                        args[i] = null; // IllusionEffect o.ä. -> null
                    } else {
                        usable = false; // primitive (Default-Maske) -> diese Überladung überspringen
                        break;
                    }
                }
                if (!usable) continue;
                m.invoke(pokemon, args);
                return;
            }
        } catch (Throwable t) {
            com.cobblecompanion.CobbleCompanion.LOGGER.warn("[CC] sendOut before evolve failed", t);
        }
    }

    /** Baut aus einem rohen Spezies-Namen (z.B. "charmeleon") eine cobblemon:-ResourceLocation. */
    public static ResourceLocation resolveSpeciesId(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;
        String s = rawName.trim().toLowerCase();
        try {
            return s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath("cobblemon", s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Baut aus dem rohen Evolution-Kontext (Item-Anforderung) eine gültige Item-ResourceLocation, falls registriert. */
    public static ResourceLocation resolveItemId(String rawContext) {
        if (rawContext == null || rawContext.isBlank()) return null;
        String s = rawContext.trim().toLowerCase().replace(" ", "_");
        try {
            ResourceLocation id = s.contains(":") ? ResourceLocation.parse(s) : ResourceLocation.fromNamespaceAndPath("cobblemon", s);
            return BuiltInRegistries.ITEM.containsKey(id) ? id : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getTodoSummary(ServerPlayer player) {
        List<Pokemon> allPokemon = PlayerDataHelper.getAllPokemon(player);
        if (allPokemon.isEmpty()) return "\u00a77You don't have any Pokemon yet!";

        List<String> hints = new ArrayList<>();

        for (Pokemon pokemon : allPokemon) {
            String name = pokemon.getSpecies().getName();
            int level = pokemon.getLevel();

            for (Evolution evo : pokemon.getEvolutions()) {
                String resultName = evo.getResult().getSpecies();
                boolean isTrade = isTradeEvolution(evo);
                String context = getRequiredContext(evo);
                boolean needsContextItem = context != null && !context.isEmpty();

                if (needsContextItem || isTrade) {
                    // Siehe ausf\u00fchrlicher Kommentar in getTodoEntries(): "kann jetzt entwickeln"
                    // bei Stein-/Tausch-Entwicklungen (Linkkabel statt echtem Tausch) richtet sich
                    // danach, ob sie in Cobblemons eigenem UI bereits "angewendet" wurde
                    // (EvolutionProxy-Set), nicht nach evo.test()/Item-Besitz allein.
                    boolean canEvolveNow = isEvolutionQueued(pokemon, evo)
                        || (needsContextItem && evo.test(pokemon) && pokemonHoldsRequiredItem(pokemon, context));
                    if (canEvolveNow) {
                        addHint(hints, "\u00a7a\u2713 \u00a7e" + name + " \u00a77(Lv." + level + ") \u00a77\u2192 \u00a7f" + resultName + " \u00a7acan evolve now!");
                    } else if (needsContextItem && playerHasItemByPath(player, context)) {
                        String itemName = cleanItemName(context);
                        addHint(hints, "\u00a7e\u25ba \u00a7e" + name + " \u00a77(Lv." + level + ") \u00a77\u2192 \u00a7f" + resultName +
                            " \u00a77\u2013 use the \u00a7e" + itemName + " \u00a77on it to evolve");
                    } else if (isTrade && playerHasItemByPath(player, LINK_CABLE_PATH)) {
                        addHint(hints, "\u00a7e\u25ba \u00a7e" + name + " \u00a77(Lv." + level + ") \u00a77\u2192 \u00a7f" + resultName +
                            " \u00a77\u2013 use a \u00a7eLink Cable \u00a77on it to evolve (or trade it)");
                    }
                    continue;
                }

                // Hokumil/Milcery & Co: Level-Up-Evolution mit HeldItemRequirement statt eigener
                // Item-Entwicklung. Das Item wird beim Geben aber sofort verbraucht (genau wie
                // bei Steinen/Linkkabel), das "bereit"-Flag landet danach ebenfalls im
                // EvolutionProxy-Set - deshalb zus\u00e4tzlich isEvolutionQueued() pr\u00fcfen.
                String heldItemContext = getHeldItemRequirementContext(evo);
                boolean canEvolveNow = (heldItemContext != null && isEvolutionQueued(pokemon, evo)) || evo.test(pokemon);
                if (canEvolveNow) {
                    addHint(hints, "\u00a7a\u2713 \u00a7e" + name + " \u00a77(Lv." + level + ") \u00a77\u2192 \u00a7f" + resultName + " \u00a7acan evolve now!");
                } else if (heldItemContext != null
                        && (playerHasItemByPath(player, heldItemContext) || pokemonHoldsRequiredItem(pokemon, heldItemContext))) {
                    String itemName = cleanItemName(heldItemContext);
                    addHint(hints, "\u00a7e\u25ba \u00a7e" + name + " \u00a77(Lv." + level + ") \u00a77\u2192 \u00a7f" + resultName +
                        " \u00a77\u2013 have it hold the \u00a7e" + itemName + " \u00a77to evolve");
                }
            }
        }

        if (hints.isEmpty()) {
            return "\u00a77No actions available right now.\n" +
                   "\u00a77Evolutions show up here when:\n" +
                   "\u00a78\u2022 The required level is reached\n" +
                   "\u00a78\u2022 An evolution item is held by the Pokemon\n" +
                   "\u00a78\u2022 The friendship level is reached\n" +
                   "\u00a77Tip: \u00a7e/companion dex <pokemon> \u00a77for details.";
        }

        StringBuilder sb = new StringBuilder("\u00a76=== What can you do? ===\n");
        int max = Math.min(hints.size(), 10);
        for (int i = 0; i < max; i++) sb.append(hints.get(i)).append("\n");
        if (hints.size() > 10) sb.append("\u00a77... and ").append(hints.size() - 10).append(" more.");
        return sb.toString().trim();
    }

    private static void addHint(List<String> hints, String hint) {
        if (!hints.contains(hint)) hints.add(hint);
    }

    private static boolean playerHasItemByPath(ServerPlayer player, String itemPath) {
        if (itemPath == null || itemPath.isEmpty()) return false;
        String search = cleanItemName(itemPath).toLowerCase().replace(" ", "_");

        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            try {
                String stackPath = BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .getPath().toLowerCase().trim();
                if (stackPath.equals(search)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static String getRequiredContext(Evolution evo) {
        try {
            Method m = evo.getClass().getMethod("getRequiredContext");
            Object val = m.invoke(evo);
            if (val == null) return null;
            // Skip trade evolutions - PokemonProperties isn't an item
            if (val.getClass().getSimpleName().toLowerCase().contains("pokemonproperties")) return null;
            return extractNameFromObject(val);
        } catch (Exception ignored) {}
        try {
            Field f = evo.getClass().getDeclaredField("requiredContext");
            f.setAccessible(true);
            Object val = f.get(evo);
            if (val == null) return null;
            if (val.getClass().getSimpleName().toLowerCase().contains("pokemonproperties")) return null;
            return extractNameFromObject(val);
        } catch (Exception ignored) {}
        return null;
    }

    private static String extractNameFromObject(Object obj) {
        String str = obj.toString();
        if (str.contains("]=")) {
            int eqIndex = str.indexOf("]=");
            String after = str.substring(eqIndex + 2);
            int end = after.length();
            for (int i = 0; i < after.length(); i++) {
                char c = after.charAt(i);
                if (c == '}' || c == ']' || c == ',') { end = i; break; }
            }
            return after.substring(0, end).trim();
        }
        if (str.contains("ResourceKey[item / ")) {
            int start = str.indexOf("ResourceKey[item / ") + "ResourceKey[item / ".length();
            int end = str.indexOf("]", start);
            if (end > start) return str.substring(start, end).trim();
        }
        return str;
    }

    public static String cleanItemName(String raw) {
        return raw.replace("cobblemon:", "")
                  .replace("minecraft:", "")
                  .replace("}", "")
                  .replace("]", "")
                  .replace("_", " ")
                  .trim();
    }

    public static int extractLevel(Object req) {
        String[] fieldNames = {"minLevel", "level", "requiredLevel"};
        for (String fieldName : fieldNames) {
            try {
                Field f = req.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(req);
                if (val instanceof Integer) return (Integer) val;
            } catch (Exception ignored) {}
        }
        try {
            Method m = req.getClass().getMethod("getMinLevel");
            Object val = m.invoke(req);
            if (val instanceof Integer) return (Integer) val;
        } catch (Exception ignored) {}
        for (Field f : req.getClass().getDeclaredFields()) {
            if (f.getType() == int.class || f.getType() == Integer.class) {
                f.setAccessible(true);
                try {
                    int val = f.getInt(req);
                    if (val > 0 && val <= 100) return val;
                } catch (Exception ignored) {}
            }
        }
        return 100;
    }

    public static String getRequiredItemName(Evolution evo) {
        String context = getRequiredContext(evo);
        if (context != null && !context.isEmpty()) return cleanItemName(context);
        for (Object req : evo.getRequirements()) {
            String reqClass = req.getClass().getSimpleName().toLowerCase();
            if (reqClass.contains("item") || reqClass.contains("stone") || reqClass.contains("held")) {
                return cleanItemName(extractNameFromObject(req));
            }
        }
        return "Item";
    }

    /**
     * Describes an evolution for the Dex command, with readable conditions.
     */
    public static String describeEvolutionForDex(Evolution evo, String resultName) {
        String context = getRequiredContext(evo);
        StringBuilder line = new StringBuilder("\u00a7f").append(resultName);

        boolean isTrade = isTradeEvolution(evo);

        if (context != null && !context.isEmpty()) {
            String itemName = cleanItemName(context);
            line.append(" \u00a77- requires \u00a7e").append(itemName);
            return line.toString();
        }

        List<String> conditions = new ArrayList<>();

        if (isTrade) {
            conditions.add("\u00a77trade");
        }

        for (Object req : evo.getRequirements()) {
            String reqClass = req.getClass().getSimpleName().toLowerCase();
            if (reqClass.contains("level")) {
                conditions.add("Level \u00a7e" + extractLevel(req));
            } else if (reqClass.contains("friendship")) {
                conditions.add("\u00a77friendship \u00a7e160+");
            } else if (reqClass.contains("trade")) {
                // already handled above via isTrade
            } else if (reqClass.contains("time")) {
                conditions.add("\u00a77specific time of day");
            } else if (reqClass.contains("biome")) {
                conditions.add("\u00a77specific biome");
            } else if (reqClass.contains("item") || reqClass.contains("stone") || reqClass.contains("held")) {
                conditions.add("\u00a7e" + cleanItemName(extractNameFromObject(req)));
            } else {
                String simplified = req.getClass().getSimpleName().replace("Requirement", "");
                if (!simplified.isEmpty()) conditions.add("\u00a77" + simplified);
            }
        }

        if (!conditions.isEmpty()) {
            line.append(" \u00a77- ").append(String.join("\u00a77, ", conditions));
        }

        return line.toString();
    }

    public static boolean isTradeEvolution(Evolution evo) {
        try {
            Method m = evo.getClass().getMethod("getRequiredContext");
            Object val = m.invoke(evo);
            if (val != null && val.getClass().getSimpleName().toLowerCase().contains("pokemonproperties")) {
                return true;
            }
        } catch (Exception ignored) {}
        for (Object req : evo.getRequirements()) {
            if (req.getClass().getSimpleName().toLowerCase().contains("trade")) return true;
        }
        return false;
    }
}