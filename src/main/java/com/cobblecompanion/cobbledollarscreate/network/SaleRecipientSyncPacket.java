package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.client.data.ClientSaleRecipientHelper;
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
 * Server -> Client: aktueller Verkaufserlös-Empfänger-Modus des Netzwerks (siehe
 * CobbleMerchantPayoutManager) - wird zusammen mit den anderen Sync-Paketen beim Strg+Rechtsklick
 * verschickt (nur wenn freqId != null, siehe CreateStockTickerInteractionHandler). recipientName
 * leer = kein Empfänger (Modus NONE oder SINGLE ohne Auswahl). entityRecipients enthält bei Modus
 * VARIES pro verknüpfter Entity den aufgelösten Empfängernamen (leer = kein Empfänger für diese
 * Entity) - für die Anzeige im Verknüpfte-NPCs-Panel. Die rohe Empfänger-UUID wird bewusst nicht
 * mitgeschickt - der Client braucht sie nur beim Senden einer NEUEN Auswahl (dort kommt die UUID
 * direkt aus der KnownPlayers-Liste), nicht zur Anzeige.
 */
public record SaleRecipientSyncPacket(String mode, String recipientName,
        List<EntityRecipientEntry> entityRecipients) implements CustomPacketPayload {

    public record EntityRecipientEntry(UUID entityUuid, String recipientName) {
        public static final StreamCodec<ByteBuf, EntityRecipientEntry> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EntityRecipientEntry::entityUuid,
            ByteBufCodecs.STRING_UTF8, EntityRecipientEntry::recipientName,
            EntityRecipientEntry::new);
    }

    public static final CustomPacketPayload.Type<SaleRecipientSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "sale_recipient_sync"));

    public static final StreamCodec<ByteBuf, SaleRecipientSyncPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, SaleRecipientSyncPacket::mode,
        ByteBufCodecs.STRING_UTF8, SaleRecipientSyncPacket::recipientName,
        ByteBufCodecs.collection(ArrayList::new, EntityRecipientEntry.CODEC), SaleRecipientSyncPacket::entityRecipients,
        SaleRecipientSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaleRecipientSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientSaleRecipientHelper.set(packet));
    }
}
