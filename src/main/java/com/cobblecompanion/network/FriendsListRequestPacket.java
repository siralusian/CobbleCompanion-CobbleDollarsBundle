package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Anfrage nach Freundesliste und offenen Anfragen, gesendet beim
 * Wechsel auf den Friends-Tab. Der Server antwortet mit FriendsSyncPacket.
 */
public record FriendsListRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FriendsListRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "friends_list_request"));

    public static final StreamCodec<ByteBuf, FriendsListRequestPacket> CODEC =
        StreamCodec.unit(new FriendsListRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FriendsListRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                FriendsSyncPacket.buildAndSend(player);
            }
        });
    }
}
