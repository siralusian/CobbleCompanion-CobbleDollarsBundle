package com.cobblecompanion.data;

import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nutzer-Fund: Cobbledollars eines OFFLINE Spielers können nicht direkt verändert werden - Grant/
 * Charge (siehe CobbleDollarsBridge) brauchen eine echte ServerPlayer-Instanz, und ein manuelles
 * Bearbeiten von CobbleDollars' eigener Speicherdatei wird beim nächsten Login des Spielers wieder
 * überschrieben (CobbleDollars lädt seinen eigenen, uns unbekannten Stand). Lösung nach demselben
 * Muster wie GamemodeInventorySyncManager's Abhol-Warteschlange (Nutzer-Vorgabe: "so wie mit dem
 * Inventar beim Deaktivieren"): Beträge, die einem offline Spieler zustehen (aktuell nur vom
 * Schlauen Beobachter, siehe ContentObserverRewardBridge), werden hier als EIN Netto-Betrag pro
 * Spieler gesammelt und erst gutgeschrieben, wenn der Spieler selbst über den Home-Tab "abholt" -
 * kein automatisches Gutschreiben beim Login, damit der Spieler es bewusst sieht (Nutzer-Vorgabe).
 *
 * Persistiert als Gson-JSON im Weltordner (atomarer Schreibvorgang), gleiches Muster wie
 * GamemodeInventorySyncManager/PastureBuilderManager.
 */
public final class PendingCobbleDollarsManager {

    private static final class Data {
        Map<String, Long> pending = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private PendingCobbleDollarsManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_pending_cobbledollars.json");
        load();
    }

    /** Summiert einen Betrag (positiv = Gutschrift, negativ = Schuld) für einen aktuell offline Spieler auf. */
    public static void addPending(UUID uuid, long amount) {
        if (amount == 0) return;
        data.pending.merge(uuid.toString(), amount, Long::sum);
        save();
    }

    public static long getPending(UUID uuid) {
        Long value = data.pending.get(uuid.toString());
        return value != null ? value : 0L;
    }

    public enum ClaimResult { NONE, GRANTED, CHARGED, INSUFFICIENT_FUNDS }

    /**
     * Bucht den ausstehenden Betrag jetzt (Spieler ist online, siehe Aufrufer). Bei einer negativen
     * Schuld und zu wenig Guthaben bleibt der Rest stehen (wie beim Sofort-Abzug in
     * ContentObserverRewardManager) - der Spieler kann es später erneut versuchen.
     */
    public static ClaimResult claim(ServerPlayer player) {
        long amount = getPending(player.getUUID());
        if (amount == 0) return ClaimResult.NONE;

        if (amount > 0) {
            CobbleDollarsBridge.grant(player, BigInteger.valueOf(amount));
            data.pending.remove(player.getUUID().toString());
            save();
            return ClaimResult.GRANTED;
        }

        boolean charged = CobbleDollarsBridge.charge(player, BigInteger.valueOf(-amount));
        if (!charged) return ClaimResult.INSUFFICIENT_FUNDS;
        data.pending.remove(player.getUUID().toString());
        save();
        return ClaimResult.CHARGED;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException | com.google.gson.JsonParseException ignored) {
            data = new Data();
        }
        if (data.pending == null) data.pending = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try {
            Path tmp = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }
}
