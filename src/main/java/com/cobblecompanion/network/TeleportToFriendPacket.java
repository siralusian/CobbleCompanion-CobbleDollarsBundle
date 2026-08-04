package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.ServerRulesManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: "Teleport zu Freund"-Klick im Friends-Tab. Server prüft erneut alle
 * Bedingungen (Server-Regel, Freundschaft, Ziel online, Ziel erlaubt Teleport) und führt bei
 * Erfolg den Teleport über den normalen "/tp"-Befehl aus (statt eigener teleportTo()-Logik) -
 * dadurch übernimmt Vanilla automatisch Dimensionswechsel, Chunk-Laden etc. korrekt.
 */
public record TeleportToFriendPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeleportToFriendPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "teleport_to_friend"));

    public static final StreamCodec<ByteBuf, TeleportToFriendPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(TeleportToFriendPacket::new, TeleportToFriendPacket::targetUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleportToFriendPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            if (!ServerRulesManager.isAllowTeleportToFriends()) {
                sendMsg(player, "cobblecompanion.msg.teleport_rule_disabled", "Teleporting to friends is disabled on this server.");
                return;
            }
            if (!FriendsManager.areFriends(player.getUUID(), packet.targetUuid())) {
                sendMsg(player, "cobblecompanion.msg.teleport_not_friends", "You can only teleport to friends.");
                return;
            }
            ServerPlayer target = server.getPlayerList().getPlayer(packet.targetUuid());
            if (target == null) {
                sendMsg(player, "cobblecompanion.msg.teleport_target_offline", "That player is not online.");
                return;
            }
            if (!FriendsManager.isTeleportAllowed(packet.targetUuid())) {
                sendMsg(player, "cobblecompanion.msg.teleport_not_allowed", "%s does not allow friends to teleport to them.", target.getName().getString());
                return;
            }

            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "tp " + player.getGameProfile().getName() + " " + target.getGameProfile().getName());
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] Teleport-to-friend command failed", e);
            }
        });
    }

    private static void sendMsg(ServerPlayer player, String key, String fallback, Object... args) {
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback, args));
    }
}
