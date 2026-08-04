package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einmalige ×10-Umrechnung der zentralen Item-Preisliste und der Schlauer-Beobachter-Beträge (siehe
 * com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale) - Gegenstück zu Basis' eigener
 * CobbleDollarsScaleMigration (dortige Kontostände/Creative-Preis/Online-Belohnungen), das seit dem
 * Modul-Split die Preisliste/Schlauer-Beobachter-Beträge nicht mehr mit umrechnen kann (beide
 * liegen jetzt hier). Eigene, unabhängige "done"-Markierung, läuft nur einmalig und nur, wenn
 * CobbleDollars installiert ist (siehe CobbleCompanionDollarsCreate.onServerStarting).
 */
public final class CobbleDollarsScaleMigrationCreate {

    private static class Data {
        boolean done = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private CobbleDollarsScaleMigrationCreate() {}

    public static void runIfNeeded(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_cobbledollars_create_scale_migration.json");
        load();
        if (data.done) return;

        CentralItemPriceManager.scaleAllPricesBy10();
        ContentObserverConfigManager.scaleAmountsBy10();

        data.done = true;
        save();
        CobbleCompanionDollarsCreate.LOGGER.info(
            "[CC] CobbleDollars/Create-Skalierungs-Migration abgeschlossen: zentrale Item-Preisliste + "
                + "Schlauer-Beobachter-Beträge auf die neue Ein-Nachkommastellen-Anzeige (×10) umgerechnet.");
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
