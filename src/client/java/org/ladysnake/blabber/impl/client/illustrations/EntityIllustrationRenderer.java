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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ladysnake.blabber.api.client.illustration.DialogueIllustrationRenderer;
import org.ladysnake.blabber.impl.common.illustrations.PositionTransform;
import org.ladysnake.blabber.impl.common.illustrations.entity.DialogueIllustrationEntity;
import org.ladysnake.blabber.impl.common.illustrations.entity.StareTarget;

public abstract class EntityIllustrationRenderer<I extends DialogueIllustrationEntity> extends DialogueIllustrationRenderer<I> {
    private @Nullable LivingEntity renderedEntity;

    public EntityIllustrationRenderer(I illustration) {
        super(illustration);
    }

    protected abstract @Nullable LivingEntity getRenderedEntity(World world);


    @Override
    @Environment(EnvType.CLIENT)
    public void render(MatrixStack context, TextRenderer textRenderer, PositionTransform positionTransform, int mouseX, int mouseY, float tickDelta) {
        LivingEntity e = this.renderedEntity == null
                ? this.renderedEntity = this.getRenderedEntity(MinecraftClient.getInstance().world)
                : this.renderedEntity;

        if (e == null) return; // Something went wrong creating the entity, so don't render.

        int x1 = illustration.minX(positionTransform);
        int y1 = illustration.minY(positionTransform);
        int x2 = illustration.maxX(positionTransform);
        int y2 = illustration.maxY(positionTransform);

        StareTarget stareTarget = illustration.stareAt();
        int fakedMouseX = stareTarget.x().isPresent() ? stareTarget.anchor().isPresent() ? positionTransform.transformX(stareTarget.anchor().get(), stareTarget.x().getAsInt()) : stareTarget.x().getAsInt() + (x1 + x2) / 2 : mouseX;
        int fakedMouseY = stareTarget.y().isPresent() ? stareTarget.anchor().isPresent() ? positionTransform.transformY(stareTarget.anchor().get(), stareTarget.y().getAsInt()) : stareTarget.y().getAsInt() + (y1 + y2) / 2 : mouseY;

        drawEntity(context,
                x1,
                y1,
                x2,
                y2,
                illustration.entitySize(),
                illustration.yOffset(),
                fakedMouseX,
                fakedMouseY,
                e);
    }

    private void drawEntity(MatrixStack context, int x1, int y1, int x2, int y2, int size, float f, float mouseX, float mouseY, LivingEntity entity) {
        float g = (float)(x1 + x2) / 2.0F;
        float h = (float)(y1 + y2) / 2.0F;
        DrawableHelper.enableScissor(x1,y1,x2,y2);
        float i = (float)Math.atan((g - mouseX) / 40.0F);
        float j = (float)Math.atan((h - mouseY) / 40.0F);
        Quaternion quaternionf = EntityIllustrationRenderer.rotateZ(3.1415927F);
        Quaternion quaternionf2 = EntityIllustrationRenderer.rotateX(j * 20.0F * 0.017453292F);
        quaternionf.hamiltonProduct(quaternionf2);
        float k = entity.bodyYaw;
        float l = entity.getYaw();
        float m = entity.getPitch();
        float n = entity.prevHeadYaw;
        float o = entity.headYaw;
        entity.bodyYaw = 180.0F + i * 20.0F;
        entity.setYaw(180.0F + i * 40.0F);
        entity.setPitch(-j * 20.0F);
        entity.headYaw = entity.getYaw();
        entity.prevHeadYaw = entity.getYaw();
        float p = 1;
        Vector3f vector3f = new Vector3f(0.0F, entity.getHeight() / 2.0F + f * p, 0.0F);
        float q = (float)size / p;
        drawEntity(context, g, h, q, vector3f, quaternionf, quaternionf2, entity);
        entity.bodyYaw = k;
        entity.setYaw(l);
        entity.setPitch(m);
        entity.prevHeadYaw = n;
        entity.headYaw = o;
        DrawableHelper.disableScissor();
    }

