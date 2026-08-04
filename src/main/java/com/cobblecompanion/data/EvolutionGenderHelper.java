package com.cobblecompanion.data;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement;

/**
 * Cobblemon hat keine eigene "GenderRequirement"-Klasse - Geschlechts-Sperren bei Entwicklungen
 * (z.B. Girafarig->Kirlia->Guardevoir NUR weiblich, ->Gallade NUR männlich) werden generisch über
 * PokemonPropertiesRequirement (mit target.getGender()) ausgedrückt, per javap bestätigt. Wird
 * gebraucht, wo eine Entwicklungsverzweigung OHNE ein konkretes, bereits getestetes Pokemon-
 * Individuum betrachtet wird (z.B. PCSortHelpers Ziel-Slot-Suche über eine ganze Kette) - dort
 * greift Cobblemons eigenes evo.test(pokemon) nicht, weil es kein spezifisches Pokemon gibt, für
 * das schon getestet werden könnte.
 */
public class EvolutionGenderHelper {

    /** null, falls die Entwicklung an kein bestimmtes Geschlecht gebunden ist. */
    public static Gender requiredGender(Evolution evo) {
        try {
            for (Requirement req : evo.getRequirements()) {
                if (req instanceof PokemonPropertiesRequirement pr) {
                    PokemonProperties target = pr.getTarget();
                    Gender gender = target.getGender();
                    if (gender != null && gender != Gender.GENDERLESS) return gender;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
