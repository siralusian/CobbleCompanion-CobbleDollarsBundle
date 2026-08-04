package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.ContentObserverBridge;
import com.cobblecompanion.cobbledollarscreate.ContentObserverConfigManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.cobbledollars.CobbleDollarsScale;
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

import java.util.UUID;

/**
 * Client -> Server: Schlauer-Beobachter-Editor "Speichern"/"Leeren"-Button (siehe
 * ContentObserverInteractionHandler/ContentObserverConfigScreen). clear=true = Konfiguration
 * entfernen (setzt den echten Filter am Block ebenfalls zurück, itemId/targetPlayerName/
 * amountPerItem werden dabei ignoriert).
 *
 * Nutzer-Vorgabe: itemId akzeptiert beim Speichern (clear=false) neben einer konkreten Item-ID
 * zusätzlich leer oder "*" (JEDES Item), "minecraft:*" (Namespace-Wildcard) und "#minecraft:logs"
 * (Tag) - siehe ContentObserverConfigManager.matches() für die eigentliche Prüf-Logik zur
 * Laufzeit. Ein eigenes clear-Flag statt weiterhin "" als Lösch-Signal zu überladen, weil "" jetzt
 * eine eigene gültige (positive) Bedeutung hat ("beobachte alles").
 */
public record ContentObserverConfigUpdatePacket(BlockPos pos, boolean clear, String itemId, String targetPlayerName, long amountPerItem)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContentObserverConfigUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_config_update"));

    public static final StreamCodec<ByteBuf, ContentObserverConfigUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ContentObserverConfigUpdatePacket::pos,
        ByteBufCodecs.BOOL, ContentObserverConfigUpdatePacket::clear,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigUpdatePacket::itemId,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigUpdatePacket::targetPlayerName,
        ByteBufCodecs.VAR_LONG, ContentObserverConfigUpdatePacket::amountPerItem,
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

            String raw = packet.itemId().trim();
            String normalizedPattern;
            // Nur bei einem konkreten Item gesetzt - Create's eigene FilteringBehaviour kennt keine
            // Wildcards/Tags, siehe ContentObserverConfigManager.matches()-Klassenkommentar.
            Item concreteItem = null;

            if (raw.isEmpty() || raw.equals("*")) {
                normalizedPattern = "*";
            } else if (raw.startsWith("#")) {
                ResourceLocation tagRl = ResourceLocation.tryParse(raw.substring(1));
                if (tagRl == null) {
                    player.sendSystemMessage(Component.translatableWithFallback(
                        "cobblecompanion.msg.contentobserver_unknown_item", "Unknown item ID: %s", raw));
                    return;
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
                    return;
                }
                concreteItem = BuiltInRegistries.ITEM.get(itemRl);
                normalizedPattern = itemRl.toString();
            }

            UUID targetUuid = FriendsManager.resolvePlayerName(player.getServer(), packet.targetPlayerName().trim());
            if (targetUuid == null) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.contentobserver_unknown_player", "Unknown player: %s", packet.targetPlayerName()));
                return;
            }

            ContentObserverConfigManager.Entry entry = new ContentObserverConfigManager.Entry();
            entry.itemId = normalizedPattern;
            entry.targetPlayerUuid = targetUuid.toString();
            // Nutzer-Vorgabe: negativ = Abzug statt Gutschrift pro Item (siehe
            // ContentObserverRewardManager) - bewusst kein Math.max(0, ...) mehr.
            entry.amountPerItem = packet.amountPerItem();
            ContentObserverConfigManager.set(player.level().dimension(), packet.pos(), entry);
            ContentObserverBridge.setFilter(player.level(), packet.pos(), concreteItem);

            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.contentobserver_saved", "Content Observer configured: %s -> %s Cobbledollars per item for %s.",
                entry.itemId, CobbleDollarsScale.formatRaw(java.math.BigInteger.valueOf(entry.amountPerItem)), packet.targetPlayerName()));
        });
    }
}
