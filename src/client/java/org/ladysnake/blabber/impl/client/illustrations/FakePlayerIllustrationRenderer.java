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
package org.ladysnake.blabber.impl.client.illustrations;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.impl.common.illustrations.entity.DialogueIllustrationFakePlayer;
import org.ladysnake.blabber.impl.mixin.PlayerEntityAccessor;
import org.ladysnake.blabber.impl.mixin.client.AbstractClientPlayerEntityAccessor;

import java.util.logging.Logger;

public class FakePlayerIllustrationRenderer extends EntityIllustrationRenderer<DialogueIllustrationFakePlayer> {
    public FakePlayerIllustrationRenderer(DialogueIllustrationFakePlayer illustration) {
        super(illustration);
    }

    @SuppressWarnings("UnreachableCode")
    @Environment(EnvType.CLIENT)
    @Override
    protected @Nullable LivingEntity getRenderedEntity(World world) {
        GameProfile profile = this.illustration.profile();
        OtherClientPlayerEntity fakePlayer = new OtherClientPlayerEntity((ClientWorld) world, profile,world.getPlayerByUuid(profile.getId()).getPublicKey());
        this.illustration.data().ifPresent(fakePlayer::readNbt);
        try {
            ((AbstractClientPlayerEntityAccessor) fakePlayer).setPlayerListEntry(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(profile.getId()));
        }catch(Exception e){
            Logger.getAnonymousLogger().info("BLABBER failed to setPlayerListEntry");
            System.out.println(e.getMessage());
        }
        fakePlayer.prevBodyYaw = fakePlayer.bodyYaw = 0.0f;
        fakePlayer.prevHeadYaw = fakePlayer.headYaw = 0.0f;
        DialogueIllustrationFakePlayer.PlayerModelOptions playerModelOptions = this.illustration.modelOptionsOrDefault();
        fakePlayer.getDataTracker().set(PlayerEntityAccessor.getPlayerModelParts(), playerModelOptions.packVisibleParts());
        fakePlayer.setMainArm(playerModelOptions.mainHand());
        return fakePlayer;
    }
}
