package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.GiftManager;
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
 * Client -> Server: Empfänger lehnt ein offenes Pokemon-Geschenk über den Home-Tab-Button ab
 * (kein Transfer, Pokemon bleibt beim Absender - siehe GiftManager.decline).
 */
public record GiftDeclinePacket(UUID fromUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiftDeclinePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gift_decline"));

    public static final StreamCodec<ByteBuf, GiftDeclinePacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(GiftDeclinePacket::new, GiftDeclinePacket::fromUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GiftDeclinePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            GiftManager.PendingGift gift = GiftManager.findPendingFromUuid(player.getUUID(), packet.fromUuid());
            if (gift == null) return;

            GiftManager.decline(player.getUUID(), gift);

            ServerPlayer sender = server.getPlayerList().getPlayer(gift.fromUuid());
            if (sender != null) {
                sender.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_declined", "%s declined your Pokemon gift.", player.getName().getString()));
            }
            GiftPendingSyncPacket.buildAndSend(player);
        });
    }
}
