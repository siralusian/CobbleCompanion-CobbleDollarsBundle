package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
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
 * Client -> Server: AdminOp-Editor-Overlay im Professor-Tab wurde mit "Freilassen" bestätigt.
 * Braucht AdminOp - funktioniert auch bei offline Zielspielern (siehe AdminEditPokemonPacket-
 * Kommentar für die UUID-basierte Storage-Begründung).
 */
public record AdminReleasePokemonPacket(UUID targetUuid, UUID pokemonUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminReleasePokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_release_pokemon"));

    public static final StreamCodec<ByteBuf, AdminReleasePokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminReleasePokemonPacket::targetUuid,
        UUIDUtil.STREAM_CODEC, AdminReleasePokemonPacket::pokemonUuid,
        AdminReleasePokemonPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminReleasePokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.targetUuid(), packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            PlayerPartyStore party = PlayerDataHelper.getPartyStoreByUuid(packet.targetUuid(), server.registryAccess());
            boolean removed = party != null && party.remove(pokemon);
            if (!removed) {
                PCStore pc = PlayerDataHelper.getPCStoreByUuid(packet.targetUuid(), server.registryAccess());
                if (pc != null) pc.remove(pokemon);
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, packet.targetUuid());
        });
    }
}
