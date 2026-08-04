package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientProfessorHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Pokédex-Stand + aktuell besessene Spezies (für die Blatt-Icon-Overlays) des
 * inspizierten Spielers, siehe ProfessorLivingDexRequestPacket. Der Client baut daraus dieselbe
 * eingebettete PokedexGUI wie beim normalen Pokédex-Knopf, tauscht zusätzlich
 * ClientLivingDexHelpers Spezies-Menge temporär aus (siehe CompanionScreen.buildProfessorLivingDexScreen()).
 */
public record ProfessorLivingDexResponsePacket(String targetName, CompoundTag pokedexTag, List<String> livingSpecies) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorLivingDexResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_livingdex_response"));

    public static final StreamCodec<ByteBuf, ProfessorLivingDexResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ProfessorLivingDexResponsePacket::targetName,
        ByteBufCodecs.COMPOUND_TAG, ProfessorLivingDexResponsePacket::pokedexTag,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ProfessorLivingDexResponsePacket::livingSpecies,
        ProfessorLivingDexResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ProfessorLivingDexResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfessorHelper.setLivingDexData(
            packet.targetName(), packet.pokedexTag(), packet.livingSpecies()));
    }
}
