package com.cobblecompanion.cobbledollarscreate;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Nutzer-Vorgabe (verknüpfte Zähler/Abzieher-Gruppen, 3. Live-Test - genaue Spezifikation): hält
 * pro Gruppen-"Frequenz" ({@link ContentObserverConfigManager.BlockConfig#groupId}) und pro
 * tatsächlicher Item-ID eine FIFO-Warteschlange offener "Versprechen" (ein Versprechen = eine
 * Zähl-Erkennung, die noch nicht durch eine passende Abzieher-Erkennung storniert wurde).
 *
 * Exakte Nutzer-Vorgabe (Korrektur nach 3. Live-Test, "unterschiedliche Preise für Zähler und
 * Abzieher bleiben erhalten"): "Wenn durch den Abzieher der älteste noch nicht ausgezahlte Eintrag
 * 'gelöscht' wird, wird geprüft wieviel $ gibt der Zähler und wieviel $ zieht der Abzieher ab. Ist
 * das Ergebnis 0, wird nichts ausgezahlt. Ist die Differenz über 0, wird der Betrag ausgezahlt. Ist
 * die Differenz unter 0, wird dieser Betrag abgezogen." - pro stornierter Einheit wird SOFORT die
 * Differenz (Zähler-Preis dieses Versprechens minus EIGENER Preis des abziehenden Beobachters für
 * dieses Item) an den Empfänger des Zähler-Versprechens verrechnet (positiv = zusätzliche
 * Auszahlung, negativ = Abzug vom Guthaben, 0 = weder noch). Setzen beide Seiten denselben Preis
 * ein (z.B. derselbe Katalog-Eintrag ist sowohl in der Zähler- als auch der Abzieher-Liste), ergibt
 * das automatisch die einfache "volle Stornierung" (Differenz 0). Wird ein Versprechen NIE von
 * einem Abzieher berührt, zahlt {@link #tick} bei Ablauf den vollen Zähler-Preis aus.
 *
 * Bewusst NICHT persistiert (anders als die meisten anderen Manager dieser Mod) - offene
 * Versprechen sind kurzlebig (30 Sekunden bis 30 Minuten) und ein Serverneustart mitten in diesem
 * Fenster ist der seltene Ausnahmefall; ein Neustart verwirft offene Versprechen ersatzlos statt
 * sie über einen Weltordner-Datei-Schreibzugriff bei JEDER Zähl-/Abzieh-Erkennung abzusichern.
 *
 * Getickt über ContentObserverPromiseTickHandler (ServerTickEvent.Post, gleiches Muster wie
 * MerchantShopPeriodicSyncHandler) statt bei jedem einzelnen Server-Tick, aus Performance-Gründen.
 */
public final class ContentObserverPromiseManager {

    private static final class Promise {
        long itemsRemaining;
        final long createdTick;
        final long expiryTicks;
        final String targetPlayerUuid;
        final long amountPerItem;

        Promise(long itemsRemaining, long createdTick, long expiryTicks, String targetPlayerUuid, long amountPerItem) {
            this.itemsRemaining = itemsRemaining;
            this.createdTick = createdTick;
            this.expiryTicks = expiryTicks;
            this.targetPlayerUuid = targetPlayerUuid;
            this.amountPerItem = amountPerItem;
        }
    }

    // groupId -> itemId -> FIFO-Warteschlange offener Versprechen
    private static final Map<String, Map<String, Deque<Promise>>> groups = new HashMap<>();

    private ContentObserverPromiseManager() {}

    /**
     * Zählender Beobachter hat itemCount Exemplare von itemId erkannt - legt ein neues Versprechen
     * über itemCount Einheiten an. expiryStage nutzt dieselbe Kodierung wie Creates Fabrikanzeiger-
     * Scrollfeld: -1 = nie, 0 = 30 Sekunden, N&gt;=1 = N Minuten.
     */
    public static void count(MinecraftServer server, String groupId, String itemId, int itemCount,
            String targetPlayerUuid, long amountPerItem, int expiryStage) {
        if (groupId == null || itemId == null || itemCount <= 0 || amountPerItem == 0 || targetPlayerUuid == null) return;
        long expiryTicks = expiryTicksFromStage(expiryStage);
        Promise promise = new Promise(itemCount, server.getTickCount(), expiryTicks, targetPlayerUuid, amountPerItem);
        groups.computeIfAbsent(groupId, g -> new HashMap<>())
            .computeIfAbsent(itemId, i -> new ArrayDeque<>())
            .addLast(promise);
    }

    /**
     * Abziehender Beobachter hat itemCount Exemplare von itemId zum Preis subtractorAmountPerItem
     * erkannt - storniert die ÄLTESTEN offenen Versprechen um itemCount Einheiten. Pro stornierter
     * Einheit wird SOFORT die Differenz (Zähler-Preis des jeweiligen Versprechens minus
     * subtractorAmountPerItem) an dessen Empfänger verrechnet - siehe Klassenkommentar.
     */
    public static void subtract(MinecraftServer server, String groupId, String itemId, int itemCount, long subtractorAmountPerItem) {
        if (groupId == null || itemId == null || itemCount <= 0) return;
        Map<String, Deque<Promise>> byItem = groups.get(groupId);
        if (byItem == null) return;
        Deque<Promise> queue = byItem.get(itemId);
        if (queue == null || queue.isEmpty()) return;

        long remaining = itemCount;
        long totalNet = 0;
        Iterator<Promise> it = queue.iterator();
        while (remaining > 0 && it.hasNext()) {
            Promise promise = it.next();
            long take = Math.min(remaining, promise.itemsRemaining);
            long netPerItem = promise.amountPerItem - subtractorAmountPerItem;
            long net = netPerItem * take;
            if (net != 0) {
                ContentObserverRewardBridge.payout(server, promise.targetPlayerUuid, net);
                totalNet += net;
            }
            promise.itemsRemaining -= take;
            remaining -= take;
            if (promise.itemsRemaining <= 0) it.remove();
        }
    }

    private static long expiryTicksFromStage(int stage) {
        if (stage < 0) return -1L;
        return stage == 0 ? 600L : stage * 20L * 60L;
    }

    /** Siehe ContentObserverPromiseTickHandler - zahlt abgelaufene Versprechen VOLL aus (nur der zu diesem Zeitpunkt noch übrige, nicht stornierte Anteil, siehe Klassenkommentar). */
    public static void tick(MinecraftServer server) {
        if (groups.isEmpty()) return;
        long now = server.getTickCount();

        for (Map<String, Deque<Promise>> byItem : groups.values()) {
            for (Deque<Promise> queue : byItem.values()) {
                Iterator<Promise> it = queue.iterator();
                while (it.hasNext()) {
                    Promise promise = it.next();
                    if (promise.expiryTicks < 0) continue; // "nie verfallen"
                    if (now - promise.createdTick < promise.expiryTicks) continue;

                    if (promise.itemsRemaining > 0) {
                        long amount = promise.itemsRemaining * promise.amountPerItem;
                        ContentObserverRewardBridge.payout(server, promise.targetPlayerUuid, amount);
                    }
                    it.remove();
                }
            }
        }
    }
}
