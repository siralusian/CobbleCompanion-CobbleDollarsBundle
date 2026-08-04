package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
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
 * Zeitlich befristeter Creative-Modus gegen Cobbledollars. Rein vanilla (kein Fremd-Mod-Import
 * nötig, Cobbledollars-Abbuchung übernimmt der Packet-Handler über CobbleDollarsBridge). Persistiert
 * als Gson-JSON im Weltordner, gleiches Muster wie {@link ServerRulesManager}.
 */
public class CreativeTimeManager {

    private static class Session {
        long expiryEpochMillis;
        String previousGameType;
    }

    private static class Data {
        long pricePerMinute = 100;
        // Nutzer-Vorgabe (Gamemodes-Einstellungskategorie): AdminOp kann den Kauf komplett
        // sperren, unabhängig vom Preis (vorher gab es dafür nur den Nebeneffekt-Bug, dass Preis=0
        // den Kauf durch einen fehlschlagenden 0-Cobbledollar-Abzug faktisch blockierte - siehe
        // CreativeTimePurchasePacket-Kommentar).
        // Boolean (nicht boolean): Gson füllt beim Laden über Reflection KEINE Feld-Initialisierer
        // (siehe Kommentar in load()) - ein primitives boolean würde bei bereits bestehenden
        // Welt-JSONs ohne dieses Feld stumm auf false (= gesperrt) statt true landen. Mit dem
        // nullbaren Wrapper lässt sich "fehlt im JSON" von "explizit false" unterscheiden.
        Boolean purchaseEnabled = true;
        Map<String, Session> sessions = new HashMap<>();
        /**
         * Nutzer-Vorgabe (Korrektur, 2x - ursprünglich zusätzlich pro Dimension eingeschränkt,
         * Dimensionsbezug auf Nutzer-Wunsch wieder entfernt): reine PRO-SPIELER Kaufberechtigung.
         * Spieler-UUID -> Modus ("WHITELIST" = darf kaufen, "BLACKLIST" = darf NICHT kaufen). Kein
         * Eintrag = keine Einschränkung (darf kaufen, wie bisher).
         */
        Map<String, String> playerPurchaseRules = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_creative_time.json");
        load();
    }

    public static long getPricePerMinute() { return data.pricePerMinute; }

    public static void setPricePerMinute(long price) {
        data.pricePerMinute = Math.max(0, price);
        save();
    }

    /** Einmalige Migration, siehe CentralItemPriceManager.scaleAllPricesBy10(). */
    public static void scalePricePerMinuteBy10() {
        data.pricePerMinute *= 10;
        save();
    }

    public static boolean isPurchaseEnabled() { return data.purchaseEnabled == null || data.purchaseEnabled; }

    public static void setPurchaseEnabled(boolean enabled) {
        data.purchaseEnabled = enabled;
        save();
    }

    /** null, wenn keine Regel für diesen Spieler existiert (= keine Einschränkung). */
    public static String getPurchaseRule(UUID player) {
        return data.playerPurchaseRules.get(player.toString());
    }

    public static void setPurchaseRule(UUID player, boolean whitelist) {
        data.playerPurchaseRules.put(player.toString(), whitelist ? "WHITELIST" : "BLACKLIST");
        save();
    }

    /** true, wenn eine Regel entfernt wurde (false = Spieler hatte gar keine). */
    public static boolean clearPurchaseRule(UUID player) {
        boolean removed = data.playerPurchaseRules.remove(player.toString()) != null;
        if (removed) save();
        return removed;
    }

    /** Kein Eintrag = keine Einschränkung (darf kaufen). WHITELIST = darf kaufen. BLACKLIST = darf NICHT kaufen. */
    public static boolean isPurchaseAllowedForPlayer(UUID player) {
        return !"BLACKLIST".equals(getPurchaseRule(player));
    }

    /** Für die Settings-Anzeige (Gamemodes-Kategorie, Liste am Ende) - alle Spieler-UUIDs mit einer konfigurierten Regel. */
    public static java.util.Set<String> getAllPlayerPurchaseRuleUuids() {
        return data.playerPurchaseRules.keySet();
    }

    /** Aktiviert Creative (falls noch nicht aktiv) und verlängert die Restzeit um minutes. */
    public static void purchase(ServerPlayer player, int minutes) {
        Session session = data.sessions.get(player.getUUID().toString());
        long now = System.currentTimeMillis();
        if (session == null) {
            session = new Session();
            session.previousGameType = player.gameMode.getGameModeForPlayer().getName();
            session.expiryEpochMillis = now;
            data.sessions.put(player.getUUID().toString(), session);
        }
        session.expiryEpochMillis = Math.max(session.expiryEpochMillis, now) + minutes * 60_000L;
        save();
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
            player.setGameMode(GameType.CREATIVE);
        }
    }

    public static long getRemainingSeconds(UUID uuid) {
        Session session = data.sessions.get(uuid.toString());
        if (session == null) return 0;
        return Math.max(0, (session.expiryEpochMillis - System.currentTimeMillis()) / 1000);
    }

    /** true, wenn dieser Spieler gerade gekaufte Creative-Zeit übrig hat (unabhängig vom aktuell
     * aktiven Spielmodus - siehe Gamemode-Umschalt-Option im Wallet-Tab). */
    public static boolean hasActiveSession(UUID uuid) {
        return getRemainingSeconds(uuid) > 0;
    }

    /**
     * Wechselt zwischen Survival, Creative und Spectator, solange noch gekaufte Zeit übrig ist
     * (Nutzer-Vorgabe: Spieler soll die Zeit nicht durchgehend im Creative-Modus "verbrauchen"
     * müssen). Die Restzeit läuft unabhängig vom gerade aktiven Modus weiter (Ablauf per
     * checkExpiry bringt sie danach in previousGameType zurück). Kein Effekt, wenn keine Session
     * aktiv ist.
     */
    public static void switchGameMode(ServerPlayer player, GameType target) {
        if (!hasActiveSession(player.getUUID())) return;
        if (player.gameMode.getGameModeForPlayer() != target) {
            player.setGameMode(target);
        }
    }

    /**
     * Prüft, ob die Session dieses Spielers abgelaufen ist und setzt in dem Fall den vorherigen
     * Spielmodus zurück. Aufrufer: periodischer Server-Tick (siehe CobbleCompanion) + Login (für
     * den Fall, dass die Zeit während der Abwesenheit abgelaufen ist).
     */
    public static void checkExpiry(ServerPlayer player) {
        Session session = data.sessions.get(player.getUUID().toString());
        if (session == null) return;
        if (System.currentTimeMillis() < session.expiryEpochMillis) return;

        GameType previous = GameType.byName(session.previousGameType, GameType.SURVIVAL);
        player.setGameMode(previous);
        data.sessions.remove(player.getUUID().toString());
        save();
        player.sendSystemMessage(Component.translatableWithFallback(
            "cobblecompanion.msg.creative_expired", "Your purchased Creative time has run out."));
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.playerPurchaseRules == null) data.playerPurchaseRules = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
