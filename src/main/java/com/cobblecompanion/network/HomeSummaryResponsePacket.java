package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientHomeHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> Client: Antwort auf HomeSummaryRequestPacket, siehe ClientHomeHelper. */
public record HomeSummaryResponsePacket(int seen, int caught, int living, int evolveReady, int catchNeeded)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HomeSummaryResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "home_summary_response"));

    public static final StreamCodec<ByteBuf, HomeSummaryResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, HomeSummaryResponsePacket::seen,
        ByteBufCodecs.VAR_INT, HomeSummaryResponsePacket::caught,
        ByteBufCodecs.VAR_INT, HomeSummaryResponsePacket::living,
        ByteBufCodecs.VAR_INT, HomeSummaryResponsePacket::evolveReady,
        ByteBufCodecs.VAR_INT, HomeSummaryResponsePacket::catchNeeded,
        HomeSummaryResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HomeSummaryResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHomeHelper.setSummary(
            packet.seen(), packet.caught(), packet.living(), packet.evolveReady(), packet.catchNeeded()));
    }
}
