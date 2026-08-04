package com.cobblecompanion.cobbledollarscreate;

import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Löst die Create-Netzwerk-freqId bzw. die zugewiesene Preisliste für einen verknüpften
 * Lagerticker auf - die CobbleDollars-spezifische Ergänzung zu Basis' gleichnamigem
 * com.cobblecompanion.integrations.create.CreateStockTickerBridge (isStockTicker/
 * getAvailableItemIds, reine Create-Typprüfungen ohne CobbleDollars-Bezug, bleiben dort, da auch
 * von der noch nicht ausgelagerten CustomNPCs-Integration direkt gebraucht).
 */
public final class CreateStockTickerBridge {

    private CreateStockTickerBridge() {}

    /**
     * Löst aus einem CobbleMerchantSellManager.Target (bereits per Ctrl+Rechtsklick verknüpfter
     * Lagerticker) die Create-Netzwerk-freqId auf - null, wenn Dimension/Ticker/Netzwerk nicht mehr
     * existiert. Zentrale Stelle statt Dimension/Pos-Auflösung in jeder Mixin-Klasse zu duplizieren
     * (siehe PlayerExtensionKtOpenShopMixin, SellHandlerMixin).
     */
    public static UUID resolveNetworkFreqId(MinecraftServer server, CobbleMerchantSellManager.Target tickerTarget) {
        if (tickerTarget == null) return null;
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tickerTarget.dimension()));
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) return null;
        BlockPos pos = new BlockPos(tickerTarget.x(), tickerTarget.y(), tickerTarget.z());
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker) || ticker.behaviour == null) return null;
        return ticker.behaviour.freqId;
    }

    /** Wie resolveNetworkFreqId(), aber direkt bis zur zugewiesenen Preisliste (siehe CentralItemPriceManager) - Fallback "default" wenn nicht verknüpft/aufgelöst. */
    public static String resolveListIdForMerchant(MinecraftServer server, UUID merchantUuid) {
        CobbleMerchantSellManager.Target tickerTarget = CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid);
        return CentralItemPriceManager.getListIdForNetwork(resolveNetworkFreqId(server, tickerTarget));
    }
}
