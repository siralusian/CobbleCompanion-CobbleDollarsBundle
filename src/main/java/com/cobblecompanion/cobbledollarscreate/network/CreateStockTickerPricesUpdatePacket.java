package com.cobblecompanion.cobbledollarscreate.network;

import com.cobblecompanion.cobbledollarscreate.CobbleCompanionDollarsCreate;
import com.cobblecompanion.cobbledollarscreate.CobbleDollarsBankBridge;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CreateStockTickerPriceManager;
import com.cobblecompanion.integrations.ModAvailability;
import com.cobblecompanion.integrations.create.CreateStockTickerBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Client -> Server: Preis-Editor "Speichern"-Button (siehe StockTickerPriceScreen). Nutzer-Vorgabe:
 * mehrere Preislisten statt einer einzigen globalen - `lists` enthält den KOMPLETTEN client-seitigen
 * Stand aller Listen (der Client hatte beim Öffnen bereits alle bekommen, siehe
 * CreateStockTickerPricesSyncPacket); jede im Server-Bestand vorhandene Liste, die hier NICHT mehr
 * auftaucht, wurde client-seitig per "-"-Button gelöscht (außer "default", die kann nicht gelöscht
 * werden). `currentListId`: die diesem Ticker-Netzwerk ab jetzt zugewiesene Liste.
 *
 * entries im Format "itemId=verkaufspreis=ankaufspreis=verkauf=ankauf=kategorie" (verkauf/ankauf:
 * "1"/"0", kategorie kann leer sein) - ungültige Zeilen (kaputte Zahl, leer) werden beim Parsen
 * still übersprungen statt den ganzen Speichervorgang abzubrechen.
 */
