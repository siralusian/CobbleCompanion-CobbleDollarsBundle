package com.cobblecompanion.client.mixin.vanilla;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Nutzer-Fund (Live-Test): Spieler, die per gekaufter/erzwungener Creative-Zeit KEINEN echten
 * Minecraft-OP-Status haben (siehe CreativeTimeManager/DimensionGamemodeManager), können das
 * vanilla F3+F4-Menü nicht zum Gamemode-Wechsel benutzen. Root Cause (per javap-Bytecode-Analyse
 * von GameModeSwitcherScreen$switchToHoveredGameMode bestätigt): der Client selbst prüft VOR dem
 * Senden lokal player.hasPermissions(2) - fehlt die Berechtigung, wird gar kein Paket verschickt
 * (kein server-seitiger Hook könnte das reparieren, da nichts beim Server ankommt).
 *
 * Fix: an genau der Stelle, an der vanilla das Menü nach Loslassen von F4 schließt
 * (checkToClose()), zusätzlich prüfen ob der Spieler kein OP ist - wenn ja, unseren eigenen
 * /companion gamemode-Befehl (der KEINE OP-Berechtigung braucht, siehe CobbleCompanionCommands)
 * mit dem gerade markierten Gamemode absenden. Bewusst NICHT die vanilla-Logik abbrechen/ersetzen,
 * sondern nur ergänzen (@At("HEAD"), nicht cancellable) - für echte OPs bleibt das Verhalten exakt
 * vanilla, für alle anderen kommt unser Befehl als zusätzlicher Seitenkanal hinzu.
 *
 * Zugriff auf das private Feld "currentlyHovered" (dessen Typ, die verschachtelte Enum
 * GameModeIcon, package-private ist und deshalb nicht als Mixin-Accessor-Rückgabetyp klappt -
 * Mixin verlangt dafür exakten Feldtyp-Match, "Object" wird NICHT als Weitwinkel akzeptiert,
 * siehe gescheiterter erster Versuch) über plain Java-Reflection statt Accessor-Mixin - simpler
 * und funktioniert für jedes private Feld unabhängig vom (Un-)Zugänglichkeitsstatus seines Typs.
 */
@Mixin(GameModeSwitcherScreen.class)
public abstract class GameModeSwitcherScreenMixin {

    private static Field cobblecompanion$currentlyHoveredField;

    private static Field cobblecompanion$currentlyHoveredField() {
        if (cobblecompanion$currentlyHoveredField == null) {
            try {
                Field f = GameModeSwitcherScreen.class.getDeclaredField("currentlyHovered");
                f.setAccessible(true);
                cobblecompanion$currentlyHoveredField = f;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return cobblecompanion$currentlyHoveredField;
    }

    @Inject(method = "checkToClose", at = @At("HEAD"))
    private void cobblecompanion$onCheckToClose(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        if (mc.player.hasPermissions(2)) return; // echte OPs: vanilla funktioniert bereits normal

        // Gleiche Bedingung wie vanilla's eigener Loslass-Check (F4 = GLFW-Keycode 292) - wir
        // wollen unseren Befehl exakt in demselben Moment senden, in dem vanilla (erfolglos)
        // versuchen würde, den Wechsel durchzuführen.
        if (InputConstants.isKeyDown(mc.getWindow().getWindow(), 292)) return;

        try {
            Object hovered = cobblecompanion$currentlyHoveredField().get(this);
            if (!(hovered instanceof Enum<?> icon)) return;

            String mode = icon.name().toLowerCase(Locale.ROOT);
            if (mode.equals("survival") || mode.equals("creative") || mode.equals("spectator")) {
                mc.player.connection.sendUnsignedCommand("companion gamemode " + mode);
            }
        } catch (ReflectiveOperationException ignored) {}
    }
}
