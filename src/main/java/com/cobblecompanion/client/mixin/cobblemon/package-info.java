/**
 * Bytecode-Eingriff in Cobblemons eigenen, CLIENT-seitigen Renderer (HeldItemRenderer) - rein
 * kosmetisch, skaliert NUR den von unserem PastureBuilder-System optisch getragenen Bau-
 * Gegenstand (siehe PastureBuilderTickHandler.setShownItem) auf das Doppelte, damit er beim
 * Bauen besser sichtbar ist. Nutzer-Vorgabe.
 *
 * WICHTIG: eigenes Unterpaket + eigene, per "client"-Liste (nicht "mixins") registrierte
 * Mixin-Config (siehe cobblecompanion_cobblemon_client.mixins.json), NIEMALS über die normale
 * "mixins"-Liste - HeldItemRenderer referenziert reine Client-Klassen (PoseStack,
 * MultiBufferSource, PosableState-Rendering), die auf einem Dedicated Server gar nicht geladen
 * werden. Ein Mixin, das versehentlich auch server-seitig zu transformieren versucht, würde den
 * Server-Start crashen - siehe project_serverside_only_verified-Vorgabe (Mod muss auch ohne
 * Client-seitige Installation server-seitig einwandfrei laufen).
 */
package com.cobblecompanion.client.mixin.cobblemon;
