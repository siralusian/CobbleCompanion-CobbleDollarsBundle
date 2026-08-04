package com.cobblecompanion.integrations.create;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gemeinsame Logik, um Items ECHT aus einem Create-Logistiknetzwerk zu entnehmen (nicht nur ein
 * Preis-/Bestandscheck) - genutzt sowohl von CustomNPCs-Tradern (siehe
 * integrations.customnpcs.mixin.ContainerNPCTraderMixin) als auch von CobbleMerchant-Käufen (siehe
 * integrations.cobbledollars.mixin.create.BuyHandlerMixin). Ursprünglich in ContainerNPCTraderMixin
 * entstanden, hierher ausgelagert, um Code-Verdopplung zu vermeiden.
 *
 * Create liefert Pakete normalerweise asynchron über Förderbänder (PackagerBlockEntity +
 * Conveyor), das lässt sich für einen Sofort-Kauf nicht 1:1 nachbilden. Diese Klasse nutzt
 * stattdessen den ohnehin bereits synchronen Entnahme-Weg, den JEDER Packager selbst verwendet
 * (targetInventory.extract(), siehe InvManipulationBehaviour) - alle Packager desselben Netzwerks
 * (LogisticallyLinkedBehaviour.getAllPresent(freqId)) werden nacheinander um die benötigte Menge
 * erleichtert. Ein Netzwerk-Lager hängt dabei nicht zwingend direkt an einem PackagerBlockEntity -
 * ein "Package Link"-Block (PackagerLinkBlockEntity) verweist stattdessen per getPackager() auf
 * einen entfernten Packager, dessen Kiste die tatsächlichen Items enthält (Live-Fund).
 */
public final class CreateNetworkStockHelper {

    private CreateNetworkStockHelper() {}

    /**
     * Entnimmt bis zu "needed" Stück von "stack" aus dem Netzwerk mit der gegebenen freqId.
     *
     * @return true, wenn insgesamt "needed" Stück tatsächlich entnommen werden konnten (bei
     * teilweisem Erfolg wird alles bereits Entnommene wieder zurückgebucht, kein Dupe-/Verlust-Risiko).
     */
    public static boolean extract(UUID freqId, ItemStack stack, int needed) {
        List<PackagerBlockEntity> touched = new ArrayList<>();
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = needed;

        for (LogisticallyLinkedBehaviour behaviour : LogisticallyLinkedBehaviour.getAllPresent(freqId, true)) {
            if (remaining <= 0) break;

            PackagerBlockEntity packager;
            if (behaviour.blockEntity instanceof PackagerBlockEntity direct) {
                packager = direct;
            } else if (behaviour.blockEntity instanceof PackagerLinkBlockEntity link) {
                packager = link.getPackager();
            } else {
                continue;
            }
            if (packager == null || packager.targetInventory == null) continue;

            // Bugfix (Live-Fund): eine einzelne extract()-Anfrage liefert immer nur EINEN
            // ItemStack zurück, dessen Anzahl technisch NIE über die Item-eigene Stapelgröße
            // (meist 64) hinausgehen kann - selbst wenn der Ziel-Inventar (über mehrere Slots
            // verteilt) insgesamt viel mehr enthält. Ohne diese Schleife wurde pro Packager nur
            // EIN Aufruf gemacht und damit nie mehr als 64 Stück entnommen, selbst wenn der
            // gemeldete Netzwerk-Bestand (getRecentSummary) deutlich höher war.
            while (remaining > 0) {
                ItemStack got = packager.targetInventory.extract(ItemHelper.ExtractionCountMode.UPTO, remaining,
                    s -> ItemStack.isSameItemSameComponents(s, stack));
                if (got.isEmpty()) break; // dieser Packager hat nichts (mehr) davon
                touched.add(packager);
                extracted.add(got);
                remaining -= got.getCount();
            }
        }

        if (remaining > 0) {
            // Race Condition ggü. dem zuvor geprüften Bestand (z.B. anderer Spieler war schneller) -
            // alles Entnommene sofort zurückbuchen, Kauf danach vom Aufrufer abgebrochen.
            for (int i = 0; i < touched.size(); i++) {
                ItemStack leftover = touched.get(i).targetInventory.insert(extracted.get(i));
                if (!leftover.isEmpty()) {
                    com.cobblecompanion.CobbleCompanion.LOGGER.warn(
                        "[CC] Rückbuchung nach fehlgeschlagener Netzwerk-Entnahme unvollständig: {} blieb übrig", leftover);
                }
            }
            return false;
        }
        return true;
    }
}
