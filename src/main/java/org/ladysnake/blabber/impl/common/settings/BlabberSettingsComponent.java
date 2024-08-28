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
package org.ladysnake.blabber.impl.common.settings;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.impl.common.commands.SettingsSubCommand;

import java.util.EnumSet;

public class BlabberSettingsComponent implements AutoSyncedComponent {
    public static final ComponentKey<BlabberSettingsComponent> KEY = ComponentRegistry.getOrCreate(Blabber.id("settings"), BlabberSettingsComponent.class);

    public static BlabberSettingsComponent get(PlayerEntity player) {
        return player.getComponent(KEY);
    }

    private EnumSet<BlabberSetting> enabledSettings = EnumSet.noneOf(BlabberSetting.class);
    private final PlayerEntity player;

    public BlabberSettingsComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isDebugEnabled() {
        if (!this.player.getWorld().isClient && !SettingsSubCommand.ALLOW_DEBUG.test(this.player.getCommandSource())) {
            return false;
        }
        return !this.enabledSettings.isEmpty();
    }

    public boolean isEnabled(BlabberSetting feature) {
        return enabledSettings.contains(feature);
    }

    public void setEnabled(BlabberSetting feature, boolean debugEnabled) {
        if (debugEnabled) {
            this.enabledSettings.add(feature);
        } else {
            this.enabledSettings.remove(feature);
        }
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return player == this.player;
    }

    @Override
    public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        boolean enabled = this.isDebugEnabled();
        buf.writeBoolean(enabled);
// TODO: Implement this
//        if (enabled) {
//            buf.writeEnumSet(this.enabledSettings, BlabberSetting.class);
//        }
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        boolean debugEnabled = buf.readBoolean();
        if (debugEnabled) {
//TODO: Implement this
//            this.enabledSettings = buf.readEnumSet(BlabberSetting.class);
        } else {
            this.enabledSettings = EnumSet.noneOf(BlabberSetting.class);
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        this.enabledSettings = EnumSet.noneOf(BlabberSetting.class);
        for (NbtElement featureId : tag.getList("enabled_features", NbtElement.STRING_TYPE)) {
            BlabberSetting feature = BlabberSetting.getById(featureId.asString());
            if (feature != null) {
                this.enabledSettings.add(feature);
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (BlabberSetting feature : this.enabledSettings) {
            list.add(NbtString.of(feature.id()));
        }
        tag.put("enabled_features", list);
    }
}
