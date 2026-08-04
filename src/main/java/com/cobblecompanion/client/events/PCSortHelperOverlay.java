package com.cobblecompanion.client.events;

import com.cobblecompanion.CobbleCompanion;
import com.cobblecompanion.client.data.ClientSettingsHelper;
import com.cobblecompanion.client.data.PCSortHelper;
import com.cobblemon.mod.common.client.gui.pc.GrabbedStorageSlot;
import com.cobblemon.mod.common.client.gui.pc.PCGUI;
import com.cobblemon.mod.common.client.gui.pc.StorageWidget;
import com.cobblemon.mod.common.client.storage.ClientBox;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * PC-Sortierhilfe (Punkt 4 der Professor-Tab-Roadmap): zeichnet eine Infobox über Cobblemons
 * ECHTEM PC-GUI (nicht dem per Reflection eingebetteten Companion-Klon aus dem Professor-Tab!),
 * sobald der Spieler gerade ein Pokemon in der Hand hält - sagt ihm, in welche Box/welchen Slot
 * es laut Sortier-Einstellungen (Settings-Tab, Kategorie "PC") gehört, und hebt GENAU diesen Slot
 * optisch hervor, falls die richtige Box schon offen ist. Hält der Spieler NICHTS, werden
 * stattdessen alle Pokemon der gerade offenen Box, die NICHT in ihrer Dex-Box liegen, mit einem
 * kleinen roten Rahmen markiert ("falsch abgelegt"-Feedback).
 *
 * Bewusst OHNE Mixin gelöst: ScreenEvent.Render.Post feuert für JEDEN offenen Screen inkl.
 * Cobblemons eigenem PCGUI, ohne dessen kompilierten Code zu verändern - reines "Draufmalen".
 * Alle benötigten StorageWidget-Methoden (getGrabbedSlot/getBox, per Bytecode-Analyse via javap
 * verifiziert) sind PUBLIC - keine Reflection mehr nötig (frühere Version reflektierte auf die
 * privaten Felder grabbedSlot/box, obwohl öffentliche Getter existieren).
 */
@EventBusSubscriber(modid = "cobblecompanion", value = Dist.CLIENT)
public class PCSortHelperOverlay {

    private static final int INFO_BOX_OFFSET_Y = -18; // relativ zum oberen Rand des PC-GUI
    private static final int INFO_BOX_H = 12;
    private static final int HIGHLIGHT_BORDER_COLOR = 0xFF00FF00;
    private static final int HIGHLIGHT_BORDER_THICKNESS = 2;
    private static final int HIGHLIGHT_TEXT_COLOR = 0x55FF55;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;
    private static final int WRONG_SLOT_BORDER_COLOR = 0xFFFF5555;
    private static final int WRONG_SLOT_BORDER_THICKNESS = 2;
    // Rahmen-Farbcode-Redesign (Nutzer-Vorgabe): kein Rahmen=exakt richtiger Slot, Blau=braucht
    // Entwicklung (Vorstufe liegt woanders als ihr eigener Zielslot), Rot=falscher Slot (final-
    // stufig oder komplett unrelated), Gelb=Pokemon in Nicht-Sortier-Box (z.B. "Neu") mit noch
    // freiem Zielslot in den Sortier-Boxen, Orange=PC-Slot eines Team-Pokemons ist von einer
    // ANDEREN Art belegt (z.B. Karpador auf Garados' Slot, während Garados im Team ist).
    private static final int NEEDS_EVOLUTION_BORDER_COLOR = 0xFF5599FF;
    private static final int FREE_ELSEWHERE_BORDER_COLOR = 0xFFFFDD55;
    private static final int TEAM_SLOT_WRONG_BORDER_COLOR = 0xFFFF9900;

    // Per javap-Bytecode-Analyse von StorageWidget.setupStorageSlots() verifiziert (nicht mehr
    // geschätzt): Box-Slots beginnen bei (Widget-X + 7, Widget-Y + 11 [+5 falls Anzeige-Optionen
    // eingeblendet sind]), Raster 5 Zeilen x 6 Spalten, Abstand 27px (StorageSlot.SIZE=25 +
    // BoxStorageWidget.BOX_SLOT_PADDING=2).
    private static final int BOX_SLOT_START_OFFSET_X = 7;
    private static final int BOX_SLOT_START_OFFSET_Y = 11;
    private static final int BOX_SLOT_DISPLAY_OPTIONS_EXTRA_Y = 5;
    private static final int BOX_SLOT_STEP = 27;
    private static final int BOX_SLOT_SIZE = 25;

