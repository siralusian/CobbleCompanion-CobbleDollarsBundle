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
 * Client -> Server: setzt (oder löscht, hasRecipient=false) den Verkaufserlös-Empfänger für EINE
 * einzelne verknüpfte Entity - nur wirksam, wenn das Netzwerk im Modus VARIES ist (siehe
 * CobbleMerchantPayoutManager), sonst still ignoriert. tickerPos wird nur für den Resync nach dem
 * Speichern gebraucht (welcher Preis-Editor gerade offen ist).
 */
public record SaleRecipientEntityUpdatePacket(BlockPos tickerPos, UUID entityUuid, boolean hasRecipient,
        UUID recipientUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaleRecipientEntityUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "sale_recipient_entity_update"));

    public static final StreamCodec<ByteBuf, SaleRecipientEntityUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SaleRecipientEntityUpdatePacket::tickerPos,
        UUIDUtil.STREAM_CODEC, SaleRecipientEntityUpdatePacket::entityUuid,
        ByteBufCodecs.BOOL, SaleRecipientEntityUpdatePacket::hasRecipient,
        UUIDUtil.STREAM_CODEC, SaleRecipientEntityUpdatePacket::recipientUuid,
        SaleRecipientEntityUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaleRecipientEntityUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            UUID freqId = CreateStockTickerInteractionHandler.resolveFreqIdAt(player.serverLevel(), packet.tickerPos());
            if (freqId == null || !CobbleMerchantPayoutManager.MODE_VARIES.equals(CobbleMerchantPayoutManager.getMode(freqId))) return;

            CobbleMerchantPayoutManager.setEntityRecipient(packet.entityUuid(), packet.hasRecipient() ? packet.recipientUuid() : null);
            CreateStockTickerInteractionHandler.resyncSaleRecipient(player, packet.tickerPos());
        });
    }
}
