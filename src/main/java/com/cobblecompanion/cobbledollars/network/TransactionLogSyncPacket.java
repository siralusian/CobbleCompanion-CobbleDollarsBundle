package com.cobblecompanion.cobbledollars.network;

import com.cobblecompanion.cobbledollars.CobbleCompanionDollars;
import com.cobblecompanion.cobbledollars.client.ClientTransactionLogHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Transaktions-Verlauf (Wallet-Tab, linke Spalte). entries im Format
 * "type|betrag|gegenpartei" (gegenpartei leer bei Merchant-/Ticker-/Creative-Einträgen), neueste
 * zuerst - siehe com.cobblecompanion.data.TransactionLogManager in CobbleCompanion: Basis.
 */
public record TransactionLogSyncPacket(List<String> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TransactionLogSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollars.MOD_ID, "transaction_log_sync"));

    public static final StreamCodec<ByteBuf, TransactionLogSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), TransactionLogSyncPacket::entries,
        TransactionLogSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Kein @OnlyIn(Dist.CLIENT): siehe LivingDexPacket.handle in CobbleCompanion: Basis (RuntimeDistCleaner).
    public static void handle(TransactionLogSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientTransactionLogHelper.setEntries(packet.entries()));
    }
}
