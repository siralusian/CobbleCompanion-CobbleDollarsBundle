package com.cobblecompanion.cobbledollarscreate.client.data;

import com.cobblecompanion.cobbledollarscreate.network.ContentObserverConfigSyncPacket;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reiner Datenhalter für den zuletzt vom Server empfangenen Schlaue-Beobachter-Editor-Stand (siehe
 * ContentObserverConfigSyncPacket) - referenziert bewusst KEINE Screen-Klasse, gleiches Muster wie
 * ClientCreateStockTickerHelper (siehe dessen Kommentar für das WARUM).
 *
 * Erweiterung (Nutzer-Vorgabe, mehrere Items pro Block + Lagernetzwerk-Preise + benannte
 * Zähler/Abzieher-Gruppen): hält jetzt die komplette dekodierte Katalog-Liste statt einzelner
 * Item/Spieler/Betrag-Felder, plus Gruppen-ID/-Name/Verfallszeit-Stufe/Netzwerk-Verbindungsstatus/
 * Mitgliederzahlen und die komplette Netzwerk-Preisliste.
 *
 * Erweiterung (Nutzer-Vorgabe, 3. Live-Test): der frühere feste "Rolle"-Schalter (subtractor) ist
 * entfallen - stattdessen zwei unabhängige aktivierte-Item-Mengen für Zähler-/Abzieher-Liste.
 *
 * Bugfix/Klarstellung (Nutzer-Fund, 4. Live-Test): {@code subtractorBlock} kehrt zurück - NUR als
 * Klassifizierung für "Aktiv/Inaktiv für alle setzen" (siehe ContentObserverConfigManager.BlockConfig#subtractorBlock).
 */
public class ClientContentObserverHelper {

    private static BlockPos pos = null;
    private static List<ContentObserverConfigSyncPacket.CatalogEntryView> rules = new ArrayList<>();
    private static String groupId = "";
    private static String groupName = "";
    private static Set<String> enabledCounterItemIds = Set.of();
    private static Set<String> enabledSubtractorItemIds = Set.of();
    private static boolean subtractorBlock = false;
    private static int promiseExpiryStage = 0;
    private static boolean networkConnected = false;
    private static String networkListName = "";
    private static int counterCount = 0;
    private static int subtractorCount = 0;
    private static Map<String, ContentObserverConfigSyncPacket.NetworkPriceView> networkPrices = Map.of();
    private static int version = 0;

    public static void setPending(ContentObserverConfigSyncPacket packet) {
        pos = packet.pos();
        rules = new ArrayList<>();
        for (String encoded : packet.rules()) rules.add(ContentObserverConfigSyncPacket.decodeCatalogEntry(encoded));
        groupId = packet.groupId();
        groupName = packet.groupName();
        enabledCounterItemIds = packet.enabledCounterItemIds();
        enabledSubtractorItemIds = packet.enabledSubtractorItemIds();
        subtractorBlock = packet.subtractorBlock();
        promiseExpiryStage = packet.promiseExpiryStage();
        networkConnected = packet.networkConnected();
        networkListName = packet.networkListName();
        counterCount = packet.counterCount();
        subtractorCount = packet.subtractorCount();
        networkPrices = packet.networkPriceMap();
        version++;
    }

    public static BlockPos getPos() { return pos; }
    public static List<ContentObserverConfigSyncPacket.CatalogEntryView> getRules() { return rules; }
    public static String getGroupId() { return groupId; }
    public static String getGroupName() { return groupName; }
    public static Set<String> getEnabledCounterItemIds() { return enabledCounterItemIds; }
    public static Set<String> getEnabledSubtractorItemIds() { return enabledSubtractorItemIds; }
    public static boolean isSubtractorBlock() { return subtractorBlock; }
    public static int getPromiseExpiryStage() { return promiseExpiryStage; }
    public static boolean isNetworkConnected() { return networkConnected; }
    public static String getNetworkListName() { return networkListName; }
    public static int getCounterCount() { return counterCount; }
    public static int getSubtractorCount() { return subtractorCount; }
    public static Map<String, ContentObserverConfigSyncPacket.NetworkPriceView> getNetworkPrices() { return networkPrices; }
    public static int getVersion() { return version; }
}
