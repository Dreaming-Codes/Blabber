/*
 * Blabber
 * Copyright (C) 2022-2023 Ladysnake
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
package io.github.ladysnake.blabber.impl.client;

import com.google.common.collect.ImmutableList;
import io.github.ladysnake.blabber.impl.common.ChoiceResult;
import io.github.ladysnake.blabber.impl.common.DialogueScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class BlabberDialogueScreen extends HandledScreen<DialogueScreenHandler> {
    public static final int MIN_RENDER_Y = 40;
    public static final int TITLE_GAP = 20;
    public static final int CHOICE_GAP = 5;
    public static final int MAX_TEXT_WIDTH = 300;

    private int selectedChoice;
    private boolean hoveringChoice;

    public BlabberDialogueScreen(DialogueScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.handler.isUnskippable();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hoveringChoice) {
            this.confirmChoice(this.selectedChoice);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        GameOptions options = MinecraftClient.getInstance().options;
        if (key == GLFW.GLFW_KEY_ENTER || options.inventoryKey.matchesKey(key, scancode)) {
            this.confirmChoice(this.selectedChoice);
            return true;
        }
        boolean tab = GLFW.GLFW_KEY_TAB == key;
        boolean down = options.backKey.matchesKey(key, scancode);
        boolean shift = (GLFW.GLFW_MOD_SHIFT & modifiers) != 0;
        if (tab || down || options.forwardKey.matchesKey(key, scancode)) {
            scrollDialogueChoice(tab && !shift || down ? -1 : 1);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    private ChoiceResult confirmChoice(int selectedChoice) {
        assert this.client != null;
        ChoiceResult result = this.handler.makeChoice(selectedChoice);

        switch (result) {
            case END_DIALOGUE -> this.client.setScreen(null);
            case ASK_CONFIRMATION -> {
                ImmutableList<Text> choices = this.handler.getCurrentChoices();
                this.client.setScreen(new ConfirmScreen(
                    this::onBigChoiceMade,
                    this.handler.getCurrentText(),
                    Text.empty(),
                    choices.get(0),
                    choices.get(1)
                ));
            }
            default -> this.selectedChoice = 0;
        }

        return result;
    }

    private void onBigChoiceMade(boolean yes) {
        assert client != null;
        if (this.confirmChoice(yes ? 0 : 1) == ChoiceResult.DEFAULT) {
            this.client.setScreen(this);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollDialogueChoice(verticalAmount);
        return true;
    }

    private void scrollDialogueChoice(double scrollAmount) {
        if (!this.handler.getCurrentChoices().isEmpty()) {
            this.selectedChoice = Math.floorMod((int) (this.selectedChoice - scrollAmount), this.handler.getCurrentChoices().size());
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        List<Text> choices = this.handler.getCurrentChoices();
        Text title = this.handler.getCurrentText();
        int y = MIN_RENDER_Y + this.getTextBoundedHeight(title, MAX_TEXT_WIDTH) + TITLE_GAP;
        for (int i = 0; i < choices.size(); i++) {
            Text choice = choices.get(i);
            int strHeight = this.getTextBoundedHeight(choice, width);
            int strWidth = strHeight == 9 ? this.textRenderer.getWidth(choice) : width;
            if (mouseX < strWidth && mouseY > y && mouseY < y + strHeight) {
                this.selectedChoice = i;
                this.hoveringChoice = true;
                return;
            }
            y += strHeight + CHOICE_GAP;
            this.hoveringChoice = false;
        }
    }

    private int getTextBoundedHeight(Text text, int maxWidth) {
        return 9 * this.textRenderer.wrapLines(text, maxWidth).size();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        super.render(context, mouseX, mouseY, tickDelta);

        assert client != null;

        int y = MIN_RENDER_Y;
        Text title = this.handler.getCurrentText();

        context.drawTextWrapped(this.textRenderer, title, 10, y, MAX_TEXT_WIDTH, 0xFFFFFF);
        y += this.getTextBoundedHeight(title, MAX_TEXT_WIDTH) + TITLE_GAP;
        List<Text> choices = this.handler.getCurrentChoices();

        for (int i = 0; i < choices.size(); i++) {
            Text choice = choices.get(i);
            int strHeight = this.getTextBoundedHeight(choice, MAX_TEXT_WIDTH);
            context.drawTextWrapped(this.textRenderer, choice, 10, y, MAX_TEXT_WIDTH, i == this.selectedChoice ? 0xE0E044 : 0xA0A0A0);
            y += strHeight + CHOICE_GAP;
        }

        Text tip = Text.translatable("blabber:dialogue.instructions", client.options.forwardKey.getBoundKeyLocalizedText(), client.options.backKey.getBoundKeyLocalizedText(), client.options.inventoryKey.getBoundKeyLocalizedText());
        context.drawText(this.textRenderer, tip, (this.width - this.textRenderer.getWidth(tip)) / 2, this.height - 30, 0x808080, false);
    }

    @Override
    protected void drawBackground(DrawContext matrices, float delta, int mouseX, int mouseY) {
        // NO-OP
    }

    @Override
    protected void drawForeground(DrawContext matrices, int mouseX, int mouseY) {
        // NO-OP
    }
}
