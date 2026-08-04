package com.cobblecompanion.data;

import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsConfigBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Globale Server-Admin-Regeln (nur von OPs änderbar, gelten für alle Spieler).
 * Persistiert als Gson-JSON im Weltordner, gleiches Muster wie {@link PlayerActivityTracker}.
 */
public class ServerRulesManager {

    public static final int RULE_FORBID_GIFTING = 0;
    public static final int RULE_ALLOW_TELEPORT_TO_FRIENDS = 1;
    // Spiegeln direkt CobbleDollars' eigene Config (siehe CobbleDollarsConfigBridge) statt einer
    // eigenen Kopie - kein "data"-Feld dafür, um Desync zu vermeiden.
    public static final int RULE_EARN_COBBLEDOLLARS_FROM_NPC = 2;
    public static final int RULE_EARN_COBBLEDOLLARS_FROM_WILD_POKEMON = 3;

    private static class Data {
        boolean forbidGifting = false;
        boolean allowTeleportToFriends = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("cobblecompanion_server_rules.json");
        load();
    }

    public static boolean isForbidGifting() { return data.forbidGifting; }
    public static boolean isAllowTeleportToFriends() { return data.allowTeleportToFriends; }

    /** Live aus CobbleDollars' eigener Config gelesen, siehe CobbleDollarsConfigBridge. */
    public static boolean isEarnCobbleDollarsFromNPC() {
        return ModAvailability.isCobbleDollarsAvailable() && CobbleDollarsConfigBridge.isEarnFromNPC();
    }

    public static boolean isEarnCobbleDollarsFromWildPokemon() {
        return ModAvailability.isCobbleDollarsAvailable() && CobbleDollarsConfigBridge.isEarnFromWildPokemon();
    }

    public static double getCobbleDollarsIncomeMultiplier() {
        return ModAvailability.isCobbleDollarsAvailable() ? CobbleDollarsConfigBridge.getIncomeMultiplier() : 1.0;
    }

    public static void setCobbleDollarsIncomeMultiplier(double value) {
        if (ModAvailability.isCobbleDollarsAvailable()) CobbleDollarsConfigBridge.setIncomeMultiplier(value);
    }

    /** Setzt eine Regel per Index (siehe RULE_-Konstanten). Gibt false bei unbekanntem Index zurück. */
    public static boolean setRule(int rule, boolean value) {
        switch (rule) {
            case RULE_FORBID_GIFTING -> data.forbidGifting = value;
            case RULE_ALLOW_TELEPORT_TO_FRIENDS -> data.allowTeleportToFriends = value;
            case RULE_EARN_COBBLEDOLLARS_FROM_NPC -> {
                if (!ModAvailability.isCobbleDollarsAvailable()) return false;
                CobbleDollarsConfigBridge.setEarnFromNPC(value);
                return true;
            }
            case RULE_EARN_COBBLEDOLLARS_FROM_WILD_POKEMON -> {
                if (!ModAvailability.isCobbleDollarsAvailable()) return false;
                CobbleDollarsConfigBridge.setEarnFromWildPokemon(value);
                return true;
            }
            default -> { return false; }
        }
        save();
        return true;
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
