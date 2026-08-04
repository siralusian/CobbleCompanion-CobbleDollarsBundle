package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblecompanion.data.PlayerDataHelper;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: "Living Dex"-Knopf im Professor-Tab wurde geklickt - fordert sowohl den
 * Pokédex-Stand (gleiche Technik wie ProfessorPokedexRequestPacket, damit Cobblemons echtes
 * PokedexGUI per Reflection eingebettet werden kann) als auch die aktuell im PC/Team besessenen
 * Spezies (für die Blatt-Icon-Overlays, analog ClientLivingDexHelper) des ausgewählten Spielers an.
 * Nutzt bewusst PlayerDataHelper.getAllPokemonByUuid (offline-fähig, wie bei AdminEditPokemonPacket),
 * NICHT das online-only PlayerDataHelper.getAllPokemon aus CobbleCompanion.sendLivingDexData.
 */
public record ProfessorLivingDexRequestPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorLivingDexRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_livingdex_request"));

    public static final StreamCodec<ByteBuf, ProfessorLivingDexRequestPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(ProfessorLivingDexRequestPacket::new, ProfessorLivingDexRequestPacket::targetUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessorLivingDexRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!AdminPermissionManager.isOp(player.getUUID())) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            PokedexManager mgr = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(packet.targetUuid());
            if (mgr == null) return;

            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            DataResult<Tag> encoded = PokedexManager.Companion.getCODEC().encodeStart(ops, mgr);
            Tag resultTag = encoded.result().orElse(new CompoundTag());
            CompoundTag tag = resultTag instanceof CompoundTag ct ? ct : new CompoundTag();

            List<String> species = new ArrayList<>();
            for (Pokemon p : PlayerDataHelper.getAllPokemonByUuid(packet.targetUuid(), server.registryAccess())) {
                String name = p.getSpecies().getName().toLowerCase();
                if (!species.contains(name)) species.add(name);
            }

            String targetName = FriendsManager.getAllKnownPlayers().getOrDefault(packet.targetUuid(), packet.targetUuid().toString());
            PacketDistributor.sendToPlayer(player, new ProfessorLivingDexResponsePacket(targetName, tag, species));
        });
    }
}
