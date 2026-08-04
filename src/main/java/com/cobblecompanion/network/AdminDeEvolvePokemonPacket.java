package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: "Zurückentwickeln"-Button im AdminOp-Editor-Overlay. Cobblemon hat dafür keine
 * eigene API - stattdessen wird FormData.getPreEvolution() genutzt (liefert Vorgänger-Spezies+Form,
 * falls vorhanden) und per PokemonProperties.apply() direkt gesetzt. Keine Entwicklungsanimation
 * (anders als forceEvolve) - ist eine reine Datenkorrektur, kein normaler Spielvorgang. Pokemon
 * ohne Vorentwicklung (Basis-Formen) werden ignoriert (Button ist dafür clientseitig ausgegraut).
 */
public record AdminDeEvolvePokemonPacket(UUID targetUuid, UUID pokemonUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminDeEvolvePokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_deevolve_pokemon"));

    public static final StreamCodec<ByteBuf, AdminDeEvolvePokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminDeEvolvePokemonPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminDeEvolvePokemonPacket::pokemonUuid,
        AdminDeEvolvePokemonPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminDeEvolvePokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            FormData form = pokemon.getForm();
            PreEvolution pre = form != null ? form.getPreEvolution() : null;
            if (pre == null) return;

            try {
                PokemonProperties props = new PokemonProperties();
                props.setSpecies(pre.getSpecies().getName());
                FormData preForm = pre.getForm();
                if (preForm != null) props.setForm(preForm.getName());
                props.apply(pokemon);
            } catch (Exception e) {
                CobbleCompanion.LOGGER.error("[CC] De-evolve failed for " + packet.pokemonUuid(), e);
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, packet.targetUuid());
        });
    }
}
