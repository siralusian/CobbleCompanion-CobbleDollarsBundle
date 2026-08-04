package com.cobblecompanion.cobbledollarscreate;

import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Kleine Helfer-Fassade, damit network/-Pakete nicht selbst Create-Klassen importieren müssen -
 * gleiches Muster wie CreateStockTickerBridge.
 */
public final class ContentObserverBridge {

    private ContentObserverBridge() {}

    public static boolean isContentObserver(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SmartObserverBlockEntity;
    }

    /**
     * Setzt den Filter DIREKT auf der echten FilteringBehaviour des Blocks (Nutzer-Vorgabe: das
     * von AdminOp eingestellte Item soll ganz normal in der Beobachter-eigenen Anzeige auftauchen,
     * nicht nur in unserer eigenen Konfiguration existieren) - null/leer löscht den Filter wieder.
     */
    public static void setFilter(Level level, BlockPos pos, Item item) {
        if (!(level.getBlockEntity(pos) instanceof SmartObserverBlockEntity blockEntity)) return;
        FilteringBehaviour filtering = blockEntity.getBehaviour(FilteringBehaviour.TYPE);
        if (filtering == null) return;
        filtering.setFilter(item == null ? ItemStack.EMPTY : new ItemStack(item));
    }
}
