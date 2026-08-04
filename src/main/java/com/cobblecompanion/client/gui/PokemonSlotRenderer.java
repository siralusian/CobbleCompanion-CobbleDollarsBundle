package com.cobblecompanion.client.gui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.Set;

/**
 * Wiederverwendbares Pokedex-Slot-Rendering (pokedex_slot.png-Hintergrund + lebendiges
 * 3D-Pokemon-Modell darin), nachgebaut aus Cobblemons eigener EntriesScrollingWidget
 * .PokemonScrollSlotRow (com.cobblemon.mod.common.client.gui.pokedex.widgets), die dasselbe
 * für die Pokemon-Liste im Pokedex/Living-Dex-Tab macht. Wird von ToDo-, Who-Needs- und
 * Types-Tab für die neuen Slot-Layouts genutzt - reine Grafik, keine Zahlen/Labels (die
 * zeichnet der jeweilige Tab selbst passend zu seinem Layout obendrauf).
 */
public final class PokemonSlotRenderer {
    private PokemonSlotRenderer() {}

    private static final ResourceLocation SLOT_BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/pokedex_slot.png");
    public static final int SLOT_SIZE = 25;

    /**
     * Zeichnet einen 25x25 Pokedex-Slot bei (x, y). speciesId darf null sein (dann nur der
     * leere Slot-Rahmen, z.B. für "Daten noch nicht geladen"). aspects steuert Shiny/Gender/
     * Formen-Darstellung des Modells - leeres Set für die "Standard"-Optik.
     */
    public static void renderSlot(GuiGraphics graphics, int x, int y, ResourceLocation speciesId, Set<String> aspects) {
        graphics.blit(SLOT_BACKGROUND, x, y, SLOT_SIZE, SLOT_SIZE, 0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        if (speciesId == null) return;
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        if (species == null) return;

        graphics.enableScissor(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1);

        PoseStack matrices = graphics.pose();
        matrices.pushPose();
        matrices.translate(x + (SLOT_SIZE / 2.0), y + 1.0, 0.0);
        matrices.scale(2.5F, 2.5F, 1F);

        RenderablePokemon renderablePokemon = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
        // Cobblemons eigene fromEulerXYZDegrees()-Erweiterungsfunktion (Hamilton-Produkt der
        // Einzelrotationen X, dann Y, dann Z) entspricht mathematisch JOMLs rotationXYZ().
        Quaternionf rotation = new Quaternionf().rotationXYZ(
            (float) Math.toRadians(13.0), (float) Math.toRadians(35.0), 0f);

        PokemonGuiUtilsKt.drawProfilePokemon(
            renderablePokemon,
            matrices,
            rotation,
            PoseType.PROFILE,
            new FloatingState(),
            0f,      // partialTicks
            4.5F,    // scale
            true,    // applyProfileTransform
            false,   // applyBaseScale
            1f, 1f, 1f, 1f, // r, g, b, a
            0f, 0f   // headYaw, headPitch
        );

        matrices.popPose();
        graphics.disableScissor();
    }

    /**
     * Wie renderSlot(), aber ohne den pokedex_slot.png-Hintergrund und in frei wählbarer
     * Größe statt fest SLOT_SIZE - für Stellen, die ihren eigenen Rahmen zeichnen (z.B. das
     * größere "Infopanel"-Layout im Type-Tab). Skaliert Modellgröße/Kamera-Offset proportional
     * zu SLOT_SIZE=25 (dort mit Scale 2.5F / Zoom 4.5F getunt).
     */
    public static void renderModel(GuiGraphics graphics, int x, int y, int size, ResourceLocation speciesId, Set<String> aspects) {
        if (speciesId == null) return;
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(speciesId);
        if (species == null) return;

        float factor = size / (float) SLOT_SIZE;
        graphics.enableScissor(x + 1, y + 1, x + size - 1, y + size - 1);

        PoseStack matrices = graphics.pose();
        matrices.pushPose();
        matrices.translate(x + (size / 2.0), y + 1.0, 0.0);
        matrices.scale(2.5F * factor, 2.5F * factor, 1F);

        RenderablePokemon renderablePokemon = new RenderablePokemon(species, aspects, ItemStack.EMPTY);
        Quaternionf rotation = new Quaternionf().rotationXYZ(
            (float) Math.toRadians(13.0), (float) Math.toRadians(35.0), 0f);

        PokemonGuiUtilsKt.drawProfilePokemon(
            renderablePokemon,
            matrices,
            rotation,
            PoseType.PROFILE,
            new FloatingState(),
            0f,
            4.5F,
            true,
            false,
            1f, 1f, 1f, 1f,
            0f, 0f
        );

        matrices.popPose();
        graphics.disableScissor();
    }
}