    // JUSTIERSCHRAUBE: nicht-interaktiver Status-Button (Sortierhilfe an/aus + wofür sortiert
    // wird) - EIN Button im exakt selben Look wie die Settings-Tab-Buttons (gleiche Textur/Maße
    // wie SETTINGS_CYCLE_W/SETTINGS_TOGGLE_H in CompanionScreen), platziert über dem Team-Bereich
    // (PARTY_SLOT_START_OFFSET_X/Y aus StorageWidget, per javap verifiziert). AUS -> roter "AUS"-
    // Text (wie SETTINGS_TOGGLE_OFF_COLOR), AN -> weißer Text mit dem aktuellen Sortiermodus.
    private static final ResourceLocation STATUS_BTN_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/summary/summary_evolve_button.png");
    private static final int STATUS_BTN_NATIVE_W = 54;
    private static final int STATUS_BTN_NATIVE_H = 15;
    private static final int STATUS_BTN_W = 59; // wie SETTINGS_CYCLE_W
    private static final int STATUS_BTN_H = 13; // wie SETTINGS_TOGGLE_H
    private static final int STATUS_BTN_GAP_ABOVE_TEAM = 3;
    private static final int STATUS_BTN_EXTRA_OFFSET_Y = 30; // Justierschraube: 15 + nochmal 15 höher
    private static final int STATUS_BTN_EXTRA_OFFSET_X = -5; // Justierschraube: 5px nach links
    private static final int STATUS_ON_COLOR = 0xFFFFFFFF;
    private static final int STATUS_OFF_COLOR = 0xFFFF5555;

