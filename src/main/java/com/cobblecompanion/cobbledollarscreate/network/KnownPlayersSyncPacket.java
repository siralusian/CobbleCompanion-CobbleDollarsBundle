package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientKnownPlayersHelper;
import com.cobblecompanion.cobbledollarscreate.data.KnownPlayersHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: alle dem Server bekannten Spieler (siehe KnownPlayersHelper), für den
 * durchsuchbaren Spieler-Picker im Verkaufserlöse-Empfänger-UI - wird zusammen mit den anderen
 * Sync-Paketen beim Strg+Rechtsklick auf einen Lagerticker verschickt.
 */
public record KnownPlayersSyncPacket(List<Entry> players) implements CustomPacketPayload {

    public record Entry(String name, UUID uuid) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Entry::name,
            UUIDUtil.STREAM_CODEC, Entry::uuid,
            Entry::new);
    }

    public static final CustomPacketPayload.Type<KnownPlayersSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "known_players_sync"));

    public static final StreamCodec<ByteBuf, KnownPlayersSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, Entry.CODEC), KnownPlayersSyncPacket::players,
        KnownPlayersSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KnownPlayersSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientKnownPlayersHelper.set(packet.players()));
    }

    public static List<Entry> from(List<KnownPlayersHelper.NameUuid> source) {
        List<Entry> result = new ArrayList<>();
        for (KnownPlayersHelper.NameUuid nameUuid : source) {
            result.add(new Entry(nameUuid.name(), nameUuid.uuid()));
        }
        return result;
    }
}
