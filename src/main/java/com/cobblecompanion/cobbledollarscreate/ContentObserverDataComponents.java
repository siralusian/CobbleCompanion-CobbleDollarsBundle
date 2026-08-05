package com.cobblecompanion.cobbledollarscreate;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registriert die eigene DataComponentType für die Schlauer-Beobachter-Gruppen-Abstimmung (siehe
 * ContentObserverGroupData) - eigene Komponente statt Creates DataComponents.BLOCK_ENTITY_DATA
 * wiederzuverwenden, weil unsere Gruppen-Zuordnung NICHT im NBT der echten (Create-eigenen)
 * BlockEntity liegt, sondern in unserem eigenen ContentObserverConfigManager.
 */
public final class ContentObserverDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CobbleCompanionDollarsCreate.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContentObserverGroupData>> GROUP_TUNING =
        COMPONENTS.register("content_observer_group_tuning", () -> DataComponentType.<ContentObserverGroupData>builder()
            .persistent(ContentObserverGroupData.CODEC)
            .networkSynchronized(ContentObserverGroupData.STREAM_CODEC)
            .build());

    private ContentObserverDataComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
