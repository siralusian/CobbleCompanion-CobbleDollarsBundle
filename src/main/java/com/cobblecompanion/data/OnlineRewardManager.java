package com.cobblecompanion.data;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.integrations.ModAvailability;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Online-Belohnung": alle X Minuten Online-Zeit gibt es automatisch Y Cobbledollars. Rein
 * vanilla (kein Fremd-Mod-Import außer über CobbleDollarsBridge, die die eigentliche Gutschrift
 * macht). Persistiert als Gson-JSON im Weltordner, gleiches Muster wie {@link ServerRulesManager}.
 * Der Fortschritt pro Spieler (wie lange schon seit der letzten Belohnung online) ist bewusst rein
 * flüchtig (Map, kein Persistenz-Bedarf) - startet bei jedem Login neu zu zählen.
 */
public class OnlineRewardManager {

    private static class Data {
        boolean enabled = false;
        int intervalMinutes = 30;
        long amount = 1000;
        /**
         * Nutzer-Vorgabe: AdminOp kann einzelnen Spielern einen ERHÖHTEN Online-Bonus geben - als
         * FESTE zusätzliche Cobbledollars-Menge (kein Prozentsatz), oben auf den normalen `amount`
         * pro Intervall draufgerechnet. Spieler-UUID -> zusätzlicher Betrag, kein Eintrag = 0.
         */
        Map<String, Long> playerBonusAmount = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static Path dataFile;
    private static final Map<UUID, Long> lastRewardEpochMillis = new HashMap<>();

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(LevelResource.ROOT).resolve("cobblecompanion_online_reward.json");
        load();
    }

    public static boolean isEnabled() { return data.enabled; }
    public static int getIntervalMinutes() { return data.intervalMinutes; }
    public static long getAmount() { return data.amount; }

    public static void setSettings(boolean enabled, int intervalMinutes, long amount) {
        data.enabled = enabled;
        data.intervalMinutes = Math.max(1, intervalMinutes);
        data.amount = Math.max(0, amount);
        save();
    }

    public static long getBonusAmount(UUID player) {
        return data.playerBonusAmount.getOrDefault(player.toString(), 0L);
    }

    /**
     * Nutzer-Vorgabe: der Bonus darf auch NEGATIV sein (Abzug statt Erhöhung) - anders als vorher
     * wird deshalb NICHT mehr bei amount<=0 automatisch entfernt (0 wäre sonst nicht von "gerade
     * auf 0 gesetzt" unterscheidbar). Entfernen läuft jetzt ausschließlich über removeBonusAmount().
     */
    public static void setBonusAmount(UUID player, long amount) {
        data.playerBonusAmount.put(player.toString(), amount);
        save();
    }

    public static void removeBonusAmount(UUID player) {
        if (data.playerBonusAmount.remove(player.toString()) != null) save();
    }

    /** Einmalige Migration, siehe CentralItemPriceManager.scaleAllPricesBy10(). */
    public static void scaleAmountsBy10() {
        data.amount *= 10;
        data.playerBonusAmount.replaceAll((k, v) -> v * 10);
        save();
    }

    public static Map<String, Long> getAllBonuses() {
        return data.playerBonusAmount;
    }

    /** Startet die Zählung für einen frisch eingeloggten Spieler neu. */
    public static void onPlayerLogin(ServerPlayer player) {
        lastRewardEpochMillis.put(player.getUUID(), System.currentTimeMillis());
    }

    public static void onPlayerLogout(ServerPlayer player) {
        lastRewardEpochMillis.remove(player.getUUID());
    }

    /**
     * Aufrufer: periodischer Server-Tick (siehe CobbleCompanion.onServerTick). Nutzer-Vorgabe:
     * dieser Timer ist jetzt auch der gemeinsame Auslöser für die Schlauer-Beobachter-Meldung
     * ("Nachrichten-Intervall an den Online-Belohnungs-Timer anpassen") - läuft deshalb bewusst
     * IMMER auf Basis von intervalMinutes, auch wenn die Online-Belohnung selbst (enabled=false)
     * gerade nichts auszahlt, damit Beobachter-Beträge trotzdem regelmäßig gemeldet werden.
     */
    public static void tick(ServerPlayer player) {
        if (!ModAvailability.isCobbleDollarsAvailable()) return;
        long now = System.currentTimeMillis();
        long last = lastRewardEpochMillis.getOrDefault(player.getUUID(), now);
        long intervalMillis = data.intervalMinutes * 60_000L;
        if (now - last < intervalMillis) return;
        lastRewardEpochMillis.put(player.getUUID(), now);

        long onlineReward = 0;
        if (data.enabled) {
            // Nutzer-Vorgabe: der Bonus darf negativ sein (Abzug) - der Gesamtbetrag wird aber nie
            // unter 0 gekürzt (kein tatsächlicher Schuldenabzug vom Guthaben über diesen Weg).
            long total = Math.max(0, data.amount + getBonusAmount(player.getUUID()));
            if (total > 0 && CobbleDollarsBridge.grant(player, BigInteger.valueOf(total))) {
                onlineReward = total;
            }
        }

        long[] observerPending = com.cobblecompanion.integrations.create.ContentObserverRewardManager.pullPending(player.getUUID());
        long observerIncome = observerPending[0];
        long observerExpense = observerPending[1];
        if (onlineReward <= 0 && observerIncome <= 0 && observerExpense <= 0) return;

        if (onlineReward > 0) {
            TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.ONLINE_REWARD, String.valueOf(onlineReward), null);
        }
        if (observerIncome > 0) {
            TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.OBSERVER_REWARD, String.valueOf(observerIncome), null);
        }
        if (observerExpense > 0) {
            TransactionLogManager.addEntry(player.getUUID(), TransactionLogManager.OBSERVER_CHARGE, String.valueOf(observerExpense), null);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
            new com.cobblecompanion.network.AutomaticIncomeReportPacket(onlineReward, observerIncome, observerExpense));
    }

    private static void load() {
        data = new Data();
        if (dataFile == null || !Files.exists(dataFile)) return;
        try (Reader reader = Files.newBufferedReader(dataFile)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) data = loaded;
        } catch (IOException ignored) {}
        if (data.playerBonusAmount == null) data.playerBonusAmount = new HashMap<>();
    }

    private static void save() {
        if (dataFile == null) return;
        try (Writer writer = Files.newBufferedWriter(dataFile)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            CobbleCompanion.LOGGER.error("[CC] OnlineRewardManager.save fehlgeschlagen", e);
        }
    }
}
