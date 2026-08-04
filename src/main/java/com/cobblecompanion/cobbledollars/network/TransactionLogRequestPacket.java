package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.data.TransactionLogManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Client -> Server: Wallet-Tab wurde geöffnet - fordert den Transaktions-Verlauf an. */
public record TransactionLogRequestPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransactionLogRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "transaction_log_request"));

    public static final StreamCodec<ByteBuf, TransactionLogRequestPacket> CODEC =
        StreamCodec.unit(new TransactionLogRequestPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransactionLogRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            List<String> lines = new ArrayList<>();
            for (TransactionLogManager.Entry entry : TransactionLogManager.getEntries(player.getUUID())) {
                lines.add(entry.type() + "|" + entry.amount() + "|" + (entry.counterpart() == null ? "" : entry.counterpart()));
            }
            PacketDistributor.sendToPlayer(player, new TransactionLogSyncPacket(lines));
        });
    }
}
