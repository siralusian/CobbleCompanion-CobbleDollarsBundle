package com.cobblecompanion.integrations.cobbledollars;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.CreativeTimeManager;
import com.cobblecompanion.data.OnlineRewardManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Einmalige ×10-Umrechnung ALLER Cobbledollars-Beträge (siehe {@link CobbleDollarsScale}), läuft
 * genau einmal beim ersten Serverstart nach Einführung der Ein-Nachkommastellen-Anzeige - danach
 * bleibt data.done dauerhaft true, gleiches Muster wie {@link com.cobblecompanion.data.InvSyncMigration}.
 * Skaliert sowohl CobbleDollars' EIGENE Kontostände (online + offline, über PlayerExtensionKt) als
 * auch alle bei uns gespeicherten Preise/Beträge, damit der reale Wert für Spieler unverändert
 * bleibt.
 *
 * WICHTIG: nur ausführen, wenn ModAvailability.isCobbleDollarsAvailable() - ohne CobbleDollars
 * gibt es weder Kontostände noch Preise, die umzurechnen wären.
 */
public final class CobbleDollarsScaleMigration {

    private static class Data {
        boolean done = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;

    private CobbleDollarsScaleMigration() {}

    public static void runIfNeeded(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_cobbledollars_scale_migration.json");
        load();
        if (data.done) return;

        int migratedAccounts = 0;
        Map<String, BigInteger> allBalances = PlayerExtensionKt.getAllPlayerCobbleDollars(server);
        for (Map.Entry<String, BigInteger> entry : allBalances.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            BigInteger oldBalance = entry.getValue();
            if (oldBalance == null || oldBalance.signum() == 0) continue;
            BigInteger newBalance = oldBalance.multiply(CobbleDollarsScale.SCALE);

            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                PlayerExtensionKt.setCobbleDollars(online, newBalance);
            } else {
                BigInteger delta = newBalance.subtract(oldBalance);
                PlayerExtensionKt.addOfflineCobbleDollars(uuid, server, delta);
            }
            migratedAccounts++;
        }

        CreativeTimeManager.scalePricePerMinuteBy10();
        OnlineRewardManager.scaleAmountsBy10();
        // Zentrale Item-Preisliste + Schlauer-Beobachter-Beträge liegen seit dem Modul-Split in
        // CobbleCompanion: CobbleDollars/Create - siehe dortiges CobbleDollarsScaleMigrationCreate
        // (eigene, unabhängige "done"-Markierung, läuft nur wenn dieses Modul installiert ist).

        data.done = true;
        save();
        CobbleCompanion.LOGGER.info(
            "[CC] CobbleDollars-Skalierungs-Migration abgeschlossen: {} Kontostände + Creative-Preis/"
                + "Online-Belohnungen auf die neue Ein-Nachkommastellen-Anzeige (×10) umgerechnet.",
            migratedAccounts);
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
