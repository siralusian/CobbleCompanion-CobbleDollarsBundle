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

/**
 * Server -> Client: Pokédex-Stand des inspizierten Spielers als NBT (PokedexManager.Companion.CODEC,
 * siehe ProfessorPokedexRequestPacket). Der Client rekonstruiert daraus einen echten PokedexManager
 * und tauscht CobblemonClient.INSTANCE.clientPokedexData temporär aus, um Cobblemons eigenes
 * Pokédex-Fenster (per Reflection eingebettet) im Nur-Lese-Modus zu zeigen.
 */
public record ProfessorPokedexResponsePacket(String targetName, CompoundTag pokedexTag) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPokedexResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_pokedex_response"));

    public static final StreamCodec<ByteBuf, ProfessorPokedexResponsePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ProfessorPokedexResponsePacket::targetName,
        ByteBufCodecs.COMPOUND_TAG, ProfessorPokedexResponsePacket::pokedexTag,
        ProfessorPokedexResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle (RuntimeDistCleaner).
    public static void handle(ProfessorPokedexResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientProfessorHelper.setPokedexData(packet.targetName(), packet.pokedexTag()));
    }
}
