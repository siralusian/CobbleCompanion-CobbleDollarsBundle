package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.FriendsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: Freundschafts-Aktion. action: 0=REQUEST_BY_NAME (targetName gesetzt),
 * 1=ACCEPT, 2=DECLINE, 3=REMOVE (jeweils targetUuid gesetzt). Der Server validiert, mutiert
 * die FriendsManager-Daten und synct danach die betroffenen (online) Spieler.
 */
public record FriendActionPacket(int action, String targetName, UUID targetUuid) implements CustomPacketPayload {

    public static final int REQUEST_BY_NAME = 0;
    public static final int ACCEPT = 1;
    public static final int DECLINE = 2;
    public static final int REMOVE = 3;

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    public static final CustomPacketPayload.Type<FriendActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "friend_action"));

    public static final StreamCodec<ByteBuf, FriendActionPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, FriendActionPacket::action,
        ByteBufCodecs.STRING_UTF8, FriendActionPacket::targetName,
        UUIDUtil.STREAM_CODEC, FriendActionPacket::targetUuid,
        FriendActionPacket::new);

    public static FriendActionPacket requestByName(String name) {
        return new FriendActionPacket(REQUEST_BY_NAME, name, EMPTY_UUID);
    }

    public static FriendActionPacket forUuid(int action, UUID uuid) {
        return new FriendActionPacket(action, "", uuid);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FriendActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            switch (packet.action()) {
                case REQUEST_BY_NAME -> handleRequest(server, player, packet.targetName());
                case ACCEPT -> {
                    if (FriendsManager.accept(player.getUUID(), packet.targetUuid())) {
                        syncBoth(server, player.getUUID(), packet.targetUuid());
                        notifyBoth(server, player.getUUID(), packet.targetUuid(),
                            "cobblecompanion.msg.friend_added", "You are now friends with %s.");
                    }
                }
                case DECLINE -> {
                    if (FriendsManager.decline(player.getUUID(), packet.targetUuid())) {
                        FriendsSyncPacket.buildAndSend(player);
                    }
                }
                case REMOVE -> {
                    if (FriendsManager.remove(player.getUUID(), packet.targetUuid())) {
                        syncBoth(server, player.getUUID(), packet.targetUuid());
                    }
                }
            }
        });
    }

    private static void handleRequest(MinecraftServer server, ServerPlayer from, String targetName) {
        FriendsManager.RequestResult result = FriendsManager.sendRequest(server, from, targetName);
        switch (result) {
            case SENT -> {
                sendMsg(from, "cobblecompanion.msg.friend_request_sent", "Friend request sent to %s.", targetName);
                // Empfänger (falls online) über neue Anfrage informieren + Sync.
                ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
                if (target != null) {
                    sendMsg(target, "cobblecompanion.msg.friend_request_received",
                        "%s sent you a friend request.", from.getName().getString());
                    FriendsSyncPacket.buildAndSend(target);
                }
            }
            case ACCEPTED_MUTUAL -> {
                ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
                UUID targetUuid = target != null ? target.getUUID() : null;
                sendMsg(from, "cobblecompanion.msg.friend_added", "You are now friends with %s.", targetName);
                FriendsSyncPacket.buildAndSend(from);
                if (target != null) {
                    sendMsg(target, "cobblecompanion.msg.friend_added", "You are now friends with %s.", from.getName().getString());
                    FriendsSyncPacket.buildAndSend(target);
                }
            }
            case ALREADY_FRIENDS -> sendMsg(from, "cobblecompanion.msg.friend_already", "You are already friends with %s.", targetName);
            case ALREADY_PENDING -> sendMsg(from, "cobblecompanion.msg.friend_pending", "You already sent %s a request.", targetName);
            case SELF -> sendMsg(from, "cobblecompanion.msg.friend_self", "You cannot add yourself.", targetName);
            case PLAYER_NOT_FOUND -> sendMsg(from, "cobblecompanion.msg.friend_not_found", "Player %s not found.", targetName);
        }
    }

    private static void syncBoth(MinecraftServer server, UUID a, UUID b) {
        ServerPlayer pa = server.getPlayerList().getPlayer(a);
        if (pa != null) FriendsSyncPacket.buildAndSend(pa);
        ServerPlayer pb = server.getPlayerList().getPlayer(b);
        if (pb != null) FriendsSyncPacket.buildAndSend(pb);
    }

    private static void notifyBoth(MinecraftServer server, UUID a, UUID b, String key, String fallback) {
        ServerPlayer pa = server.getPlayerList().getPlayer(a);
        ServerPlayer pb = server.getPlayerList().getPlayer(b);
        if (pa != null && pb != null) {
            sendMsg(pa, key, fallback, pb.getName().getString());
            sendMsg(pb, key, fallback, pa.getName().getString());
        }
    }

    private static void sendMsg(ServerPlayer player, String key, String fallback, String arg) {
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback, arg));
    }
}
