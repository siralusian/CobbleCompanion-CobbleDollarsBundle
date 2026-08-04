package com.cobblecompanion.cobbledollarscustomnpcs;

import com.cobblecompanion.cobbledollarscustomnpcs.network.CustomNpcTraderStockSyncPacket;
import com.cobblecompanion.data.CustomNpcTraderLinkManager;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.containers.ContainerNPCTrader;

import java.util.ArrayList;
import java.util.List;

/**
 * Nutzer-Vorgabe: "neben dem Kauf-Item anzeigen wieviel davon im Lager verfügbar ist" - sendet beim
 * Öffnen des CustomNPCs-Bestell-GUI (ContainerNPCTrader) einmalig den verfügbaren Netzwerk-
 * Lagerbestand pro Verkaufsslot (siehe CustomNpcTraderStockOverlay für die Anzeige). Nur relevant,
 * wenn ModAvailability.isCreateAvailable() UND isCustomNpcsAvailable() (siehe
 * CobbleCompanionDollarsCustomNPCs.onServerStarting) - diese Klasse importiert Typen aus beiden
 * Mods direkt.
 */
public class CustomNpcTraderStockSyncHandler {

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof ContainerNPCTrader trader)) return;
        if (trader.role == null || trader.role.npc == null) return;

        CustomNpcTraderLinkManager.Target tickerTarget = CustomNpcTraderLinkManager.getTickerLink(trader.role.npc.getUUID());
        List<Integer> available = new ArrayList<>();
        StockTickerBlockEntity ticker = tickerTarget == null ? null : resolveTicker(player, tickerTarget);

        for (int i = 0; i < 18; i++) {
            ItemStack sold = trader.role.inventorySold.getItem(i);
            if (sold.isEmpty()) {
                available.add(-1);
                continue;
            }
            if (ticker == null || ticker.behaviour == null) {
                available.add(0); // Nicht (mehr) verknüpft -> kann nichts verkaufen.
                continue;
            }
            available.add(ticker.getRecentSummary().getCountOf(sold));
        }

        PacketDistributor.sendToPlayer(player, new CustomNpcTraderStockSyncPacket(available));
    }

    private static StockTickerBlockEntity resolveTicker(ServerPlayer player, CustomNpcTraderLinkManager.Target target) {
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(target.dimension()));
        ServerLevel level = player.getServer().getLevel(dimensionKey);
        if (level == null) return null;
        return level.getBlockEntity(target.pos()) instanceof StockTickerBlockEntity ticker ? ticker : null;
    }
}
