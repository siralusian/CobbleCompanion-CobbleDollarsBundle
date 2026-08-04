package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.CreateStockTickerInteractionHandler;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantPayoutManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: setzt den Verkaufserlös-Empfänger-Modus für das GESAMTE Netzwerk des gerade im
 * Preis-Editor geöffneten Lagertickers (siehe CobbleMerchantPayoutManager). hasRecipient=false ->
 * recipientUuid wird ignoriert (Modus NONE oder VARIES brauchen keinen Netzwerk-Empfänger). Nur
 * echte Minecraft-OPs (gleiches Muster wie der restliche Preis-Editor).
 */
public record SaleRecipientNetworkUpdatePacket(BlockPos tickerPos, String mode, boolean hasRecipient,
        UUID recipientUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaleRecipientNetworkUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "sale_recipient_network_update"));

    public static final StreamCodec<ByteBuf, SaleRecipientNetworkUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SaleRecipientNetworkUpdatePacket::tickerPos,
        ByteBufCodecs.STRING_UTF8, SaleRecipientNetworkUpdatePacket::mode,
        ByteBufCodecs.BOOL, SaleRecipientNetworkUpdatePacket::hasRecipient,
        UUIDUtil.STREAM_CODEC, SaleRecipientNetworkUpdatePacket::recipientUuid,
        SaleRecipientNetworkUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaleRecipientNetworkUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            UUID freqId = CreateStockTickerInteractionHandler.resolveFreqIdAt(player.serverLevel(), packet.tickerPos());
            if (freqId == null) return;

            CobbleMerchantPayoutManager.setNetworkPayout(freqId, packet.mode(), packet.hasRecipient() ? packet.recipientUuid() : null);
            CreateStockTickerInteractionHandler.resyncSaleRecipient(player, packet.tickerPos());
        });
    }
}
