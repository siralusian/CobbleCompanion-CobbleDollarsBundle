package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientContentObserverHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server -> Client: Strg+Rechtsklick auf einen Schlauen Beobachter (create:content_observer, siehe
 * ContentObserverInteractionHandler) - öffnet den eigenen Konfigurations-Editor mit dem aktuellen
 * Stand. Leere rules-Liste = noch nicht konfiguriert.
 *
 * Nutzer-Vorgabe (3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): {@code rules} kodiert
 * jeden Katalog-Eintrag als "itemId|targetPlayerName|amountPerItem|inCounterList|inSubtractorList"
 * (siehe {@link #encodeCatalogEntry}/{@link #decodeCatalogEntry}) - bei einem GRUPPIERTEN Block ist
 * das der VOLLSTÄNDIGE geteilte Gruppen-Katalog (siehe ContentObserverGroupCatalogManager), bei
 * einem ungruppierten Block seine eigene, unabhängige Liste (inCounterList/inSubtractorList dabei
 * immer false/unbenutzt). Der frühere feste "Rolle"-Schalter pro Block ist entfallen - jeder Block
 * wählt unabhängig für Zähler- UND Abzieher-Liste per Checkbox, welche Katalog-Items er selbst
 * erkennen soll ({@link #enabledCounterItemIds}/{@link #enabledSubtractorItemIds}, letzte beide
 * Teile von {@code groupMeta}).
 *
 * Nutzer-Vorgabe (Lagernetzwerk-Preise): {@code networkPrices} enthält die KOMPLETTE Preisliste des
 * per Lagerverbinder/-ticker angeschlossenen Netzwerks als "itemId=ankaufspreis=verkaufspreis" -
 * der Editor kann damit für JEDES getippte/ausgewählte Item lokal sofort den Netzwerkpreis
 * nachschlagen, ohne für jede Eingabe einen weiteren Server-Rundlauf zu brauchen.
 *
 * {@code groupMeta}/{@code meta} packen mehrere Werte in ein Textfeld (Records/StreamCodec.composite
 * erlauben max. 5 Top-Level-Feldpaare, siehe AdminEditPokemonPacket-Vorbild für dasselbe
 * Packmuster): groupMeta = "&lt;groupId&gt;" + Trennzeichen + "&lt;groupName&gt;" + Trennzeichen +
 * "&lt;enabledCounterItemIdsCsv&gt;" + Trennzeichen + "&lt;enabledSubtractorItemIdsCsv&gt;" (leere
 * groupId = keine Gruppe), meta = "promiseExpiryStage|networkConnected|counterCount|subtractorCount|networkListName".
 */
public record ContentObserverConfigSyncPacket(BlockPos pos, List<String> rules, String groupMeta, String meta, List<String> networkPrices)
        implements CustomPacketPayload {

    /** Trennzeichen für groupMeta - ASCII "unit separator" (0x1F), kommt in normalem Freitext praktisch nie vor. */
    private static final char GROUP_META_SEPARATOR = (char) 0x1F;

    public record CatalogEntryView(String itemId, String targetPlayerName, long amountPerItem, boolean inCounterList, boolean inSubtractorList) {}
    public record NetworkPriceView(long ankaufspreis, long verkaufspreis) {}

    public static String encodeCatalogEntry(String itemId, String targetPlayerName, long amountPerItem, boolean inCounterList, boolean inSubtractorList) {
        return itemId + "|" + targetPlayerName + "|" + amountPerItem + "|" + inCounterList + "|" + inSubtractorList;
    }

    public static CatalogEntryView decodeCatalogEntry(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length < 3) return new CatalogEntryView("", "", 0, false, false);
        long amount = 0;
        try {
            amount = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {}
        boolean inCounter = parts.length > 3 && Boolean.parseBoolean(parts[3]);
        boolean inSubtractor = parts.length > 4 && Boolean.parseBoolean(parts[4]);
        return new CatalogEntryView(parts[0], parts[1], amount, inCounter, inSubtractor);
    }

    public static String encodeGroupMeta(String groupId, String groupName, String enabledCounterItemIdsCsv, String enabledSubtractorItemIdsCsv) {
        return (groupId == null ? "" : groupId) + GROUP_META_SEPARATOR + (groupName == null ? "" : groupName)
            + GROUP_META_SEPARATOR + (enabledCounterItemIdsCsv == null ? "" : enabledCounterItemIdsCsv)
            + GROUP_META_SEPARATOR + (enabledSubtractorItemIdsCsv == null ? "" : enabledSubtractorItemIdsCsv);
    }

    private String groupMetaPart(int index) {
        String[] parts = groupMeta.split(String.valueOf(GROUP_META_SEPARATOR), -1);
        return index < parts.length ? parts[index] : "";
    }

    public String groupId() {
        return groupMetaPart(0);
    }

    public String groupName() {
        return groupMetaPart(1);
    }

    private static Set<String> csvToSet(String csv) {
        if (csv.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(csv.split(",")));
    }

    /** Item-Pattern-Strings, die DIESER Block als ZÄHLER aus dem Gruppen-Katalog erkennen soll. */
    public Set<String> enabledCounterItemIds() {
        return csvToSet(groupMetaPart(2));
    }

    /** Item-Pattern-Strings, die DIESER Block als ABZIEHER aus dem Gruppen-Katalog erkennen soll. */
    public Set<String> enabledSubtractorItemIds() {
        return csvToSet(groupMetaPart(3));
    }

    public static String encodeMeta(int promiseExpiryStage, boolean subtractorBlock, boolean networkConnected, int counterCount, int subtractorCount, String networkListName) {
        return promiseExpiryStage + "|" + subtractorBlock + "|" + networkConnected + "|" + counterCount + "|" + subtractorCount + "|" + (networkListName == null ? "" : networkListName);
    }

    /** Netzwerk-Preislisten-Name fürs Info-Panel (leer, wenn nicht verbunden) - letztes Feld, deshalb mit split-Limit gelesen statt über metaPart() (Name selbst könnte theoretisch "|" enthalten). */
    public String networkListName() {
        String[] parts = meta.split("\\|", 6);
        return parts.length > 5 ? parts[5] : "";
    }

    public int promiseExpiryStage() {
        try {
            return Integer.parseInt(metaPart(0));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Reine Bulk-Aktion-Klassifizierung dieses Blocks (Zähler-/Abzieher-Block) - siehe ContentObserverConfigManager.BlockConfig#subtractorBlock. */
    public boolean subtractorBlock() {
        return Boolean.parseBoolean(metaPart(1));
    }

    public boolean networkConnected() {
        return Boolean.parseBoolean(metaPart(2));
    }

    public int counterCount() {
        try {
            return Integer.parseInt(metaPart(3));
        } catch (Exception e) {
            return 0;
        }
    }

    public int subtractorCount() {
        try {
            return Integer.parseInt(metaPart(4));
        } catch (Exception e) {
            return 0;
        }
    }

    private String metaPart(int index) {
        String[] parts = meta.split("\\|", -1);
        return index < parts.length ? parts[index] : "";
    }

    public static String encodeNetworkPrice(String itemId, long ankaufspreis, long verkaufspreis) {
        return itemId + "=" + ankaufspreis + "=" + verkaufspreis;
    }

    /** Baut eine schnelle Item-ID -> Preis-Lookup-Map aus {@link #networkPrices} - für den Client-Screen. */
    public Map<String, NetworkPriceView> networkPriceMap() {
        Map<String, NetworkPriceView> result = new HashMap<>();
        for (String encoded : networkPrices) {
            String[] parts = encoded.split("=", -1);
            if (parts.length < 3) continue;
            try {
                result.put(parts[0], new NetworkPriceView(Long.parseLong(parts[1]), Long.parseLong(parts[2])));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public static final CustomPacketPayload.Type<ContentObserverConfigSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_config_sync"));

    public static final StreamCodec<ByteBuf, ContentObserverConfigSyncPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ContentObserverConfigSyncPacket::pos,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ContentObserverConfigSyncPacket::rules,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigSyncPacket::groupMeta,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigSyncPacket::meta,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ContentObserverConfigSyncPacket::networkPrices,
        ContentObserverConfigSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverConfigSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientContentObserverHelper.setPending(packet));
    }
}
