package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientFriendsHelper;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.ServerDataHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: Freundesliste ("name|uuid|online|seen|caught|living|allowsTeleport" je
 * Eintrag) und offene Freundschaftsanfragen ("name|uuid" je Eintrag) für den Friends-Tab.
 * allowsTeleport = hat der Freund "Freunde dürfen zu mir teleportieren" aktiviert (steuert, ob
 * der Teleport-Button im Client für diesen Freund angezeigt wird).
 */
public record FriendsSyncPacket(List<String> friends, List<String> requests) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FriendsSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "friends_sync"));

    public static final StreamCodec<ByteBuf, FriendsSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), FriendsSyncPacket::friends,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), FriendsSyncPacket::requests,
        FriendsSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Baut die Freundes-/Anfragedaten für einen Spieler und schickt sie ihm. Server-seitig,
     * daher keine Client-Referenzen hier. Dex-Zähler nur bei diesem Sync berechnet.
     */
    public static void buildAndSend(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        List<String> friends = new ArrayList<>();
        for (UUID friendUuid : FriendsManager.getFriends(player.getUUID())) {
            ServerPlayer online = server.getPlayerList().getPlayer(friendUuid);
            boolean isOnline = online != null;
            String name = isOnline ? online.getName().getString() : FriendsManager.getKnownName(friendUuid);
            ServerDataHelper.DexCounts counts = ServerDataHelper.getPlayerDexCounts(server, friendUuid);
            boolean allowsTeleport = FriendsManager.isTeleportAllowed(friendUuid);
            friends.add(name + "|" + friendUuid + "|" + isOnline + "|" + counts.seen + "|" + counts.caught + "|" + counts.living
                + "|" + allowsTeleport);
        }

        List<String> requests = new ArrayList<>();
        for (UUID senderUuid : FriendsManager.getPendingFor(player.getUUID())) {
            requests.add(FriendsManager.getKnownName(senderUuid) + "|" + senderUuid);
        }

        PacketDistributor.sendToPlayer(player, new FriendsSyncPacket(friends, requests));
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(FriendsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFriendsHelper.applySync(packet.friends(), packet.requests());
            // Auto-Accept: offene Anfragen sofort annehmen, wenn der Spieler das eingestellt hat.
            if (com.cobblecompanion.client.data.ClientSettingsHelper.isFriendsAutoAcceptRequests()
                    && com.cobblecompanion.client.data.ClientNetworkUtil.canSendToServer(FriendActionPacket.TYPE.id())) {
                for (ClientFriendsHelper.RequestItem req : ClientFriendsHelper.getRequests()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        FriendActionPacket.forUuid(FriendActionPacket.ACCEPT, req.uuid));
                }
            }
        });
    }
}
