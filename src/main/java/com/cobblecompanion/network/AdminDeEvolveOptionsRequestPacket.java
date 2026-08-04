package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.pokemon.FormData;
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

import java.util.UUID;

/**
 * Client -> Server: "Zurückentwickeln"-Button im AdminOp-Editor-Overlay - fragt (analog zu
 * AdminEvolveOptionsRequestPacket) die ECHTE serverseitige Pokemon-Instanz nach ihrer
 * Vorentwicklung (FormData.getPreEvolution()), statt sich auf das clientseitig per loadFromNBT()
 * rekonstruierte Pokemon zu verlassen (dieselbe Unzuverlässigkeit wie bei den normalen
 * Entwicklungen - siehe AdminEvolveOptionsRequestPacket-Kommentar). Zeigt danach ein
 * Auswahlfenster mit maximal einem Eintrag (jedes Pokemon hat höchstens eine direkte
 * Vorentwicklung), analog zum Entwickeln-Fenster - Konsistenz und Bestätigung vor dem Klick.
 */
public record AdminDeEvolveOptionsRequestPacket(UUID targetUuid, UUID pokemonUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminDeEvolveOptionsRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_deevolve_options_request"));

    public static final StreamCodec<ByteBuf, AdminDeEvolveOptionsRequestPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminDeEvolveOptionsRequestPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminDeEvolveOptionsRequestPacket::pokemonUuid,
        AdminDeEvolveOptionsRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminDeEvolveOptionsRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            String toSpeciesId = "";
            if (pokemon != null) {
                FormData form = pokemon.getForm();
                PreEvolution pre = form != null ? form.getPreEvolution() : null;
                if (pre != null && pre.getSpecies() != null) {
                    toSpeciesId = pre.getSpecies().getResourceIdentifier().toString();
                }
            }
            PacketDistributor.sendToPlayer(admin, new AdminDeEvolveOptionsResponsePacket(toSpeciesId));
        });
    }
}
