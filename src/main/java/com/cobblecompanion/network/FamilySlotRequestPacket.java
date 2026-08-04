package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.EvolutionFamilyHelper;
import com.cobblecompanion.data.ServerDataHelper;
import com.cobblemon.mod.common.pokemon.Species;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: fragt für eine Art die "effektive" Pokédex-Nummer an, die der Pokédex-
 * Sortiermodus für die Box/Slot-Berechnung nutzen soll (siehe EvolutionFamilyHelper). Braucht
 * einen echten Server-Roundtrip aus demselben Grund wie EvolutionChainRequestPacket:
 * Species.getEvolutions()/getPreEvolution() sind auf einem echten Dedicated Server clientseitig
 * unzuverlässig.
 */
public record FamilySlotRequestPacket(String speciesName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FamilySlotRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "family_slot_request"));

    public static final StreamCodec<ByteBuf, FamilySlotRequestPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, FamilySlotRequestPacket::speciesName,
        FamilySlotRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FamilySlotRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Species species = ServerDataHelper.findSpeciesByName(packet.speciesName());
            int anchorDexNumber = species != null
                ? EvolutionFamilyHelper.effectiveAnchorDexNumber(species)
                : -1;

            PacketDistributor.sendToPlayer(player,
                new FamilySlotResponsePacket(packet.speciesName(), anchorDexNumber));
        });
    }
}
