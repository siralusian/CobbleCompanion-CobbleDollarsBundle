package com.cobblecompanion.cobbledollarscreate;

import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

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

    /**
     * Nutzer-Vorgabe (mehrere Items pro Block): bei genau einer Regel zeigt der Filter-Slot das
     * echte Item (setFilter(Item) oben), bei null/keiner Regel wird er geleert, bei MEHREREN
     * Regeln zeigt er Creates eigenes "create:filter"-Item als Platzhalter (Create rendert/behandelt
     * ein FilterItem im Filter-Slot bereits nativ anders, siehe FilteringRenderer - kein eigener
     * Renderer-Hack nötig).
     */
    public static void setFilterForRuleCount(Level level, BlockPos pos, Item singleItem, int ruleCount) {
        if (ruleCount > 1) {
            // Nutzer-Vorgabe: direkt über die Item-Registry statt AllItems.FILTER - dessen
            // Rückgabetyp (Registrate ItemEntry) ist auf unserem Compile-Classpath nicht zugreifbar.
            Item filterItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "filter"));
            setFilter(level, pos, filterItem);
        } else {
            setFilter(level, pos, singleItem);
        }
    }

    /**
     * Nutzer-Vorgabe (Lagernetzwerk-Preise): sucht an allen 6 Nachbarpositionen nach einem Create-
     * Lagerverbinder (create:stock_link) oder Lagerticker (create:stock_ticker) und liest dessen
     * bereits zugewiesene Netzwerk-"Frequenz" aus - rein lesend, KEIN echter Beitritt zu Creates
     * Bestands-Netzwerk (siehe Klassenkommentar ContentObserverConfigManager). PackagerLinkBlockEntity
     * (Lagerverbinder) und StockCheckingBlockEntity (Basis von StockTickerBlockEntity) deklarieren
     * ihre LogisticallyLinkedBehaviour beide als öffentliches Feld "behaviour" mit öffentlichem
     * "freqId"-Feld - kein getBehaviour(TYPE)-Umweg nötig. Wird bei JEDEM Aufruf frisch neu gesucht
     * (kein gecachtes Handle), also unabhängig vom Redstone-Flackern des Beobachters selbst.
     */
    public static UUID findLinkedNetworkFreqId(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            var blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof PackagerLinkBlockEntity link && link.behaviour != null) {
                if (link.behaviour.freqId != null) return link.behaviour.freqId;
            } else if (blockEntity instanceof StockCheckingBlockEntity checker && checker.behaviour != null) {
                if (checker.behaviour.freqId != null) return checker.behaviour.freqId;
            }
        }
        return null;
    }
}
