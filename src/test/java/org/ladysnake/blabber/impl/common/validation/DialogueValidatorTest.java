/*
 * Blabber
 * Copyright (C) 2022-2024 Ladysnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; If not, see <https://www.gnu.org/licenses>.
 */
package org.ladysnake.blabber.impl.common.validation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.test.GameTestException;
import net.minecraft.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ladysnake.blabber.impl.common.model.DialogueTemplate;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class DialogueValidatorTest {
    @BeforeAll
    public static void setup() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    public void validationFailsOnIncompleteDialogue() throws Exception {
        DialogueTemplate dialogue = loadDialogue("/incomplete_dialogue.json");
        Assertions.assertInstanceOf(
                ValidationResult.Error.NoChoice.class,
                DialogueValidator.validateStructure(dialogue),
                "Dialogue validation should detect incomplete dialogues"
        );
    }

    @Test
    public void validationLogsConditionalDialogues() throws Exception {
        DialogueTemplate dialogue = loadDialogue("/conditional_dialogue.json");
        ValidationResult result = DialogueValidator.validateStructure(dialogue);
        Assertions.assertTrue(result instanceof ValidationResult.Warnings warnings && warnings.warnings().get(0) instanceof ValidationResult.Warning.ConditionalSoftLock, "Dialogue validation should detect conditional softlocks in dialogues");
        Assertions.assertEquals("bargain only has conditional paths to the end of the dialogue", ((ValidationResult.Warnings) result).message());
    }

    @Test
    public void validationFailsOnLoopingDialogue() throws Exception {
        DialogueTemplate dialogue = loadDialogue("/looping_dialogue.json");
        Assertions.assertInstanceOf(ValidationResult.Error.SoftLock.class, DialogueValidator.validateStructure(dialogue), "Dialogue validation should detect looping dialogues");
    }

    @Test
    public void validationFailsOnInvalidReference() throws Exception {
        DialogueTemplate dialogue = loadDialogue("/invalid_reference.json");
        Assertions.assertInstanceOf(ValidationResult.Error.NonexistentIllustration.class, DialogueValidator.validateStructure(dialogue), "Dialogue validation should detect invalid illustration reference");
    }

    private static DialogueTemplate loadDialogue(String name) throws Exception {
        return getResult(DialogueTemplate.CODEC.parse(JsonOps.INSTANCE, new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(DialogueValidatorTest.class.getResourceAsStream(name))), JsonElement.class)), s -> {
            throw new GameTestException(s);
        });
    }

    public static <T, E extends Exception> T getResult(DataResult<T> result, Function<String, E> exceptionGetter) throws Exception {
        Optional<DataResult.PartialResult<T>> optional = result.error();
        if (optional.isPresent()) {
            throw exceptionGetter.apply(optional.get().message());
        } else {
            return result.result().orElseThrow();
        }
    }
}
