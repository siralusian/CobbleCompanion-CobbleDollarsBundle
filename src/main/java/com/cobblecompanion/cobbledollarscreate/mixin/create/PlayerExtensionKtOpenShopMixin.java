package com.cobblecompanion.cobbledollarscreate.mixin.create;

import com.cobblecompanion.cobbledollarscreate.CreateStockTickerBridge;
import com.cobblecompanion.cobbledollarscreate.MerchantStockSyncHelper;
import com.cobblecompanion.cobbledollarscreate.data.CentralItemPriceManager;
import com.cobblecompanion.cobbledollarscreate.data.CobbleMerchantSellManager;
import com.cobblecompanion.cobbledollarscreate.data.MerchantOfferManager;
import fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt;
import fr.harmex.cobbledollars.common.world.item.trading.CobbleDollarsShopHolder;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Category;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Offer;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Shop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zwei Aufgaben rund um das Öffnen eines CobbleMerchant/CustomNPC-Merchant-Shops (siehe
 * EntityNPCInterfaceMerchantMixin in CC:CobbleDollars/CustomNPCs + CobbleMerchant.mobInteract(),
 * beide rufen PlayerExtensionKt.openShop(...) DIREKT auf - dieser 3-Parameter-openShop ist per
 * javap verifiziert der EINZIGE echte Ansatzpunkt, der sowohl den normalen Rechtsklick-Weg als auch
 * den Bank-Screen-Button-Weg (OpenShopHandler ruft am Ende ebenfalls openShop$default() ->
 * openShop() auf) gleichermaßen abdeckt:
 *
 * 1. HEAD: Nutzer-Vorgabe "die Verkaufsliste des Merchants soll automatisch und VOLLSTÄNDIG aus
 *    der dem verknüpften Netzwerk zugewiesenen Preisliste erzeugt werden, analog zur bereits
 *    bestehenden Bank-Erzwingung" (siehe SellHandlerMixin) - überschreibt holder.getShop() JEDES
 *    Mal komplett, BEVOR CobbleDollars die Kaufoberfläche baut (die liest den Shop erst weiter unten
 *    im Original-Methodenkörper). Ein manuell im CobbleDollars-Editier-Modus gepflegter Shop wird
 *    dabei NICHT mehr benutzt (Nutzer-Bestätigung: komplette Ersetzung, keine Koexistenz).
 *    Nutzer-Vorgabe: das gilt NUR für Merchants mit einem verknüpften Lagerticker
 *    (CobbleMerchantSellManager.getEffectiveBuyTickerLink) - ohne Verknüpfung bleibt holder.getShop()
 *    unangetastet, CobbleDollars zeigt dann seinen eigenen/manuell editierten Shop, wie ohne unsere
 *    Mod. Vorher wurde ohne Verknüpfung einfach auf die "default"-Preisliste zurückgefallen.
 * 2. TAIL: Bestandsanzeige/rotes X im Kaufinterface (siehe OfferEntryStockOverlayMixin/BuyHandlerMixin) -
 *    unverändert, liest jetzt automatisch den bei HEAD frisch gesetzten Shop.
 */
@Mixin(PlayerExtensionKt.class)
public abstract class PlayerExtensionKtOpenShopMixin {

    private static final String DEFAULT_CATEGORY_NAME = "Sonstiges";

    @Inject(method = "openShop(Lnet/minecraft/world/entity/player/Player;Lfr/harmex/cobbledollars/common/world/item/trading/CobbleDollarsShopHolder;Z)V",
        at = @At("HEAD"))
    private static void cobblecompanion$generateShop(Player player, CobbleDollarsShopHolder holder, boolean isEditMode, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (holder == null) return;
        UUID merchantUuid = holder.getMerchantUUID();
        if (merchantUuid == null) return;

        // Nutzer-Vorgabe: ohne verknüpften Lagerticker bleibt der Shop unangetastet - CobbleDollars
        // zeigt dann seinen eigenen/manuell editierten Shop, als gäbe es unsere Mod für diesen
        // Merchant nicht (statt wie bisher auf die "default"-Preisliste zurückzufallen).
        if (CobbleMerchantSellManager.getEffectiveBuyTickerLink(merchantUuid) == null) return;

        String listId = CreateStockTickerBridge.resolveListIdForMerchant(serverPlayer.server, merchantUuid);
        Map<String, Long> buyPrices = CentralItemPriceManager.getBuyPrices(listId);
        Set<String> buyDisabled = CentralItemPriceManager.getBuyDisabled(listId);
        Map<String, String> itemCategories = CentralItemPriceManager.getCategories(listId);

        Set<String> allItemIds = new LinkedHashSet<>(buyPrices.keySet());
        allItemIds.addAll(itemCategories.keySet());

        // Nutzer-Vorgabe: individuelles Angebot pro Merchant statt immer des gesamten
        // Netzwerk-Angebots (siehe MerchantOfferManager/MerchantOfferEditScreen) - null bedeutet
        // "kein Filter", der Merchant bleibt beim vollen Netzwerk-Angebot.
        Set<String> customRestriction = MerchantOfferManager.getActiveRestriction(merchantUuid);
        if (customRestriction != null) allItemIds.retainAll(customRestriction);

        Map<String, Category> categories = new LinkedHashMap<>();
        for (String itemId : allItemIds) {
            boolean enabled = !buyDisabled.contains(itemId);
            long price = enabled ? buyPrices.getOrDefault(itemId, 0L) : 0L;
            String categoryName = itemCategories.get(itemId);
            // Nutzer-Vorgabe: Aufnahmekriterium ist ein aktiver Preis > 0 ODER eine gesetzte
            // Kategorie (dann Preis 0$) - Items ganz ohne beides gehören nicht in die Verkaufsliste.
            if (price <= 0 && (categoryName == null || categoryName.isBlank())) continue;

            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));

            String catKey = (categoryName == null || categoryName.isBlank()) ? DEFAULT_CATEGORY_NAME : categoryName;
            Category category = categories.computeIfAbsent(catKey, name -> new Category(name, new ArrayList<>()));
            category.getOffers().add(new Offer(stack, BigInteger.valueOf(Math.max(price, 0)), -1));
        }

        holder.setShop(new Shop(new ArrayList<>(categories.values())));
    }

    @Inject(method = "openShop(Lnet/minecraft/world/entity/player/Player;Lfr/harmex/cobbledollars/common/world/item/trading/CobbleDollarsShopHolder;Z)V",
        at = @At("TAIL"))
    private static void cobblecompanion$syncNetworkStock(Player player, CobbleDollarsShopHolder holder, boolean isEditMode, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        MerchantStockSyncHelper.sync(serverPlayer, holder);
    }
}
