package com.cobblecompanion.integrations.curios;

import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * Isolationsgrenze zur Curios-API: alle Aufrufe hierher gehen ausschließlich hinter
 * ModAvailability.isCuriosAvailable(), damit diese Klasse ohne Curios im Classpath nie geladen
 * werden muss und der Rest des Mods keine direkten Curios-Importe braucht.
 *
 * Wichtig (per javap-Analyse von CurioInventoryCapability#loadInventory bestätigt): das
 * geladene ListTag ueberschreibt NUR die darin enthaltenen Slot-Typen - bereits ausgerüstete
 * Curios in Slot-Typen, die im Tag fehlen, bleiben unangetastet. Ein leeres ListTag räumt also
 * NICHTS auf. Deshalb wird hier vor jedem Laden explizit über getEquippedCurios() (öffentliche,
 * stabile IItemHandlerModifiable-API) jeder Slot einzeln auf ItemStack.EMPTY gesetzt.
 */
public final class CuriosInventoryBridge {

    private CuriosInventoryBridge() {}

    /** Serialisiert das aktuell ausgerüstete Curios-Inventar (leeres ListTag falls keins vorhanden). */
    public static ListTag save(LivingEntity entity) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(entity);
        if (handler.isEmpty()) return new ListTag();
        return handler.get().saveInventory(false);
    }

    /** Leert alle ausgerüsteten Curios-Slots (echtes Leeren, nicht nur loadInventory mit leerem Tag). */
    public static void clear(LivingEntity entity) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(entity);
        if (handler.isEmpty()) return;
        IItemHandlerModifiable equipped = handler.get().getEquippedCurios();
        for (int i = 0; i < equipped.getSlots(); i++) {
            equipped.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Lädt ein zuvor mit save() erzeugtes ListTag. Aufrufer muss vorher clear() aufgerufen haben. */
    public static void load(LivingEntity entity, ListTag tag) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(entity);
        if (handler.isEmpty() || tag == null || tag.isEmpty()) return;
        handler.get().loadInventory(tag);
    }
}
