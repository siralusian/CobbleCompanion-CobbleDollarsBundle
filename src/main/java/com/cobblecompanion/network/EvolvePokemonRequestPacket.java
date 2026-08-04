package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.TodoHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: Entwickeln-Button im ToDo-Tab wurde geklickt. Löst die Entwicklung von
 * pokemonUuid nach toSpeciesId aus (siehe TodoHelper.tryEvolve) und schickt bei Erfolg direkt
 * eine aktualisierte ToDo-Liste zurück, damit der erledigte Eintrag sofort verschwindet.
 * toAspects (,-getrennt, kann leer sein) identifiziert die genaue Ziel-Form - manche Pokemon
 * (z.B. Hokumil/Milcery) können gleichzeitig zu mehreren Formen derselben Zielspezies bereit
 * sein, siehe TargetKey in TodoHelper.getTodoEntries().
 * sendOut = true ruft das Pokémon vor der Entwicklung aus (Setting "Send out before evolving").
 */
public record EvolvePokemonRequestPacket(UUID pokemonUuid, ResourceLocation toSpeciesId, String toAspects, boolean sendOut)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EvolvePokemonRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "evolve_pokemon_request"));

    public static final StreamCodec<ByteBuf, EvolvePokemonRequestPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        EvolvePokemonRequestPacket::pokemonUuid,
        ResourceLocation.STREAM_CODEC,
        EvolvePokemonRequestPacket::toSpeciesId,
        ByteBufCodecs.STRING_UTF8,
        EvolvePokemonRequestPacket::toAspects,
        ByteBufCodecs.BOOL,
        EvolvePokemonRequestPacket::sendOut,
        EvolvePokemonRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EvolvePokemonRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TodoHelper.tryEvolve(player, packet.pokemonUuid(), packet.toSpeciesId(), packet.toAspects(), packet.sendOut());
                PacketDistributor.sendToPlayer(player, new TodoResponsePacket(TodoHelper.getTodoEntries(player)));
            }
        });
    }
}
