package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.FriendsManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: spiegelt das rein clientseitige Setting "Freunde dürfen zu mir
 * teleportieren" (ClientSettingsHelper.friendsAllowTeleportToMe) serverseitig in
 * FriendsManager, damit andere Spieler beim Anzeigen des Teleport-Buttons wissen, ob dieser
 * Freund das erlaubt. Gesendet bei jedem Umschalten im Settings-Tab sowie einmalig nach Login.
 */
public record TeleportPreferencePacket(boolean allow) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeleportPreferencePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "teleport_preference"));

    public static final StreamCodec<ByteBuf, TeleportPreferencePacket> CODEC =
        ByteBufCodecs.BOOL.map(TeleportPreferencePacket::new, TeleportPreferencePacket::allow);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleportPreferencePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            FriendsManager.setTeleportAllowed(player.getUUID(), packet.allow());

            // Alle Online-Freunde neu synchronisieren, damit ihr Teleport-Button sofort
            // erscheint/verschwindet, statt erst beim nächsten Öffnen des Friends-Tabs.
            MinecraftServer server = player.getServer();
            if (server == null) return;
            for (UUID friendUuid : FriendsManager.getFriends(player.getUUID())) {
                ServerPlayer friend = server.getPlayerList().getPlayer(friendUuid);
                if (friend != null) FriendsSyncPacket.buildAndSend(friend);
            }
        });
    }
}
