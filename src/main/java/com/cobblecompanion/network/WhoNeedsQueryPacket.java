package com.cobblecompanion.network;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.data.ServerDataHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: Anfrage, welche Spieler eine bestimmte Spezies noch brauchen
 * (Who-Needs-Tab). livingDexMode steuert, ob bereits gefangene-aber-nicht-besessene
 * Spieler mit aufgenommen werden. onlyFriends filtert auf Freunde, friendsFirst sortiert
 * Freunde nach vorn (siehe ServerDataHelper.whoNeedsDetailed).
 */
public record WhoNeedsQueryPacket(String speciesName, boolean livingDexMode, boolean onlyFriends, boolean friendsFirst)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WhoNeedsQueryPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanion.MOD_ID, "who_needs_query"));

    public static final StreamCodec<ByteBuf, WhoNeedsQueryPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, WhoNeedsQueryPacket::speciesName,
        ByteBufCodecs.BOOL, WhoNeedsQueryPacket::livingDexMode,
        ByteBufCodecs.BOOL, WhoNeedsQueryPacket::onlyFriends,
        ByteBufCodecs.BOOL, WhoNeedsQueryPacket::friendsFirst,
        WhoNeedsQueryPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WhoNeedsQueryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                List<String> entries = new ArrayList<>();
                for (ServerDataHelper.PlayerNeedEntry e :
                        ServerDataHelper.whoNeedsDetailed(player.getServer(), player.getUUID(), packet.speciesName(),
                            packet.onlyFriends(), packet.friendsFirst())) {
                    entries.add(e.name + "|" + e.uuid + "|" + e.online + "|" + e.needsPokedex + "|" + e.isFriend);
                }
                PacketDistributor.sendToPlayer(player, new WhoNeedsResultPacket(entries));
            }
        });
    }
}
