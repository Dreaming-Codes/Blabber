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
package org.ladysnake.blabber.impl.common.packets;

import net.minecraft.util.Identifier;

import java.util.Set;

public record DialogueListPacket(Set<Identifier> dialogueIds) {
    /*public static final PacketType<DialogueListPacket> TYPE = PacketType.create(Blabber.id("dialogue_list"), DialogueListPacket::new);

    public DialogueListPacket(PacketByteBuf buf) {
        this(buf.<Identifier, Set<Identifier>>readCollection(HashSet::new, PacketByteBuf::readIdentifier));
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeCollection(dialogueIds(), PacketByteBuf::writeIdentifier);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }*/
}
