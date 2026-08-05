package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.ContentObserverBridge;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.integrations.ModAvailability;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client -> Server: Nutzer-Vorgabe - der Schlauer-Beobachter-Editor soll den Ankaufs-/
 * Verkaufspreis des per Lagerverbinder/-ticker angeschlossenen Netzwerks nicht nur ANZEIGEN,
 * sondern auch DIREKT AUS SEINEM INTERFACE HERAUS ändern können (bisher ging das nur am
 * Lagerticker selbst, siehe CreateStockTickerPricesUpdatePacket). Wirkt sich auf die GESAMTE
 * Preisliste des Netzwerks aus (nicht nur diesen einen Beobachter) - Auswirkungspunkt ist
 * ContentObserverConfigScreen "Hinzufügen/Aktualisieren", nicht die "Nutze Ankaufspreis"/"Nutze
 * Verkaufspreis"-Buttons (die kopieren nur lokal in die Betrag-Zeile, siehe Klassenkommentar dort).
 *
 * ankaufspreis/verkaufspreis &lt;= 0 löscht den jeweiligen Preis wieder aus der Liste (wie am
 * Lagerticker selbst: 0/negativ = "kein Preis konfiguriert", siehe CentralItemPriceManager).
 */
public record ContentObserverNetworkPriceUpdatePacket(BlockPos pos, String itemId, long ankaufspreis, long verkaufspreis)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContentObserverNetworkPriceUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "content_observer_network_price_update"));

    public static final StreamCodec<ByteBuf, ContentObserverNetworkPriceUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ContentObserverNetworkPriceUpdatePacket::pos,
        ByteBufCodecs.STRING_UTF8, ContentObserverNetworkPriceUpdatePacket::itemId,
        ByteBufCodecs.VAR_LONG, ContentObserverNetworkPriceUpdatePacket::ankaufspreis,
        ByteBufCodecs.VAR_LONG, ContentObserverNetworkPriceUpdatePacket::verkaufspreis,
        ContentObserverNetworkPriceUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContentObserverNetworkPriceUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;
            if (!ModAvailability.isCreateAvailable()) return;
            if (!ContentObserverBridge.isContentObserver(player.level(), packet.pos())) return;

            String itemId = packet.itemId();
            ResourceLocation itemRl = ResourceLocation.tryParse(itemId);
            if (itemRl == null || !BuiltInRegistries.ITEM.containsKey(itemRl)) return;

            UUID freqId = ContentObserverBridge.findLinkedNetworkFreqId(player.level(), packet.pos());
            if (freqId == null) return; // kein Lagerverbinder/-ticker angeschlossen

            String listId = CentralItemPriceManager.getListIdForNetwork(freqId);
            String name = CentralItemPriceManager.getListName(listId);
            Map<String, Long> sellPrices = new LinkedHashMap<>(CentralItemPriceManager.getSellPrices(listId));
            Map<String, Long> buyPrices = new LinkedHashMap<>(CentralItemPriceManager.getBuyPrices(listId));
            Set<String> sellDisabled = new LinkedHashSet<>(CentralItemPriceManager.getSellDisabled(listId));
            Set<String> buyDisabled = new LinkedHashSet<>(CentralItemPriceManager.getBuyDisabled(listId));
            Map<String, String> categories = new LinkedHashMap<>(CentralItemPriceManager.getCategories(listId));

            if (packet.ankaufspreis() > 0) sellPrices.put(itemId, packet.ankaufspreis());
            else sellPrices.remove(itemId);
            if (packet.verkaufspreis() > 0) buyPrices.put(itemId, packet.verkaufspreis());
            else buyPrices.remove(itemId);

            CentralItemPriceManager.setListContents(listId, name, sellPrices, buyPrices, sellDisabled, buyDisabled, categories);
        });
    }
}
