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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.ladysnake.blabber.api.client.illustration.DialogueIllustrationRenderer;
import org.ladysnake.blabber.impl.common.illustrations.DialogueIllustrationTexture;
import org.ladysnake.blabber.impl.common.illustrations.PositionTransform;

public class TextureIllustrationRenderer extends DialogueIllustrationRenderer<DialogueIllustrationTexture> {
    public TextureIllustrationRenderer(DialogueIllustrationTexture illustration) {
        super(illustration);
    }

    @Override
    public void render(MatrixStack context, TextRenderer textRenderer, PositionTransform positionTransform, int mouseX, int mouseY, float tickDelta) {
        RenderSystem.setShaderTexture(0, illustration.texture());
        DrawableHelper.drawTexture(context,
                illustration.minX(positionTransform),
                illustration.minY(positionTransform),
                illustration.width(),
                illustration.height(),
                0,
                0,
                illustration.regionWidth().orElse(illustration.width()),
                illustration.regionHeight().orElse(illustration.height()),
                illustration.textureWidth().orElse(illustration.width()),
                illustration.textureHeight().orElse(illustration.height())
        );
    }

}
