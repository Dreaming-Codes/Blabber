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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.apace100.origins.origin.OriginLayers;
import io.github.apace100.origins.registry.ModComponents;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.TextUtils;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.serialization.FailingOptionalFieldCodec;

import java.util.*;

public record DialogueRandomAnswer(Text text) {
    public static final HashMap<String,String[]> classes= new HashMap<>();
    static {
        classes.put("alchemist",new String[]{"alchimista","alchimisti"});
        classes.put("blacksmith",new String[]{"fabbro","fabbri"});
        classes.put("carpenter",new String[]{"carpentiere","carpentieri"});
        classes.put("cook",new String[]{"cuoco","cuochi"});
        classes.put("farmer",new String[]{"contadino","contadini"});
        classes.put("miner",new String[]{"raccoglitore","raccoglitori"});
        classes.put("bard",new String[]{"bardo","bardi"});
        classes.put("hunter",new String[]{"cacciatore","cacciatori"});
        classes.put("mage",new String[]{"mago","maghi"});
        classes.put("shadow",new String[]{"ombra","ombre"});
        classes.put("warrior",new String[]{"guerriero","guerrieri"});
        classes.put("wanderer",new String[]{"viandante","viandanti"});
    }
    static Codec<DialogueRandomAnswer> codec(Codec<JsonElement> jsonCodec) {
        Codec<Text> textCodec = jsonCodec.xmap(Text.Serializer::fromJson, Text.Serializer::toJsonTree);

        return RecordCodecBuilder.create(instance -> instance.group(
                FailingOptionalFieldCodec.of(textCodec, "text", Text.empty()).forGetter(DialogueRandomAnswer::text)
        ).apply(instance, DialogueRandomAnswer::new));
    }

    public static void writeToPacket(PacketByteBuf buf, DialogueRandomAnswer answer) {
        buf.writeText(answer.text());
    }

    public DialogueRandomAnswer(PacketByteBuf buf) {
        this(buf.readText());
    }

    public DialogueRandomAnswer parseText(@Nullable ServerCommandSource source, @Nullable Entity sender) throws CommandSyntaxException {
        if(text().getString().contains("@class") || text().getString().contains("@pluralclass") || text().getString().contains("@Class") || text().getString().contains("@pluralClass")){
            try {
                String c = ModComponents.ORIGIN.get(sender).getOrigin(OriginLayers.getLayer(new Identifier("elysium:class"))).getIdentifier().toString().replace("elysium:class/","");
                String name=c.split("/")[0];
                return new DialogueRandomAnswer(Texts.parse(source, Text.of(text().getString()
                        .replace("@class",classes.get(name)[0])
                        .replace("@Class", StringUtils.capitalize(classes.get(name)[0]))
                        .replace("@pluralclass", classes.get(name)[1])
                        .replace("@pluralClass", StringUtils.capitalize(classes.get(name)[1]))), sender, 0));
            }catch(Exception ignored){
            }
        }
        return new DialogueRandomAnswer(Texts.parse(source, text(), sender, 0));
    }

    @Override
    public String toString() {
        return "%s".formatted(StringUtils.abbreviate(this.text.getString(), 20));
    }
}
