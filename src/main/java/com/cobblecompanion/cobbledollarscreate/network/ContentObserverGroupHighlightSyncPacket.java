package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientContentObserverHighlightHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: Antwort auf {@link ContentObserverGroupHighlightRequestPacket} - siehe dessen Klassenkommentar. */
public record ContentObserverGroupHighlightSyncPacket(List<BlockPos> positions) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContentObserverGroupHighlightSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_group_highlight_sync"));

    public static final StreamCodec<ByteBuf, ContentObserverGroupHighlightSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, BlockPos.STREAM_CODEC), ContentObserverGroupHighlightSyncPacket::positions,
        ContentObserverGroupHighlightSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverGroupHighlightSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientContentObserverHighlightHelper.setPositions(packet.positions()));
    }
}
