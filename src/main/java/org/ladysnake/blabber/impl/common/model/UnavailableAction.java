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
package org.ladysnake.blabber.impl.common.model;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.serialization.FailingOptionalFieldCodec;

import java.util.Optional;

public record UnavailableAction(UnavailableDisplay display, Optional<Text> message) {
    static Codec<UnavailableAction> codec(Codec<JsonElement> jsonCodec) {
        Codec<Text> textCodec = jsonCodec.xmap(Text.Serializer::fromJson, Text.Serializer::toJsonTree);

        return RecordCodecBuilder.create(instance -> instance.group(
                UnavailableDisplay.CODEC.fieldOf("display").forGetter(UnavailableAction::display),
                FailingOptionalFieldCodec.of(textCodec, "message").forGetter(UnavailableAction::message)
        ).apply(instance, UnavailableAction::new));
    }

    public UnavailableAction(PacketByteBuf buf) {
        this(buf.readEnumConstant(UnavailableDisplay.class), buf.readOptional(PacketByteBuf::readText));
    }

    public static void writeToPacket(PacketByteBuf buf, UnavailableAction action) {
        buf.writeEnumConstant(action.display());
        buf.writeOptional(action.message(), PacketByteBuf::writeText);
    }

    public UnavailableAction parseText(@Nullable ServerCommandSource source, @Nullable Entity sender) throws CommandSyntaxException {
        Optional<Text> parsedMessage = message().isEmpty() ? Optional.empty() : Optional.of(Texts.parse(source, message().get(), sender, 0));
        return new UnavailableAction(display(), parsedMessage);
    }
}
