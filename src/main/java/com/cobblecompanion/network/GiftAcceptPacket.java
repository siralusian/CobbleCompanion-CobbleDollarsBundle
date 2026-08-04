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
 * Client -> Server: Empfänger nimmt ein offenes Pokemon-Geschenk über den Home-Tab-Button an
 * (Alternative zum Chat-Command "/companion accept gift &lt;Name&gt;" für Spieler mit Client-Mod).
 */
public record GiftAcceptPacket(UUID fromUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiftAcceptPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gift_accept"));

    public static final StreamCodec<ByteBuf, GiftAcceptPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(GiftAcceptPacket::new, GiftAcceptPacket::fromUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GiftAcceptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            GiftManager.PendingGift gift = GiftManager.findPendingFromUuid(player.getUUID(), packet.fromUuid());
            if (gift == null) return;

            GiftManager.GiftResult result = GiftManager.accept(player, gift);
            switch (result) {
                case OK -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_received", "You received a Pokemon from %s!", gift.fromName()));
                case RULE_FORBIDDEN -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_rule_disabled", "Gifting Pokemon is disabled on this server."));
                case SENDER_OFFLINE -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_sender_offline", "%s needs to be online to complete the gift.", gift.fromName()));
                case POKEMON_NOT_FOUND -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_pokemon_not_found", "That Pokemon could not be found."));
                case RECEIVER_FULL -> player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.gift_receiver_full", "Your party and PC are full - make room and try again."));
                default -> {}
            }
            GiftPendingSyncPacket.buildAndSend(player);
        });
    }
}
