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

/**
 * Server -> Client: Antwort auf DexCompletionSearchRequestPacket, siehe ClientDexCompletionHelper.
 * selfStatus = "hasCaughtPokedex,ownsLivingDex" der GESUCHTEN Art (gepackt in ein String-Feld statt
 * 2 eigene BOOL-Felder, da StreamCodec.composite auf 5 Feld-Paare gedeckelt ist).
 */
public record DexCompletionSearchResponsePacket(String speciesName, String preEvolutionId, String preEvolutionEdge,
                                                 List<String> evolveLines, String selfStatus) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DexCompletionSearchResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "dex_completion_search_response"));

    public static final StreamCodec<ByteBuf, DexCompletionSearchResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DexCompletionSearchResponsePacket::speciesName,
        ByteBufCodecs.STRING_UTF8, DexCompletionSearchResponsePacket::preEvolutionId,
        ByteBufCodecs.STRING_UTF8, DexCompletionSearchResponsePacket::preEvolutionEdge,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), DexCompletionSearchResponsePacket::evolveLines,
        ByteBufCodecs.STRING_UTF8, DexCompletionSearchResponsePacket::selfStatus,
        DexCompletionSearchResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DexCompletionSearchResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDexCompletionHelper.setSearchResult(
            packet.speciesName(), packet.preEvolutionId(), packet.preEvolutionEdge(), packet.evolveLines(), packet.selfStatus()));
    }
}