    private void drawEntity(MatrixStack context, float x, float y, float size, Vector3f vector3f, Quaternion quaternionf, @Nullable Quaternion quaternionf2, LivingEntity entity) {
        context.push();
        context.translate(x, y, 50.0);
        context.multiplyPositionMatrix(Matrix4f.scale(size,size,-size));
        context.translate(vector3f.x, vector3f.y, vector3f.z);
        context.multiply(quaternionf);
        DiffuseLighting.method_34742();
        EntityRenderDispatcher entityRenderDispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (quaternionf2 != null) {
            quaternionf2.conjugate();
            entityRenderDispatcher.setRotation(quaternionf2);
        }

        entityRenderDispatcher.setRenderShadows(false);
        RenderSystem.runAsFancy(() -> {

            entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, context, MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(), 15728880);
        });
        RenderSystem.disableDepthTest();
        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
        RenderSystem.enableDepthTest();
        entityRenderDispatcher.setRenderShadows(true);
        context.pop();
        DiffuseLighting.enableGuiDepthLighting();
    }

    private static Quaternion rotateX(float v) {
        float sin = org.joml.Math.sin(v * 0.5f);
        float cos = org.joml.Math.cosFromSin(sin, v * 0.5f);
        Quaternion q= new Quaternion(Quaternion.IDENTITY);
        q.set(q.getW() * sin + q.getX() * cos,
                q.getY() * cos + q.getZ() * sin,
                q.getZ() * cos - q.getY() * sin,
                q.getW() * cos - q.getX() * sin);
        return q;
    }

    private static Quaternion rotateZ(float v) {
        float sin = org.joml.Math.sin(v * 0.5f);
        float cos = org.joml.Math.cosFromSin(sin, v * 0.5f);
        Quaternion q= new Quaternion(Quaternion.IDENTITY);
        q.set(q.getX() * cos + q.getY() * sin,
                q.getY() * cos - q.getX() * sin,
                q.getW() * sin + q.getZ() * cos,
                q.getW() * cos - q.getZ() * sin);
        return q;
    }

    /*public static void drawEntity(int x1, int y1,int x2, int y2, int size, float i, float mouseX, float mouseY, LivingEntity entity) {
        float j = (float)(x1 + x2) / 2.0F;
        float k = (float)(y1 + y2) / 2.0F;
        //RenderSystem.enableScissor(x1, y1, x2 - x1, y2 - y1);
        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.translate((double)j, (double)k, 50.0);
        RenderSystem.applyModelViewMatrix();
        MatrixStack matrixStack2 = new MatrixStack();
        matrixStack2.scale((float)size, (float)size, -(float)size);
        matrixStack2.translate(0.0, entity.getHeight()/ 2.0F + 2 * i, 0.0);
        Quaternion quaternion = Vec3f.POSITIVE_Z.getDegreesQuaternion(180.0F);
        Quaternion quaternion2 = Vec3f.POSITIVE_X.getDegreesQuaternion(j * 20.0F*0.017453292F);
        quaternion.hamiltonProduct(quaternion2);
        matrixStack2.multiply(quaternion);
        float bodyYaw = entity.bodyYaw;
        float l = entity.getYaw();
        float m = entity.getPitch();
        float n = entity.prevHeadYaw;
        float o = entity.headYaw;

        float b1 = (float)Math.atan((j - mouseX) / 40.0F);
        float b2 = (float)Math.atan((k - mouseY) / 40.0F);
        entity.bodyYaw = 180.0F + b1 * 20.0F;
        entity.setYaw(180.0F + b1 * 40.0F);
        entity.setPitch(-b2 * 20.0F);
        entity.headYaw = entity.getYaw();
        entity.prevHeadYaw = entity.getYaw();
        DiffuseLighting.method_34742();
        EntityRenderDispatcher entityRenderDispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        quaternion2.conjugate();
        entityRenderDispatcher.setRotation(quaternion2);
        entityRenderDispatcher.setRenderShadows(false);
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderSystem.runAsFancy(() -> {
            entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, matrixStack2, immediate, 15728880);
        });
        immediate.draw();
        entityRenderDispatcher.setRenderShadows(true);
        entity.bodyYaw = bodyYaw;
        entity.setYaw(l);
        entity.setPitch(m);
        entity.prevHeadYaw = n;
        entity.headYaw = o;
        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
        DiffuseLighting.enableGuiDepthLighting();
        //RenderSystem.disableScissor();
    }*/
}
