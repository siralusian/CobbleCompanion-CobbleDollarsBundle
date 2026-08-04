package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Vorgabe (Gamemodes-Einstellungskategorie): AdminOp kann pro Dimension (auch Custom-
 * Dimensionen anderer Mods) einen festen Spielmodus vorgeben - betritt ein Spieler diese Dimension,
 * wird er automatisch in diesen Modus versetzt, beim Verlassen wieder in seinen vorherigen. Bei
 * Creative wird NICHT der normale Vanilla-Creative-Modus vergeben, sondern derselbe "beschnittene"
 * per {@link CreativeTimeManager}/{@link CreativeRestrictionHandler} - siehe isForcedCreative(),
 * das dort verodert wird. Persistiert als Gson-JSON im Weltordner, gleiches Muster wie
 * {@link CreativeTimeManager}.
 *
 * Dimensions-IDs werden als reiner String (z.B. "minecraft:the_nether" oder
 * "meine_mod:testwelt") gespeichert - so funktioniert es auch für Dimensionen, die beim Setzen der
 * Regel gerade nicht geladen sind (z.B. Custom-Dimension eines aktuell deaktivierten Datapacks).
 */
public class DimensionGamemodeManager {

    private static class Data {
        /** Dimension-ID (z.B. "minecraft:the_nether") -> erzwungener Spielmodus (Name aus GameType). */
        Map<String, String> rules = new HashMap<>();
        /** Spieler-UUID -> Spielmodus vor dem Betreten einer geregelten Dimension, für die Rückkehr beim Verlassen. */
        Map<String, String> previousGameType = new HashMap<>();
        /** Spieler-UUID -> Dimension-ID, für die previousGameType aktuell gilt (erkennt Wechsel zwischen zwei geregelten Dimensionen). */
        Map<String, String> activeDimension = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_dimension_gamemode.json");
        load();
    }

    public static void setRule(String dimensionId, GameType mode) {
        data.rules.put(dimensionId, mode.getName());
        save();
    }

    public static boolean removeRule(String dimensionId) {
        boolean removed = data.rules.remove(dimensionId) != null;
        if (removed) save();
        return removed;
    }

    public static GameType getRule(String dimensionId) {
        String name = data.rules.get(dimensionId);
        return name == null ? null : GameType.byName(name, null);
    }

    public static Map<String, String> getAllRules() {
        return data.rules;
    }

    /**
     * Aufrufer: Login + Dimensionswechsel (siehe CobbleCompanion). Prüft die Regel der Dimension,
     * in der sich player GERADE befindet, und setzt bzw. stellt den Spielmodus entsprechend.
     */
    public static void onDimensionEnter(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        String currentDimension = player.level().dimension().location().toString();
        GameType rule = getRule(currentDimension);

        if (rule == null) {
            // Keine Regel hier - falls player gerade noch aus einer geregelten Dimension "aktiv"
            // ist, wieder in den vorherigen Modus zurückversetzen.
            String previousName = data.previousGameType.remove(uuid);
            data.activeDimension.remove(uuid);
            if (previousName != null) {
                GameType previous = GameType.byName(previousName, GameType.SURVIVAL);
                if (player.gameMode.getGameModeForPlayer() != previous) player.setGameMode(previous);
                save();
            }
            return;
        }

        String activeFor = data.activeDimension.get(uuid);
        if (currentDimension.equals(activeFor)) {
            // Bereits für DIESE Dimension aktiv - Modus nur erzwingen, falls der Spieler ihn
            // zwischenzeitlich anderweitig verlassen hat (z.B. eigener Op-Status).
            if (player.gameMode.getGameModeForPlayer() != rule) player.setGameMode(rule);
            return;
        }

        // Neu betreten (auch beim direkten Wechsel zwischen zwei geregelten Dimensionen): nur
        // merken, falls noch KEIN vorheriger Modus für diesen Spieler gespeichert ist - sonst
        // würde ein Wechsel zwischen zwei geregelten Dimensionen den ursprünglichen echten Modus
        // überschreiben und beim endgültigen Verlassen ginge er verloren.
        if (!data.previousGameType.containsKey(uuid)) {
            data.previousGameType.put(uuid, player.gameMode.getGameModeForPlayer().getName());
        }
        data.activeDimension.put(uuid, currentDimension);
        if (player.gameMode.getGameModeForPlayer() != rule) player.setGameMode(rule);
        save();
    }

    /**
     * Nutzer-Fund (Live-Test): eine neu gesetzte/entfernte Regel wirkte sich NICHT sofort auf
     * Spieler aus, die sich bereits IN der betroffenen Dimension befanden - onDimensionEnter()
     * wird nämlich nur bei Login/Dimensionswechsel aufgerufen, keins von beidem passiert beim
     * bloßen Setzen der Regel selbst. Aufrufer (Command-Handler/Packet-Handler) müssen deshalb
     * nach JEDER Regeländerung diese Methode für die betroffene Dimension aufrufen - sie prüft für
     * alle aktuell online Spieler in genau dieser Dimension erneut (funktioniert unverändert für
     * sowohl "Regel neu gesetzt" als auch "Regel entfernt", da onDimensionEnter beide Fälle deckt).
     */
    public static void applyToOnlinePlayers(MinecraftServer server, String dimensionId) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().location().toString().equals(dimensionId)) {
                onDimensionEnter(p);
            }
        }
    }

    /** Nutzer-Vorgabe: Creative per Dimensionsregel ist dasselbe "beschnittene" Creative wie der Kauf - siehe CreativeRestrictionHandler. */
    public static boolean isForcedCreative(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        String activeFor = data.activeDimension.get(uuid);
        if (activeFor == null) return false;
        return activeFor.equals(player.level().dimension().location().toString()) && getRule(activeFor) == GameType.CREATIVE;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.rules == null) data.rules = new HashMap<>();
        if (data.previousGameType == null) data.previousGameType = new HashMap<>();
        if (data.activeDimension == null) data.activeDimension = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