public record CreateStockTickerPricesUpdatePacket(BlockPos pos, boolean enabled, String currentListId, List<PriceListPayload> lists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateStockTickerPricesUpdatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
            CobbleCompanionDollarsCreate.MOD_ID, "create_stockticker_prices_update"));

    public static final StreamCodec<ByteBuf, CreateStockTickerPricesUpdatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, CreateStockTickerPricesUpdatePacket::pos,
        ByteBufCodecs.BOOL, CreateStockTickerPricesUpdatePacket::enabled,
        ByteBufCodecs.STRING_UTF8, CreateStockTickerPricesUpdatePacket::currentListId,
        ByteBufCodecs.collection(ArrayList::new, PriceListPayload.CODEC), CreateStockTickerPricesUpdatePacket::lists,
        CreateStockTickerPricesUpdatePacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreateStockTickerPricesUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Nutzer-Vorgabe: die Preislisten dürfen NUR von echten Minecraft-OPs bearbeitet werden,
            // bewusst NICHT über das eigene AdminPermissionManager-System.
            if (!player.hasPermissions(2)) {
                CobbleCompanionDollarsCreate.LOGGER.info("[CC] Preisliste-Speichern von {} verweigert: kein Minecraft-OP", player.getName().getString());
                return;
            }
            if (!ModAvailability.isCreateAvailable()) return;
            if (!CreateStockTickerBridge.isStockTicker(player.level(), packet.pos())) {
                CobbleCompanionDollarsCreate.LOGGER.info("[CC] Lagerticker-Preis-Speichern bei {} fehlgeschlagen: kein Lagerticker mehr dort", packet.pos());
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.stockticker_save_failed", "Save failed - no stock ticker at this position anymore."));
                return;
            }

            java.util.Set<String> incomingIds = new java.util.HashSet<>();
            List<String> unknownItems = new ArrayList<>();
            int totalSellCount = 0;
            for (PriceListPayload listPayload : packet.lists()) {
                incomingIds.add(listPayload.id());
                LinkedHashMap<String, Long> sellPrices = new LinkedHashMap<>();
                LinkedHashMap<String, Long> buyPrices = new LinkedHashMap<>();
                java.util.LinkedHashSet<String> sellDisabled = new java.util.LinkedHashSet<>();
                java.util.LinkedHashSet<String> buyDisabled = new java.util.LinkedHashSet<>();
                LinkedHashMap<String, String> categories = new LinkedHashMap<>();
                for (String line : listPayload.entries()) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String itemId = line.substring(0, eq).trim();
                    String[] rest = line.substring(eq + 1).split("=", -1);
                    if (itemId.isEmpty() || rest.length == 0) continue;
                    // Bequemlichkeit: "diamond=50" wird wie "minecraft:diamond=50" behandelt, statt
                    // eine Zeile ohne Namespace-Präfix einfach unbemerkt zu verwerfen.
                    if (!itemId.contains(":")) itemId = "minecraft:" + itemId;
                    net.minecraft.resources.ResourceLocation itemRl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
                    if (itemRl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemRl)) {
                        unknownItems.add(itemId);
                        continue;
                    }
                    long sellPrice;
                    try {
                        sellPrice = Long.parseLong(rest[0].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    long buyPrice = sellPrice;
                    if (rest.length >= 2) {
                        try {
                            buyPrice = Long.parseLong(rest[1].trim());
                        } catch (NumberFormatException e) {
                            buyPrice = sellPrice;
                        }
                    }
                    boolean sellEnabled = rest.length < 3 || !"0".equals(rest[2].trim());
                    boolean buyEnabled = rest.length < 4 || !"0".equals(rest[3].trim());
                    // Kategorie ist das letzte Feld - kann theoretisch selbst "=" enthalten, deshalb
                    // Rest ab Index 4 wieder zusammenfügen statt nur rest[4] zu nehmen.
                    String category = rest.length >= 5 ? String.join("=", java.util.Arrays.copyOfRange(rest, 4, rest.length)).trim() : "";
                    if (sellPrice > 0) sellPrices.put(itemId, sellPrice);
                    if (buyPrice > 0) buyPrices.put(itemId, buyPrice);
                    if (!sellEnabled) sellDisabled.add(itemId);
                    if (!buyEnabled) buyDisabled.add(itemId);
                    if (!category.isEmpty()) categories.put(itemId, category);
                }
                CentralItemPriceManager.setListContents(listPayload.id(), listPayload.name(), sellPrices, buyPrices, sellDisabled, buyDisabled, categories);
                totalSellCount += sellPrices.size();
            }

            // Listen, die server-seitig existieren aber im Speicher-Stand fehlen, wurden client-
            // seitig per "-"-Button gelöscht (deleteList() schützt "default" ohnehin selbst).
            for (CentralItemPriceManager.PriceListInfo info : CentralItemPriceManager.getAllLists()) {
                if (!incomingIds.contains(info.id())) {
                    CentralItemPriceManager.deleteList(info.id());
                }
            }

            // Nutzer-Vorgabe: Bezahlpflicht gilt pro Netzwerk (freqId), nicht mehr pro Ticker-
            // Block - ein Umschalten hier wirkt sich auf alle Ticker desselben Netzwerks aus. Die
            // Netzwerk-Preislisten-Zuweisung ebenso.
            if (player.level().getBlockEntity(packet.pos()) instanceof com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity ticker
                    && ticker.behaviour != null) {
                CreateStockTickerPriceManager.setEnabled(ticker.behaviour.freqId, packet.enabled());
                CentralItemPriceManager.setListIdForNetwork(ticker.behaviour.freqId, packet.currentListId());
            }
            CobbleCompanionDollarsCreate.LOGGER.info("[CC] Preislisten gespeichert ({} Liste(n), {} Verkaufs-Einträge insgesamt, unbekannte Items: {}), Ticker bei {} aktiviert={}",
                packet.lists().size(), totalSellCount, unknownItems, packet.pos(), packet.enabled());
            // Spiegelt die Verkaufspreise ALLER Listen zusätzlich in CobbleDollars' eigene globale
            // Bank-Config, damit der client-seitige "Verkaufen"-Button für JEDES bepreiste Item
            // anklickbar bleibt (siehe CobbleDollarsBankBridge-Klassenkommentar) - reine UI-
            // Freischaltung, echte Preisberechnung bleibt pro-Netzwerk-Liste in SellHandlerMixin.
            if (ModAvailability.isCobbleDollarsAvailable()) {
                CobbleDollarsBankBridge.syncGlobalBank(CentralItemPriceManager.getUnionSellPrices());
            }

            player.sendSystemMessage(Component.translatableWithFallback(
                "cobblecompanion.msg.stockticker_saved",
                "Saved (required: %s, %s list(s), %s sell entries total).", String.valueOf(packet.enabled()),
                String.valueOf(packet.lists().size()), String.valueOf(totalSellCount)));
            if (!unknownItems.isEmpty()) {
                player.sendSystemMessage(Component.translatableWithFallback(
                    "cobblecompanion.msg.stockticker_unknown_items", "Unknown item IDs ignored: %s",
                    String.join(", ", unknownItems)));
            }
        });
    }
}
