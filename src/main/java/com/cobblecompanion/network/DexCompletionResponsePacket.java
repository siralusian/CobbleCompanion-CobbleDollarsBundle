package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientDexCompletionHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: Antwort auf DexCompletionRequestPacket, siehe ClientDexCompletionHelper. */
public record DexCompletionResponsePacket(List<String> catchLines, List<String> evolveLines) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DexCompletionResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dex_completion_response"));

    public static final StreamCodec<ByteBuf, DexCompletionResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), DexCompletionResponsePacket::catchLines,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), DexCompletionResponsePacket::evolveLines,
        DexCompletionResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DexCompletionResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDexCompletionHelper.setEntries(packet.catchLines(), packet.evolveLines()));
    }
}
