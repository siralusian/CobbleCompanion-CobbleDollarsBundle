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

/**
 * Client -> Server: Spieler bietet einem Freund eines seiner Party-Pokemon als Geschenk an.
 * Verschiebt NICHTS sofort - der Empfänger muss annehmen (per Chat-Command
 * "/companion accept gift &lt;Name&gt;", funktioniert auch ohne Client-Mod), siehe GiftManager.
 */
public record GiftOfferPacket(java.util.UUID pokemonUuid, java.util.UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiftOfferPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "gift_offer"));

    public static final StreamCodec<ByteBuf, GiftOfferPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, GiftOfferPacket::pokemonUuid,
        UUIDUtil.STREAM_CODEC, GiftOfferPacket::targetUuid,
        GiftOfferPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GiftOfferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            GiftManager.GiftResult result = GiftManager.createOffer(player, packet.pokemonUuid(), packet.targetUuid());
            switch (result) {
                case OK -> {
                    ServerPlayer target = server.getPlayerList().getPlayer(packet.targetUuid());
                    player.sendSystemMessage(Component.translatableWithFallback(
                        "cobblecompanion.msg.gift_offer_sent", "Gift offer sent to %s.",
                        target != null ? target.getName().getString() : "?"));
                    if (target != null) {
                        target.sendSystemMessage(Component.translatableWithFallback(
                            "cobblecompanion.msg.gift_offer_received",
                            "%s wants to give you a Pokemon! Type /companion accept gift %s to accept.",
                            player.getName().getString(), player.getName().getString()));
                        GiftPendingSyncPacket.buildAndSend(target);
                    }
                }
                case RULE_FORBIDDEN -> sendMsg(player, "cobblecompanion.msg.gift_rule_disabled", "Gifting Pokemon is disabled on this server.");
                case SELF -> sendMsg(player, "cobblecompanion.msg.gift_self", "You cannot gift yourself a Pokemon.");
                case NOT_FRIENDS -> sendMsg(player, "cobblecompanion.msg.gift_not_friends", "You can only gift Pokemon to friends.");
                case TARGET_OFFLINE -> sendMsg(player, "cobblecompanion.msg.gift_target_offline", "That player is not online.");
                case POKEMON_NOT_FOUND -> sendMsg(player, "cobblecompanion.msg.gift_pokemon_not_found", "That Pokemon could not be found.");
                case LAST_POKEMON -> sendMsg(player, "cobblecompanion.msg.gift_last_pokemon", "You cannot gift your only Pokemon.");
                default -> {}
            }
        });
    }

    private static void sendMsg(ServerPlayer player, String key, String fallback) {
        player.sendSystemMessage(Component.translatableWithFallback(key, fallback));
    }
}
