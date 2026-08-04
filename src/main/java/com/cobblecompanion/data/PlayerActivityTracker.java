package com.cobblecompanion.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the last-seen timestamp of players so we can tell who was
 * recently active even while offline.
 */
public class PlayerActivityTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Long> lastSeen = new HashMap<>();
    private static Path dataFile;

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("cobblecompanion_activity.json");
        load();
    }

    public static void recordLogin(ServerPlayer player) {
        lastSeen.put(player.getName().getString(), System.currentTimeMillis());
        save();
    }

    public static long getLastSeen(String playerName) {
        return lastSeen.getOrDefault(playerName, 0L);
    }

    public static boolean wasActiveWithin(String playerName, long hours) {
        long last = getLastSeen(playerName);
        if (last == 0L) return false;
        long cutoff = System.currentTimeMillis() - (hours * 60 * 60 * 1000);
        return last >= cutoff;
    }

    public static Map<String, Long> getAllRecentPlayers(long hours) {
        long cutoff = System.currentTimeMillis() - (hours * 60 * 60 * 1000);
        Map<String, Long> recent = new HashMap<>();
        for (Map.Entry<String, Long> entry : lastSeen.entrySet()) {
            if (entry.getValue() >= cutoff) {
                recent.put(entry.getKey(), entry.getValue());
            }
        }
        return recent;
    }

    private static void load() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Type type = new TypeToken<HashMap<String, Long>>(){}.getType();
            Map<String, Long> loaded = GSON.fromJson(reader, type);
            if (loaded != null) lastSeen = loaded;
        } catch (IOException ignored) {}
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(lastSeen, writer);
        } catch (IOException ignored) {}
    }
}