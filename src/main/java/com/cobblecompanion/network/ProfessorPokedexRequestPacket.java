package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.AdminPermissionManager;
import com.cobblecompanion.data.FriendsManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
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

import java.util.UUID;

/**
 * Client -> Server: "Pokédex"-Knopf im Professor-Tab wurde geklickt - fordert den Pokédex-Stand
 * des ausgewählten Spielers an. Anders als Team/PC nimmt Cobblemons PokedexGUI keine Daten im
 * Konstruktor entgegen, sondern liest sie live aus einem client-seitigen Singleton
 * (CobblemonClient.INSTANCE.clientPokedexData), das an den LOKALEN Spieler gebunden ist - siehe
 * CompanionScreen.buildProfessorPokedexScreen() für den Austausch-Trick. Hier auf dem Server
 * wird der komplette PokedexManager über Cobblemons eigenen Codec (PokedexManager.Companion.CODEC)
 * nach NBT serialisiert, damit der Client daraus per selbem Codec wieder ein vollständiges
 * PokedexManager-Objekt (inkl. aller Formen/Aspekte) rekonstruieren kann.
 */
public record ProfessorPokedexRequestPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfessorPokedexRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "professor_pokedex_request"));

    public static final StreamCodec<ByteBuf, ProfessorPokedexRequestPacket> CODEC =
        UUIDUtil.STREAM_CODEC.map(ProfessorPokedexRequestPacket::new, ProfessorPokedexRequestPacket::targetUuid);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessorPokedexRequestPacket packet, IPayloadContext context) {
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

            String targetName = FriendsManager.getAllKnownPlayers().getOrDefault(packet.targetUuid(), packet.targetUuid().toString());
            PacketDistributor.sendToPlayer(player, new ProfessorPokedexResponsePacket(targetName, tag));
        });
    }
}
