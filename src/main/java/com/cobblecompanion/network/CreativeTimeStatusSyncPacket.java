package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientCreativeTimeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: aktueller Creative-Zeit-Preis + eigene Restzeit + ob Preis/Kauf-Schalter änderbar sind (AdminOp) + ob der Kauf aktuell überhaupt erlaubt ist. */
public record CreativeTimeStatusSyncPacket(long pricePerMinute, long remainingSeconds, boolean canEditPrice, boolean purchaseEnabled)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreativeTimeStatusSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "creative_time_status_sync"));

    public static final StreamCodec<ByteBuf, CreativeTimeStatusSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, CreativeTimeStatusSyncPacket::pricePerMinute,
        ByteBufCodecs.VAR_LONG, CreativeTimeStatusSyncPacket::remainingSeconds,
        ByteBufCodecs.BOOL, CreativeTimeStatusSyncPacket::canEditPrice,
        ByteBufCodecs.BOOL, CreativeTimeStatusSyncPacket::purchaseEnabled,
        CreativeTimeStatusSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(CreativeTimeStatusSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCreativeTimeHelper.setStatus(
            packet.pricePerMinute(), packet.remainingSeconds(), packet.canEditPrice(), packet.purchaseEnabled()));
    }
}
