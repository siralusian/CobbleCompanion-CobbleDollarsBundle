package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblecompanion.data.TodoHelper;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: "Entwickeln"-Button im AdminOp-Editor-Overlay fragt hier die Liste ALLER
 * potentiell möglichen Entwicklungen ab, statt sie clientseitig aus dem per loadFromNBT()
 * rekonstruierten Pokemon-Objekt zu bauen - genau dieses rekonstruierte Objekt liefert bei
 * getEvolutions() laut Live-Test unzuverlässig eine leere Liste (vermutlich weil loadFromNBT()
 * nicht denselben vollständigen Initialisierungspfad durchläuft wie eine echte, aus der Storage
 * geladene Pokemon-Instanz). Die ECHTE serverseitige Instanz (via PlayerDataHelper.findPokemonByUuid,
 * dieselbe Quelle, die auch AdminForceEvolvePokemonPacket nutzt) liefert die Liste zuverlässig.
 */
public record AdminEvolveOptionsRequestPacket(UUID targetUuid, UUID pokemonUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminEvolveOptionsRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_evolve_options_request"));

    public static final StreamCodec<ByteBuf, AdminEvolveOptionsRequestPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminEvolveOptionsRequestPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminEvolveOptionsRequestPacket::pokemonUuid,
        AdminEvolveOptionsRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminEvolveOptionsRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            List<String> options = new ArrayList<>();
            if (pokemon != null) {
                for (Evolution evo : pokemon.getEvolutions()) {
                    ResourceLocation toId = TodoHelper.resolveSpeciesId(evo.getResult().getSpecies());
                    if (toId == null) continue;
                    String aspects = TodoHelper.getResultAspectsString(evo);
                    options.add(toId + "|" + aspects);
                }
            }
            PacketDistributor.sendToPlayer(admin, new AdminEvolveOptionsResponsePacket(options));
        });
    }
}
