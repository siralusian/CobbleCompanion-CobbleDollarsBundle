package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
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
 * Client -> Server: "Verschenken"-Auswahl im AdminOp-Editor-Overlay - überträgt ein Pokemon vom
 * gerade inspizierten Spieler (fromUuid) direkt zu einem anderen Spieler (toUuid). Braucht nur
 * AdminOp, funktioniert auch wenn der Absender offline ist (UUID-basierte Storage, siehe
 * AdminEditPokemonPacket-Kommentar) - der Empfänger muss aktuell noch online sein, da der
 * Auswahl-Dialog bisher nur online Spieler zur Auswahl listet.
 */
public record AdminGiftPokemonPacket(UUID fromUuid, UUID pokemonUuid, UUID toUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminGiftPokemonPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "admin_gift_pokemon"));

    public static final StreamCodec<ByteBuf, AdminGiftPokemonPacket> CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, AdminGiftPokemonPacket::fromUuid,
        UUIDUtil.STREAM_CODEC, AdminGiftPokemonPacket::pokemonUuid,
        UUIDUtil.STREAM_CODEC, AdminGiftPokemonPacket::toUuid,
        AdminGiftPokemonPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdminGiftPokemonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer admin)) return;
            if (!AdminPermissionManager.isAdminOp(admin.getUUID())) return;
            MinecraftServer server = admin.getServer();
            if (server == null) return;

            Pokemon pokemon = PlayerDataHelper.findPokemonByUuid(packet.fromUuid(), packet.pokemonUuid(), server.registryAccess());
            if (pokemon == null) return;

            PlayerPartyStore fromParty = PlayerDataHelper.getPartyStoreByUuid(packet.fromUuid(), server.registryAccess());
            PCStore fromPC = PlayerDataHelper.getPCStoreByUuid(packet.fromUuid(), server.registryAccess());
            boolean removed = fromParty != null && fromParty.remove(pokemon);
            if (!removed && fromPC != null) removed = fromPC.remove(pokemon);
            if (!removed) return;

            PlayerPartyStore toParty = PlayerDataHelper.getPartyStoreByUuid(packet.toUuid(), server.registryAccess());
            PCStore toPC = PlayerDataHelper.getPCStoreByUuid(packet.toUuid(), server.registryAccess());
            PartyPosition partyPos = toParty != null ? toParty.getFirstAvailablePosition() : null;
            if (partyPos != null) {
                toParty.set(partyPos, pokemon);
            } else {
                PCPosition pcPos = toPC != null ? toPC.getFirstAvailablePosition() : null;
                if (pcPos != null) {
                    toPC.set(pcPos, pokemon);
                } else {
                    // Kein Platz beim Empfänger - zurück zum Absender statt es zu verlieren.
                    PartyPosition backPos = fromParty != null ? fromParty.getFirstAvailablePosition() : null;
                    if (backPos != null) fromParty.set(backPos, pokemon);
                    else if (fromPC != null) {
                        PCPosition backPcPos = fromPC.getFirstAvailablePosition();
                        if (backPcPos != null) fromPC.set(backPcPos, pokemon);
                    }
                }
            }

            ProfessorPCRequestPacket.buildAndSend(admin, server, packet.fromUuid());
        });
    }
}
