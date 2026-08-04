package com.cobblecompanion.cobbledollarscreate.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine einzelne Preisliste (siehe CentralItemPriceManager) für die Übertragung im Preis-Editor
 * (Strg+Rechtsklick auf einen Lagerticker) - entries im Format
 * "itemId=verkaufspreis=ankaufspreis=verkaufAn=ankaufAn=kategorie" (verkaufAn/ankaufAn: "1"/"0",
 * kategorie kann leer sein). Server sendet beim Öffnen ALLE Listen komplett (siehe
 * CreateStockTickerPricesSyncPacket), damit das Dropdown im Editor rein client-seitig zwischen
 * ihnen wechseln kann, ohne Server-Rückfragen.
 */
public record PriceListPayload(String id, String name, List<String> entries) {

    public static final StreamCodec<ByteBuf, PriceListPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, PriceListPayload::id,
        ByteBufCodecs.STRING_UTF8, PriceListPayload::name,
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), PriceListPayload::entries,
        PriceListPayload::new);
}
