package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Antwort auf EvolutionChainRequestPacket. chain-Einträge im Format
 * "speziesName:GENDER" (GENDER = MALE/FEMALE, leer falls die Entwicklung an kein bestimmtes
 * Geschlecht gebunden ist - siehe EvolutionGenderHelper), unsortiert/BFS-Reihenfolge über ALLE
 * Verzweigungen (nicht nur die erste).
 */
public record EvolutionChainResponsePacket(String speciesName, List<String> chain) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EvolutionChainResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "evolution_chain_response"));

    public static final StreamCodec<ByteBuf, EvolutionChainResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, EvolutionChainResponsePacket::speciesName,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), EvolutionChainResponsePacket::chain,
        EvolutionChainResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EvolutionChainResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.cobblecompanion.client.data.PCSortHelper.setEvolutionChain(packet.speciesName(), packet.chain());
        });
    }
}
