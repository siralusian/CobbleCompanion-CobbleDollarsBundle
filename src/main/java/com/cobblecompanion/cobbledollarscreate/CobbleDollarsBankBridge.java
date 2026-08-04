package com.cobblecompanion.cobbledollarscreate;

import fr.harmex.cobbledollars.common.CobbleDollars;
import fr.harmex.cobbledollars.common.config.BankConfig;
import fr.harmex.cobbledollars.common.network.packets.s2c.SyncShopConfigPacket;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Bank;
import fr.harmex.cobbledollars.common.world.item.trading.shop.Offer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.Map;

/**
 * CobbleDollars' eigene BankScreen (Verkaufen-Button) prüft rein CLIENT-seitig, ob ein Item in
 * ClientShopConfig.getBank() (synced per SyncShopConfigPacket) vorkommt - unabhängig von unserer
 * eigenen Preisüberschreibung in SellHandlerMixin. Da CobbleMerchants jetzt IMMER jedes Item
 * annehmen sollen (Nutzer-Vorgabe "er muss quasi alles annehmen egal ob es dafür Cobbledollars
 * gibt oder nicht"), reicht eine Liste der zentral bepreisten Items nicht mehr - stattdessen wird
 * hier JEDES registrierte Item einmalig in CobbleDollars' globale Bank-Config eingetragen (Preis 0
 * als Default, überschrieben für Items aus CentralItemPriceManager). Der tatsächlich BEZAHLTE
 * Preis kommt weiterhin ausschließlich aus unserem eigenen Server-seitigen Override
 * (SellHandlerMixin) - die hier eingetragenen Preise dienen nur dazu, den Button clientseitig
 * für alles anklickbar zu machen. Aufrufer müssen VORHER ModAvailability.isCobbleDollarsAvailable()
 * geprüft haben.
 */
public final class CobbleDollarsBankBridge {

    private CobbleDollarsBankBridge() {}

    /**
     * Trägt jedes registrierte Item (außer Luft) in die globale Bank-Config ein, falls noch nicht
     * vorhanden, und aktualisiert den Preis aller in prices gelisteten Items. Broadcastet den
     * neuen Stand an alle Online-Spieler. Aufrufer: einmalig beim Serverstart (voller Item-Katalog
     * fehlt dann noch komplett) und nach jeder Änderung der zentralen Preisliste.
     */
    public static void syncGlobalBank(Map<String, Long> prices) {
        BankConfig bankConfig = CobbleDollars.INSTANCE.getBankConfig();
        Bank bank = bankConfig.getBank();
        boolean changed = false;
        for (ResourceLocation itemRl : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(itemRl);
            if (item == null || item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            BigInteger price = BigInteger.valueOf(prices.getOrDefault(itemRl.toString(), 0L));
            Offer existing = bank.get(stack);
            if (existing == null) {
                bank.add(new Offer(stack, price, Integer.MAX_VALUE));
                changed = true;
            } else if (!existing.getPrice().equals(price)) {
                existing.setPrice(price);
                changed = true;
            }
        }
        if (!changed) return;
        BankConfig.Companion.save();
        new SyncShopConfigPacket(CobbleDollars.INSTANCE.getShopConfig().getDefaultShop(), bank).sendToAllPlayers();
    }
}
