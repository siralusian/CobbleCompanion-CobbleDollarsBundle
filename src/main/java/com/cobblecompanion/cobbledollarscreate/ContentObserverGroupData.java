package com.cobblecompanion.cobbledollarscreate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Nutzer-Vorgabe (verknüpfte Zähler/Abzieher-Gruppen, Item-Abstimmung): Datenkomponenten-Payload,
 * der auf einem Schlauer-Beobachter-ITEM speichert, auf welche Gruppe es "abgestimmt" ist - exaktes
 * Gegenstück zu Creates eigenem "Freq"-Tag in LogisticallyLinkedBlockItem.assignFrequency(), nur
 * als eigene DataComponentType statt Creates BLOCK_ENTITY_DATA-Wiederverwendung (siehe
 * ContentObserverDataComponents). Ein frisches, nie abgestimmtes Item trägt diese Komponente
 * schlicht nicht (kein Default) - siehe ContentObserverInteractionHandler.
 */
public record ContentObserverGroupData(String groupId) {

    public static final Codec<ContentObserverGroupData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("groupId").forGetter(ContentObserverGroupData::groupId)
    ).apply(instance, ContentObserverGroupData::new));

    public static final StreamCodec<ByteBuf, ContentObserverGroupData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ContentObserverGroupData::groupId,
        ContentObserverGroupData::new);
}
