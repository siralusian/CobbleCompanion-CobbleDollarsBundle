package com.cobblecompanion.cobbledollarscreate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Nutzer-Vorgabe: Zähler/Abzieher-Gruppen (siehe ContentObserverConfigManager.BlockConfig#groupId)
 * lassen sich benennen, damit man bei mehreren Gruppen die Übersicht behält (ein Feature, das
 * Creates eigenes Frequenz-System laut Nutzer nicht hat) - eine Gruppen-ID kann von mehreren
 * Blöcken geteilt werden, der Name ist deshalb hier zentral pro groupId gespeichert statt auf
 * jedem einzelnen Block. Persistiert als Gson-JSON im Weltordner, gleiches Muster wie
 * ContentObserverConfigManager.
 */
public final class ContentObserverGroupManager {

    private static final class Data {
        Map<String, String> names = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private ContentObserverGroupManager() {}

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_content_observer_groups.json");
        load();
    }

    public static String getName(String groupId) {
        return groupId == null ? "" : data.names.getOrDefault(groupId, "");
    }

    public static void setName(String groupId, String name) {
        if (groupId == null) return;
        if (name == null || name.isBlank()) {
            data.names.remove(groupId);
        } else {
            data.names.put(groupId, name.trim());
        }
        save();
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {
        } catch (com.google.gson.JsonParseException e) {
            // Bugfix (Live-Fund, Server-Crash): siehe ContentObserverGroupCatalogManager#load -
            // ein inkompatibles/beschädigtes Format darf den Server-Start nie verhindern.
            CobbleCompanionDollarsCreate.LOGGER.warn(
                "[CC] {} ließ sich nicht lesen (inkompatibles/beschädigtes Format) - starte mit leeren Gruppennamen neu.", dataFile, e);
            data = new Data();
        }
        if (data.names == null) data.names = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }
}
