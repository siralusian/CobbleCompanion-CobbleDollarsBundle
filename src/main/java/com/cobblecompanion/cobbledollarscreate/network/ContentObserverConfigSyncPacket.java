package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientContentObserverHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Strg+Rechtsklick auf einen Schlauen Beobachter (create:content_observer, siehe
 * ContentObserverInteractionHandler) - öffnet den eigenen Konfigurations-
 * Editor mit dem aktuellen Stand. itemId/targetPlayerName leer = noch nicht konfiguriert.
 */
public record ContentObserverConfigSyncPacket(BlockPos pos, String itemId, String targetPlayerName, long amountPerItem)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContentObserverConfigSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_config_sync"));

    public static final StreamCodec<ByteBuf, ContentObserverConfigSyncPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ContentObserverConfigSyncPacket::pos,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigSyncPacket::itemId,
        ByteBufCodecs.STRING_UTF8, ContentObserverConfigSyncPacket::targetPlayerName,
        ByteBufCodecs.VAR_LONG, ContentObserverConfigSyncPacket::amountPerItem,
        ContentObserverConfigSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverConfigSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientContentObserverHelper.setPending(
            packet.pos(), packet.itemId(), packet.targetPlayerName(), packet.amountPerItem()));
    }
}
