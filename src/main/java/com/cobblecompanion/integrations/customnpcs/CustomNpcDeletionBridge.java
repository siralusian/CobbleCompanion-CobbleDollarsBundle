package com.cobblecompanion.integrations.customnpcs;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Kleine Isolationsgrenze für den Lösch-Button im Verknüpfte-NPCs-Panel (siehe
 * network.LinkedMerchantActionPacket) - importiert CustomNPCs-Typen direkt, darf daher nur
 * aufgerufen werden, wenn ModAvailability.isCustomNpcsAvailable() true ist (Aufrufer prüft das).
 * npc.delete() ist bytecode-verifiziert die Methode, die auch der Lösch-Button im CustomNPCs-
 * eigenen Editier-GUI aufruft (räumt VisibilityController/Role/Job korrekt auf, danach Discard).
 */
public final class CustomNpcDeletionBridge {

    private CustomNpcDeletionBridge() {}

    public static boolean delete(Entity entity) {
        if (!(entity instanceof EntityNPCInterface npc)) return false;
        npc.delete();
        return true;
    }
}