    // Per javap-Bytecode-Analyse von StorageWidget verifiziert: Team-Slots beginnen bei
    // (Widget-X + 193, Widget-Y + 8).
    private static final int PARTY_SLOT_START_OFFSET_X = 193;
    private static final int PARTY_SLOT_START_OFFSET_Y = 8;

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PCGUI pcgui)) return;

        try {
            GuiGraphics graphics = event.getGuiGraphics();
            Minecraft mc = Minecraft.getInstance();
            int guiLeft = (mc.getWindow().getGuiScaledWidth() - PCGUI.BASE_WIDTH) / 2;
            int guiTop = (mc.getWindow().getGuiScaledHeight() - PCGUI.BASE_HEIGHT) / 2;

            StorageWidget storageWidget = pcgui.getStorage();
            if (storageWidget == null) return;

            // Status-Button (Sortierhilfe an/aus + Sortiermodus) immer sichtbar, auch wenn die
            // Sortierhilfe gerade AUS ist - genau dafür ist die rote "AUS"-Anzeige ja da.
            renderStatusButton(graphics, mc, storageWidget);

            if (!ClientSettingsHelper.isPcSortHelperEnabled()) return;

            ClientPC pc = pcgui.getPc();
            if (pc == null) return;
            int totalBoxes = pc.getBoxes().size();
            int currentBox = storageWidget.getBox() + 1; // intern 0-indexiert

            GrabbedStorageSlot grabbedSlot = storageWidget.getGrabbedSlot();
            Pokemon held = grabbedSlot != null ? grabbedSlot.getPokemon() : null;

            if (held != null) {
                renderHeldFeedback(graphics, mc, pcgui, storageWidget, pc, held, totalBoxes, currentBox, guiLeft, guiTop);
            } else {
                renderMisplacedMarkers(graphics, pcgui, storageWidget, pc, totalBoxes, currentBox);
            }
        } catch (Exception e) {
            CobbleCompanion.LOGGER.error("[CC] Error in PCSortHelperOverlay", e);
        }
    }

    /**
     * Nicht-interaktiver Status-Button über dem Team-Bereich - exakt derselbe Look wie ein
     * Settings-Tab-Button (gleiche Textur/Maße, siehe Klassenkommentar), aber ohne Klick-Handler.
     * AUS -> roter "AUS"-Text, AN -> weißer Text mit dem aktuellen Sortiermodus (Pokédex/Living Dex).
     */
    private static void renderStatusButton(GuiGraphics graphics, Minecraft mc, StorageWidget storageWidget) {
        int x = storageWidget.getX() + PARTY_SLOT_START_OFFSET_X + STATUS_BTN_EXTRA_OFFSET_X;
        int y = storageWidget.getY() + PARTY_SLOT_START_OFFSET_Y - STATUS_BTN_H - STATUS_BTN_GAP_ABOVE_TEAM
            - STATUS_BTN_EXTRA_OFFSET_Y;

        // Die Textur ist ein 54x30-Sheet (zwei gestapelte 54x15-Frames: normal bei V=0, Hover bei
        // V=15) - textureHeight muss deshalb STATUS_BTN_NATIVE_H*2 sein, nicht nur NATIVE_H, sonst
        // normalisiert die GPU die V-Koordinaten falsch und beide Frames werden zusammengequetscht
        // in den sichtbaren Button-Bereich gesampelt (Bug-Report: "2 mal der Evolve Button").
        graphics.blit(STATUS_BTN_TEXTURE, x, y, STATUS_BTN_W, STATUS_BTN_H,
            0f, 0f, STATUS_BTN_NATIVE_W, STATUS_BTN_NATIVE_H, STATUS_BTN_NATIVE_W, STATUS_BTN_NATIVE_H * 2);

        boolean enabled = ClientSettingsHelper.isPcSortHelperEnabled();
        boolean livingDexPlus = enabled && ClientSettingsHelper.isPcSortModeLivingDexPlus();
        String text;
        if (!enabled) {
            text = Component.translatable("cobblecompanion.gui.pcsort.status_off").getString();
        } else {
            text = Component.translatable(switch (ClientSettingsHelper.getPcSortMode()) {
                case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX -> "cobblecompanion.settings.filter.livingdex";
                case ClientSettingsHelper.PC_SORT_MODE_LIVING_DEX_PLUS -> "cobblecompanion.settings.filter.livingdexplus";
                default -> "cobblecompanion.settings.filter.pokedex";
            }).getString();
        }
        // Living Dex+ wird auf Nutzer-Wunsch immer in Gold/Gelb dargestellt, auch hier im PC-Status-Button.
        int color = !enabled ? STATUS_OFF_COLOR : livingDexPlus ? 0xFFFFD700 : STATUS_ON_COLOR;

        int textX = x + (STATUS_BTN_W - mc.font.width(text)) / 2;
        int textY = y + (STATUS_BTN_H - 8) / 2;
        graphics.drawString(mc.font, text, textX, textY, color, true);
    }

    /** Infotext + exakte Ziel-Slot-Markierung für das gerade gehaltene Pokemon. */
    private static void renderHeldFeedback(GuiGraphics graphics, Minecraft mc, PCGUI pcgui, StorageWidget storageWidget,
                                            ClientPC pc, Pokemon held, int totalBoxes, int currentBox, int guiLeft, int guiTop) {
        PCSortHelper.SortTarget target = PCSortHelper.computeTarget(held, totalBoxes);
        if (target == null) return;

        String text;
        boolean inTargetBox;
        if (target.duplicate) {
            text = Component.translatable("cobblecompanion.gui.pcsort.duplicate").getString();
            inTargetBox = false;
        } else if (target.evolvesTo != null) {
            String boxLabel = boxDisplayName(pc, target.box);
            text = Component.translatable("cobblecompanion.gui.pcsort.needs_evolution", target.evolvesTo, boxLabel,
                target.row, target.slot).getString();
            inTargetBox = currentBox == target.box;
        } else {
            String boxLabel = boxDisplayName(pc, target.box);
            text = Component.translatable("cobblecompanion.gui.pcsort.target_box", boxLabel,
                target.row, target.slot).getString();
            inTargetBox = currentBox == target.box;
        }

        int textWidth = mc.font.width(text);
        int textX = guiLeft + (PCGUI.BASE_WIDTH - textWidth) / 2;
        int textY = guiTop + INFO_BOX_OFFSET_Y;

        graphics.fill(textX - 4, textY - 2, textX + textWidth + 4, textY + INFO_BOX_H, 0xC0000000);
        graphics.drawString(mc.font, text, textX, textY, inTargetBox ? HIGHLIGHT_TEXT_COLOR : DEFAULT_TEXT_COLOR, true);

        // Jetzt der GENAUE Ziel-Slot statt (wie zuvor) eines Rahmens ums gesamte Fenster - Position
        // per javap-verifizierter Formel (siehe Klassenkommentar), nicht mehr geschätzt.
        if (inTargetBox && !target.duplicate) {
            int[] pos = slotScreenPos(storageWidget, pcgui, target.row, target.slot);
            drawSlotBorder(graphics, pos[0], pos[1], HIGHLIGHT_BORDER_COLOR, HIGHLIGHT_BORDER_THICKNESS);
        }
    }

    /**
     * Hält der Spieler nichts: Rahmen-Farbcode-Redesign (Nutzer-Vorgabe) über die gerade offene
     * Box. Innerhalb echter Sortier-Boxen (nicht "Neu", nicht die reservierte "Doppelte"-Box):
     * kein Rahmen = exakt richtiger Slot (Box+Zeile+Slot, nicht nur Box), Blau = Vorstufe liegt
     * nicht auf ihrem eigenen Zielslot (braucht noch Entwicklung), Rot = Endstufe (oder komplett
     * unrelated Art) liegt falsch. In Nicht-Sortier-Boxen (z.B. "Neu"): Gelb, wenn der eigene
     * Zielslot in den Sortier-Boxen noch frei ist. Orange (Vorrang vor allem anderen): ein PC-Slot
     * gehört laut Ziel-Berechnung einem Pokemon, das gerade im TEAM ist, aber eine ANDERE Art
     * liegt dort (z.B. Karpador auf Garados' Slot, während Garados im Team ist).
     *
     * BUGFIX/Ergänzung (Nutzer-Vorgabe): In NICHT-Sortier-Boxen gilt Gelb jetzt AUCH, wenn der
     * Zielslot von einer KOMPLETT ANDEREN Art belegt ist (z.B. Glumanda liegt auf Bisasams
     * Zielslot -> Bisasam wird Gelb markiert), nicht nur wenn er frei ist. Gilt NUR in Nicht-
     * Sortier-Boxen (Nutzer-Korrektur: Gelb ist ausschließlich für Boxen reserviert, die nicht zum
     * Sortieren benötigt werden) - innerhalb echter Sortier-Boxen bleibt es bei Rot/Blau, auch wenn
     * der Zielslot durch eine andere Art blockiert ist. "Gehört zur selben Familie/Art" wird über
     * computeDexPosition()-Gleichheit geprüft (derselbe Ziel-Index = dieselbe Familie/Art/Form,
     * modusunabhängig).
     */
    private static void renderMisplacedMarkers(GuiGraphics graphics, PCGUI pcgui, StorageWidget storageWidget,
                                                 ClientPC pc, int totalBoxes, int currentBox) {
        if (currentBox == totalBoxes) return; // reservierte "Doppelte"-Box - keine Markierungen dort
        if (currentBox < 1 || currentBox > pc.getBoxes().size()) return;

        boolean sortingBox = PCSortHelper.isSortingBox(currentBox, totalBoxes);

        // Orange-Vorbereitung: für jedes TEAM-Pokemon dessen eigenen Zielslot berechnen - liegt er
        // in DIESER Box, wird die Slot-Position vorgemerkt (row*6+slot als Schlüssel).
        java.util.Map<Integer, Pokemon> teamTargetInThisBox = new java.util.HashMap<>();
        try {
            for (Pokemon t : pcgui.getParty()) {
                if (t == null) continue;
                int[] targetPos = PCSortHelper.computeDexPosition(t, totalBoxes);
                if (targetPos == null || targetPos[0] != currentBox) continue;
                teamTargetInThisBox.put(targetPos[1] * 6 + targetPos[2], t);
            }
        } catch (Exception ignored) {}

        ClientBox box = pc.getBoxes().get(currentBox - 1);
        List<Pokemon> slots = box.getSlots();
        for (int i = 0; i < slots.size() && i < 30; i++) {
            Pokemon p = slots.get(i);
            int row = i / 6 + 1;
            int slot = i % 6 + 1;
            int[] pos = slotScreenPos(storageWidget, pcgui, row, slot);

            Pokemon teamOwner = teamTargetInThisBox.get(row * 6 + slot);
            if (teamOwner != null && p != null
                && !p.getSpecies().getResourceIdentifier().equals(teamOwner.getSpecies().getResourceIdentifier())) {
                drawSlotBorder(graphics, pos[0], pos[1], TEAM_SLOT_WRONG_BORDER_COLOR, WRONG_SLOT_BORDER_THICKNESS);
                continue;
            }

            if (p == null) continue;

            int[] target = PCSortHelper.computeDexPosition(p, totalBoxes);
            if (target == null) continue; // Antwort steht noch aus, diesen Frame überspringen
            boolean exact = target[0] == currentBox && target[1] == row && target[2] == slot;
            if (exact) continue; // kein Rahmen (gilt für Sortier- UND Nicht-Sortier-Boxen gleich)

            if (!sortingBox) {
                // Nicht-Sortier-Box (z.B. "Neu"): Gelb, wenn der Zielslot frei ODER von einer
                // anderen Art belegt ist - Rot/Blau gelten hier laut Vorgabe nie.
                Pokemon occupantAtTarget = getPokemonAt(pc, target[0], target[1], target[2]);
                boolean targetBlockedByOther = false;
                if (occupantAtTarget != null) {
                    int[] occupantTarget = PCSortHelper.computeDexPosition(occupantAtTarget, totalBoxes);
                    targetBlockedByOther = occupantTarget == null
                        || occupantTarget[0] != target[0] || occupantTarget[1] != target[1] || occupantTarget[2] != target[2];
                }
                if (occupantAtTarget == null || targetBlockedByOther) {
                    drawSlotBorder(graphics, pos[0], pos[1], FREE_ELSEWHERE_BORDER_COLOR, WRONG_SLOT_BORDER_THICKNESS);
                }
                continue;
            }

            // Sortier-Box: immer Rot/Blau je nach Vorentwicklungs-Status, NIE Gelb (Nutzer-
            // Korrektur) - selbst wenn der Zielslot durch eine andere Art blockiert ist. BUGFIX:
            // species.getEvolutions() ist client-seitig auf einem echten Server unzuverlässig
            // (siehe PCSortHelper.hasKnownEvolutions()).
            Boolean hasEvolutions = PCSortHelper.hasKnownEvolutions(p.getSpecies().getName());
            if (hasEvolutions == null) continue; // Antwort steht noch aus
            int color = hasEvolutions ? NEEDS_EVOLUTION_BORDER_COLOR : WRONG_SLOT_BORDER_COLOR;
            drawSlotBorder(graphics, pos[0], pos[1], color, WRONG_SLOT_BORDER_THICKNESS);
        }
    }

    /** Pokemon an einer bestimmten Box/Zeile/Slot-Position (1-indexiert), oder null (leer/ungültig). */
    private static Pokemon getPokemonAt(ClientPC pc, int box, int row, int slot) {
        try {
            ClientBox b = pc.getBoxes().get(box - 1);
            List<Pokemon> slots = b.getSlots();
            int posInBox = (row - 1) * 6 + (slot - 1);
            if (posInBox < 0 || posInBox >= slots.size()) return null;
            return slots.get(posInBox);
        } catch (Exception e) {
            return null;
        }
    }

    /** Bildschirm-Position (oben-links) des Box-Slots row/slot (1-indexiert wie PCSortHelper.SortTarget). */
    private static int[] slotScreenPos(StorageWidget storageWidget, PCGUI pcgui, int row, int slot) {
        int baseX = storageWidget.getX() + BOX_SLOT_START_OFFSET_X;
        int baseY = storageWidget.getY() + BOX_SLOT_START_OFFSET_Y
            + (pcgui.getDisplayOptions() ? BOX_SLOT_DISPLAY_OPTIONS_EXTRA_Y : 0);
        int x = baseX + (slot - 1) * BOX_SLOT_STEP;
        int y = baseY + (row - 1) * BOX_SLOT_STEP;
        return new int[]{x, y};
    }

    private static void drawSlotBorder(GuiGraphics graphics, int x, int y, int color, int thickness) {
        int x2 = x + BOX_SLOT_SIZE;
        int y2 = y + BOX_SLOT_SIZE;
        graphics.fill(x, y, x2, y + thickness, color);
        graphics.fill(x, y2 - thickness, x2, y2, color);
        graphics.fill(x, y, x + thickness, y2, color);
        graphics.fill(x2 - thickness, y, x2, y2, color);
    }

    private static String boxDisplayName(ClientPC pc, int boxNumber) {
        try {
            var box = pc.getBoxes().get(boxNumber - 1);
            if (box.getName() != null) {
                String name = box.getName().getString();
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignored) {}
        return "Box " + boxNumber;
    }
}
