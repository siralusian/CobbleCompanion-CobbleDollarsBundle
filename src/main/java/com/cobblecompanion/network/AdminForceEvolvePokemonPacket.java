package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblecompanion.data.TodoHelper;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: "Entwickeln"-Button im AdminOp-Editor-Overlay - lässt den Admin aus ALLEN
 * potentiell möglichen Entwicklungen des Pokemons wählen (siehe openAdminEvolvePicker() im
 * CompanionScreen) und wendet sie über Evolution.forceEvolve() an, OHNE Cobblemons normale
 * Voraussetzungen (Level/Item/Freundschaft/etc.) zu prüfen - das ist der ganze Sinn dieses
 * AdminOp-Sonderwegs. toSpeciesId+toAspects identifizieren die gewählte Evolution exakt (manche
 * Pokemon wie Hokumil/Milcery haben mehrere gleichzeitig mögliche Zielformen derselben Spezies),
 * gleiche Matching-Logik wie TodoHelper.tryEvolve() (dort ohne die requirements-Prüfung).
 */
public record AdminForceEvolvePokemonPacket(UUID targetUuid, UUID pokemonUuid, String toSpeciesId, String toAspects)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminForceEvolvePokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_force_evolve_pokemon"));

    public static final StreamCodec<ByteBuf, AdminForceEvolvePokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminForceEvolvePokemonPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminForceEvolvePokemonPacket::pokemonUuid,
        ByteBufCodecs.STRING_UTF8, AdminForceEvolvePokemonPacket::toSpeciesId,
        ByteBufCodecs.STRING_UTF8, AdminForceEvolvePokemonPacket::toAspects,
        AdminForceEvolvePokemonPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminForceEvolvePokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            for (Evolution evo : pokemon.getEvolutions()) {
                ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
                if (toId == null || !toId.toString().equals(packet.toSpeciesId())) continue;
                if (!TodoHelper.getResultAspectsString(evo).equals(packet.toAspects())) continue;
                evo.forceEvolve(pokemon);
                break;
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, packet.targetUuid());
        });
    }
}
