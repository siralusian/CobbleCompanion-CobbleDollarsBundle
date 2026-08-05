package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.ContentObserverBridge;
import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverGroupCatalogManager;
import com.cobblecompanion.cobbledollarscreate.ContentObserverGroupManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.integrations.ModAvailability;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client -> Server: Schlauer-Beobachter-Editor "Speichern"/"Leeren"-Button (siehe
 * ContentObserverInteractionHandler/ContentObserverConfigScreen). clear=true = Konfiguration
 * komplett entfernen (setzt den echten Filter am Block ebenfalls zurück, alle anderen Felder
 * werden dabei ignoriert).
 *
 * Nutzer-Vorgabe (3. Live-Test, "geteilte Katalog-Liste ohne Block-Rolle"): {@code rules} kodiert
 * jeden Katalog-Eintrag als "itemId|targetPlayerName|amountPerItem|inCounterList|inSubtractorList"
 * (gleiches Format wie ContentObserverConfigSyncPacket#encodeCatalogEntry) - itemId akzeptiert
 * neben einer konkreten Item-ID zusätzlich leer oder "*" (JEDES Item), "minecraft:*" (Namespace-
 * Wildcard) und "#minecraft:logs" (Tag), siehe ContentObserverConfigManager.matches(). Bei einem
 * GRUPPIERTEN Block ist {@code rules} der VOLLSTÄNDIGE gewünschte geteilte Katalog (der Editor
 * zeigt links bereits den ganzen Katalog, Speichern schreibt ihn deshalb komplett zurück statt
 * granular zu diffen), bei einem ungruppierten Block seine eigene, unabhängige Liste.
 *
 * Ein Eintrag braucht nur dann einen gültigen Empfänger, wenn er zur ZÄHLER-Liste gehört
 * (inCounterList=true) - reine Abzieher-Einträge (nur inSubtractorList=true) dienen ausschließlich
 * der Item-Erkennung, siehe ContentObserverPromiseManager.
 *
 * {@code groupMeta} kodiert "&lt;groupId&gt;" + Trennzeichen + "&lt;groupName&gt;" + Trennzeichen +
 * "&lt;enabledCounterItemIdsCsv&gt;" + Trennzeichen + "&lt;enabledSubtractorItemIdsCsv&gt;" (leere
 * groupId = Gruppe verlassen/keiner zugehörig) - der Gruppenname wird zentral für ALLE Mitglieder
 * der Gruppe aktualisiert (siehe ContentObserverGroupManager). {@code meta} kodiert
 * "promiseExpiryStage|bulkActionItemId|bulkAction" (bulkAction ∈ {"", "ON", "OFF"} - Nutzer-Vorgabe:
 * beim Speichern kann für EIN Item die Aktiv/Inaktiv-Checkbox bei ALLEN Beobachtern der Gruppe
 * erzwungen werden, siehe {@link #handle}).
 */
public record ContentObserverConfigUpdatePacket(BlockPos pos, boolean clear, List<String> rules, String groupMeta, String meta)
        implements CustomPacketPayload {

    private static final char GROUP_META_SEPARATOR = (char) 0x1F;

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
        return new HashSet<>(java.util.Arrays.asList(csv.split(",")));
    }

    public Set<String> enabledCounterItemIds() {
        return csvToSet(groupMetaPart(2));
    }

    public Set<String> enabledSubtractorItemIds() {
        return csvToSet(groupMetaPart(3));
    }

    public static String encodeMeta(int promiseExpiryStage, boolean subtractorBlock, String bulkActionItemId, String bulkAction) {
        return promiseExpiryStage + "|" + subtractorBlock + "|" + (bulkActionItemId == null ? "" : bulkActionItemId) + "|" + (bulkAction == null ? "" : bulkAction);
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

    public String bulkActionItemId() {
        return metaPart(2);
    }

    /** "" (keine Bulk-Aktion), "ON" oder "OFF" - siehe Klassenkommentar. */
    public String bulkAction() {
        return metaPart(3);
    }

    private String metaPart(int index) {
        String[] parts = meta.split("\\|", -1);
        return index < parts.length ? parts[index] : "";
    }

    public static final CustomPacketPayload.Type<ContentObserverConfigUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_config_update"));

    public static final StreamCodec<ByteBuf, ContentObserverConfigUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ContentObserverConfigUpdatePacket::pos,
        ByteBufCodecs.BOOL, ContentObserverConfigUpdatePacket::clear,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ContentObserverConfigUpdatePacket::rules,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigUpdatePacket::groupMeta,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigUpdatePacket::meta,
        ContentObserverConfigUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverConfigUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Nutzer-Vorgabe: nur echte Minecraft-OPs dürfen den Schlauen Beobachter konfigurieren -
            // gleiches Muster wie die zentrale Preisliste (CentralItemPriceManager).
            if (!player.hasPermissions(2)) {
                CobbleCompanionDollarsCreate.LOGGER.info("[CC] Schlauer-Beobachter-Speichern von {} verweigert: kein Minecraft-OP", player.getName().getString());
                return;
            }
            if (!ModAvailability.isCreateAvailable()) return;
            if (!ContentObserverBridge.isContentObserver(player.level(), packet.pos())) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.contentobserver_save_failed", "Save failed - no Content Observer at this position anymore."));
                return;
            }

            if (packet.clear()) {
                ContentObserverConfigManager.remove(player.level().dimension(), packet.pos());
                ContentObserverBridge.setFilter(player.level(), packet.pos(), null);
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.contentobserver_cleared", "Content Observer configuration removed."));
                return;
            }

            List<ContentObserverGroupCatalogManager.CatalogEntry> parsedEntries = new ArrayList<>();
            Map<String, Item> concreteItemByPattern = new HashMap<>();
            for (String encoded : packet.rules()) {
                ContentObserverConfigSyncPacket.CatalogEntryView view = ContentObserverConfigSyncPacket.decodeCatalogEntry(encoded);
                String raw = view.itemId().trim();
                String targetPlayerRaw = view.targetPlayerName().trim();

                String normalizedPattern;
                Item concreteItem = null;
                if (raw.isEmpty() || raw.equals("*")) {
                    normalizedPattern = "*";
                } else if (raw.startsWith("#")) {
                    ResourceLocation tagRl = ResourceLocation.tryParse(raw.substring(1));
                    if (tagRl == null) {
                        player.sendSystemMessage(Component.translatableWithFallback(
                            "cobblecompanion.msg.contentobserver_unknown_item", "Unknown item ID: %s", raw));
                        continue;
                    }
                    normalizedPattern = "#" + TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagRl).location();
                } else if (raw.endsWith(":*")) {
                    normalizedPattern = raw;
                } else {
                    String itemId = raw.contains(":") ? raw : "minecraft:" + raw;
                    ResourceLocation itemRl = ResourceLocation.tryParse(itemId);
                    if (itemRl == null || !BuiltInRegistries.ITEM.containsKey(itemRl)) {
                        player.sendSystemMessage(Component.translatableWithFallback(
                            "cobblecompanion.msg.contentobserver_unknown_item", "Unknown item ID: %s", itemId));
                        continue;
                    }
                    concreteItem = BuiltInRegistries.ITEM.get(itemRl);
                    normalizedPattern = itemRl.toString();
                }

                // Bugfix (siehe Klassenkommentar): ein reiner Abzieher-Eintrag braucht keinen
                // Empfänger - leerer Name bleibt dann einfach null, statt den Eintrag komplett zu
                // verwerfen. Ein Zähler-Eintrag OHNE Empfänger kann dagegen nie auszahlen.
                UUID targetUuid = null;
                if (!targetPlayerRaw.isEmpty()) {
                    targetUuid = FriendsManager.resolvePlayerName(player.getServer(), targetPlayerRaw);
                    if (targetUuid == null) {
                        player.sendSystemMessage(Component.translatableWithFallback(
                            "cobblecompanion.msg.contentobserver_unknown_player", "Unknown player: %s", targetPlayerRaw));
                        continue;
                    }
                } else if (view.inCounterList()) {
                    continue;
                }

                ContentObserverGroupCatalogManager.CatalogEntry entry = new ContentObserverGroupCatalogManager.CatalogEntry();
                entry.itemId = normalizedPattern;
                entry.targetPlayerUuid = targetUuid != null ? targetUuid.toString() : null;
                entry.amountPerItem = view.amountPerItem();
                entry.inCounterList = view.inCounterList();
                entry.inSubtractorList = view.inSubtractorList();
                if (concreteItem != null) concreteItemByPattern.put(normalizedPattern, concreteItem);
                parsedEntries.add(entry);
            }

            String newGroupId = packet.groupId() == null || packet.groupId().isBlank() ? null : packet.groupId();

            ContentObserverConfigManager.BlockConfig cfg = new ContentObserverConfigManager.BlockConfig();
            cfg.groupId = newGroupId;
            cfg.promiseExpiryStage = packet.promiseExpiryStage();
            cfg.subtractorBlock = packet.subtractorBlock();

            Set<String> effectiveItemIds = new LinkedHashSet<>();
            if (newGroupId != null) {
                ContentObserverGroupCatalogManager.replaceCatalog(newGroupId, parsedEntries);
                cfg.rules = new ArrayList<>();
                cfg.enabledCounterItemIds = packet.enabledCounterItemIds();
                cfg.enabledSubtractorItemIds = packet.enabledSubtractorItemIds();
                for (ContentObserverGroupCatalogManager.CatalogEntry entry : parsedEntries) {
                    boolean effectiveCounter = entry.inCounterList && cfg.enabledCounterItemIds.contains(entry.itemId);
                    boolean effectiveSubtractor = entry.inSubtractorList && cfg.enabledSubtractorItemIds.contains(entry.itemId);
                    if (effectiveCounter || effectiveSubtractor) effectiveItemIds.add(entry.itemId);
                }
            } else {
                for (ContentObserverGroupCatalogManager.CatalogEntry entry : parsedEntries) {
                    ContentObserverConfigManager.Rule rule = new ContentObserverConfigManager.Rule();
                    rule.itemId = entry.itemId;
                    rule.targetPlayerUuid = entry.targetPlayerUuid;
                    rule.amountPerItem = entry.amountPerItem;
                    cfg.rules.add(rule);
                    effectiveItemIds.add(entry.itemId);
                }
                cfg.enabledCounterItemIds = new HashSet<>();
                cfg.enabledSubtractorItemIds = new HashSet<>();
            }

            ContentObserverConfigManager.set(player.level().dimension(), packet.pos(), cfg);
            if (cfg.groupId != null) ContentObserverGroupManager.setName(cfg.groupId, packet.groupName());

            // Nutzer-Vorgabe (3. Live-Test, "Aktiv/Inaktiv bei allen Beobachtern setzen"): beim
            // Speichern kann für EIN im Formular bearbeitetes Item erzwungen werden, dass ALLE
            // Beobachter der Gruppe (inkl. dieser) es fortan (nicht) erkennen - überschreibt deren
            // eigene, bisher individuell gesetzte Checkbox für genau dieses Item.
            //
            // Bugfix (Nutzer-Fund, 4. Live-Test): "alle Beobachter" heißt NICHT wirklich jeder
            // einzelne Block der Gruppe - ein reiner Abzieher-Block soll durch "Aktiv setzen" NICHT
            // plötzlich auch anfangen, dasselbe Item zu ZÄHLEN (und umgekehrt), nur weil derselbe
            // Katalog-Eintrag in beiden Listen geführt wird. Die Zähler-Seite der Bulk-Aktion trifft
            // deshalb nur als Zähler klassifizierte Blöcke, die Abzieher-Seite nur als Abzieher
            // klassifizierte (siehe BlockConfig#subtractorBlock).
            String bulkItemId = packet.bulkActionItemId();
            String bulkAction = packet.bulkAction();
            if (newGroupId != null && !bulkItemId.isBlank() && !bulkAction.isBlank()) {
                boolean on = bulkAction.equals("ON");
                boolean bulkInCounter = false, bulkInSubtractor = false;
                for (ContentObserverGroupCatalogManager.CatalogEntry entry : parsedEntries) {
                    if (entry.itemId.equals(bulkItemId)) {
                        bulkInCounter = entry.inCounterList;
                        bulkInSubtractor = entry.inSubtractorList;
                        break;
                    }
                }
                for (ContentObserverConfigManager.BlockConfig other : ContentObserverConfigManager.getGroupConfigs(newGroupId)) {
                    if (bulkInCounter && !other.subtractorBlock) {
                        if (on) other.enabledCounterItemIds.add(bulkItemId); else other.enabledCounterItemIds.remove(bulkItemId);
                    }
                    if (bulkInSubtractor && other.subtractorBlock) {
                        if (on) other.enabledSubtractorItemIds.add(bulkItemId); else other.enabledSubtractorItemIds.remove(bulkItemId);
                    }
                }
                ContentObserverConfigManager.saveNow();
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.contentobserver_bulk_applied", "%s set to %s for all observers in the group.", bulkItemId, on ? "active" : "inactive"));
            }

            // Nutzer-Vorgabe (mehrere Items pro Block): bei genau 1 (für DIESEN Block wirksamer)
            // Item zeigt der Filter-Slot das echte Item, bei mehreren "create:filter" - siehe
            // ContentObserverBridge-Klassenkommentar.
            Item filterItem = effectiveItemIds.size() == 1 ? concreteItemByPattern.get(effectiveItemIds.iterator().next()) : null;
            ContentObserverBridge.setFilterForRuleCount(player.level(), packet.pos(), filterItem, effectiveItemIds.size());

            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.contentobserver_saved", "Content Observer configured (%s rule(s)).",
                String.valueOf(effectiveItemIds.size())));
        });
    }
}
