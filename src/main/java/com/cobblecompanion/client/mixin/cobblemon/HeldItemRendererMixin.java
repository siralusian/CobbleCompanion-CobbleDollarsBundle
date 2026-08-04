package com.cobblecompanion.client.mixin.cobblemon;

import com.cobblemon.mod.common.client.render.item.HeldItemRenderer;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nutzer-Vorgabe: der von einem bauenden Pokémon getragene Gegenstand (siehe
 * PastureBuilderTickHandler.setShownItem) soll doppelt so groß angezeigt werden. Cobblemon bietet
 * dafür keine öffentliche API (per javap-Analyse verifiziert: renderAtLocator() ist privat und
 * skaliert normale "gehaltene" Items überhaupt nicht, nur die HEAD/Wearable-Sonderfälle). Injiziert
 * direkt NACH dem poseStack.mulPose(Matrix4f)-Aufruf, der die Locator-Transformation anwendet (exakt
 * verifizierte Bytecode-Stelle, siehe Offset 141 in der dekompilierten Methode) - an dieser Stelle
 * ist der PoseStack schon auf die Position/Rotation des "item"-Locators ausgerichtet, ein
 * zusätzliches poseStack.scale() betrifft deshalb NUR den nachfolgend gerenderten Gegenstand, nichts
 * anderes am Pokémon-Modell.
 *
 * Beschränkt auf: targetLocator=="item" (nicht die HEAD/Wearable-Locators) UND die Entity ist ein
 * PokemonEntity mit nicht-leerem shownItem - dieses Feld wird aktuell AUSSCHLIESSLICH von unserem
 * PastureBuilder-System gesetzt (verifiziert: kein anderer Cobblemon-Call-Site existiert), ein
 * einfacher "nicht leer"-Check reicht daher als Marker, ohne ein zusätzliches eigenes Flag am
 * Pokémon einführen zu müssen.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(
        method = "renderAtLocator",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER
        )
    )
    private void cobblecompanion$scaleBuildItem(ItemStack item, PosableState state, LivingEntity entity,
            PoseStack poseStack, MultiBufferSource buffer, int light, int seed, boolean frontLight,
            String targetLocator, CallbackInfo ci) {
        if (!"item".equals(targetLocator)) return;
        if (!(entity instanceof PokemonEntity pokemon)) return;
        if (pokemon.getShownItem().isEmpty()) return;

        poseStack.scale(2.0F, 2.0F, 2.0F);
    }
}
