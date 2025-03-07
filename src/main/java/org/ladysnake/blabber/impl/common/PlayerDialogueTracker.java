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
package org.ladysnake.blabber.impl.common;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.impl.common.machine.DialogueStateMachine;
import org.ladysnake.blabber.impl.common.model.DialogueTemplate;

import java.util.Optional;
import java.util.UUID;

public final class PlayerDialogueTracker implements ServerTickingComponent {
    public static final ComponentKey<PlayerDialogueTracker> KEY = ComponentRegistry.getOrCreate(Blabber.id("dialogue_tracker"), PlayerDialogueTracker.class);

    private final PlayerEntity player;
    private @Nullable DialogueStateMachine currentDialogue;
    private @Nullable Entity interlocutor;
    private @Nullable DeserializedState deserializedState;
    private int resumptionAttempts = 0;

    public PlayerDialogueTracker(PlayerEntity player) {
        this.player = player;
    }

    public static PlayerDialogueTracker get(PlayerEntity player) {
        return KEY.get(player);
    }

    public void startDialogue(Identifier id, @Nullable Entity interlocutor) throws CommandSyntaxException {
        DialogueTemplate template = DialogueRegistry.getOrEmpty(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dialogue " + id));
        DialogueStateMachine currentDialogue = this.startDialogue0(
                id,
                template,
                template.start(),
                interlocutor
        );
        currentDialogue.getStartAction().ifPresent(a -> a.action().handle((ServerPlayerEntity) this.player, interlocutor));
    }

    private DialogueStateMachine startDialogue0(Identifier id, DialogueTemplate template, @Nullable String start, @Nullable Entity interlocutor) throws CommandSyntaxException {
        ServerPlayerEntity serverPlayer = ((ServerPlayerEntity) this.player);
        this.interlocutor = interlocutor;
        try {
            DialogueTemplate parsedTemplate = template.parseText(CommandDialogueAction.getSource(serverPlayer), serverPlayer);
            this.currentDialogue = new DialogueStateMachine(id, parsedTemplate, start);
            this.updateConditions(serverPlayer, this.currentDialogue);
            this.openDialogueScreen();
            return this.currentDialogue;
        } catch (CommandSyntaxException e) {
            this.interlocutor = null;
            throw e;
        }
    }

    public void endDialogue() {
        this.currentDialogue = null;
        this.interlocutor = null;

        if (this.player instanceof ServerPlayerEntity sp && this.player.currentScreenHandler instanceof DialogueScreenHandler) {
            sp.closeHandledScreen();
        }
    }

    public Optional<DialogueStateMachine> getCurrentDialogue() {
        return Optional.ofNullable(this.currentDialogue);
    }

    public Optional<Entity> getInterlocutor() {
        return Optional.ofNullable(this.interlocutor);
    }

    public void updateDialogue() {
        DialogueStateMachine oldDialogue = this.currentDialogue;
        Entity oldInterlocutor = this.interlocutor;
        if (oldDialogue != null) {
            this.endDialogue();

            DialogueRegistry.getOrEmpty(oldDialogue.getId())
                    .ifPresent(template -> this.tryResumeDialogue(
                            oldDialogue.getId(),
                            template,
                            oldDialogue.getCurrentStateKey(),
                            oldInterlocutor
                    ));
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        if (tag.contains("current_dialogue_id", NbtElement.STRING_TYPE)) {
            Identifier dialogueId = Identifier.tryParse(tag.getString("current_dialogue_id"));
            if (dialogueId != null) {
                Optional<DialogueTemplate> dialogueTemplate = DialogueRegistry.getOrEmpty(dialogueId);
                if (dialogueTemplate.isPresent()) {
                    UUID interlocutorUuid = tag.containsUuid("interlocutor") ? tag.getUuid("interlocutor") : null;
                    String selectedState = tag.contains("current_dialogue_state", NbtElement.STRING_TYPE) ? tag.getString("current_dialogue_state") : null;
                    this.deserializedState = new DeserializedState(dialogueId, dialogueTemplate.get(), selectedState, interlocutorUuid);
                }
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        if (this.currentDialogue != null) {
            tag.putString("current_dialogue_id", this.currentDialogue.getId().toString());
            tag.putString("current_dialogue_state", this.currentDialogue.getCurrentStateKey());
            if (this.interlocutor != null) {
                tag.putUuid("interlocutor", this.interlocutor.getUuid());
            }
        }
    }

    @Override
    public void serverTick() {
        DeserializedState saved = this.deserializedState;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) this.player;
        if (saved != null) {
            if (resumptionAttempts++ < 200) {   // only try for like, 10 seconds after joining the world
                Entity interlocutor;
                if (saved.interlocutorUuid() != null) {
                    interlocutor = serverPlayer.getWorld().getEntity(saved.interlocutorUuid());
                    if (interlocutor == null) return;    // no one to talk to
                } else {
                    interlocutor = null;
                }
                tryResumeDialogue(saved.dialogueId(), saved.template(), saved.selectedState(), interlocutor);
            }
            this.resumptionAttempts = 0;
            this.deserializedState = null;
        }

        if (this.currentDialogue != null) {
            if(!this.player.isAlive()){
                this.endDialogue();
                return;
            }
            if (this.player.currentScreenHandler == this.player.playerScreenHandler) {
                if (this.currentDialogue.isUnskippable()) {
                    this.openDialogueScreen();
                } else {
                    this.endDialogue();
                    return;
                }
            }

            try {
                PacketByteBuf update = this.updateConditions(serverPlayer, this.currentDialogue);

                if (update != null) {
                    ServerPlayNetworking.send(serverPlayer, Blabber.id("choice_availability"), update);
                }
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("Error while updating dialogue conditions", e);
            }
        }
    }

    private void tryResumeDialogue(Identifier id, DialogueTemplate template, String selectedState, Entity interlocutor) {
        try {
            this.startDialogue0(id, template, selectedState, interlocutor);
        } catch (CommandSyntaxException e) {
            Blabber.LOGGER.error("(Blabber) Failed to load dialogue template {}", id, e);
        }
    }

    private @Nullable PacketByteBuf updateConditions(ServerPlayerEntity player, DialogueStateMachine currentDialogue) throws CommandSyntaxException {
        if (currentDialogue.hasConditions()) {
            return currentDialogue.updateConditions(new LootContext.Builder(player.getWorld())
                    .parameter(LootContextParameters.ORIGIN, player.getPos())
                    .optionalParameter(LootContextParameters.THIS_ENTITY, player)
                    .build(LootContextTypes.COMMAND));
        }
        return null;
    }

    private void openDialogueScreen() {
        Preconditions.checkState(this.currentDialogue != null);
        this.player.openHandledScreen(new DialogueScreenHandlerFactory(this.currentDialogue, Text.of("Blabber Dialogue Screen"), this.interlocutor));
    }

    private record DeserializedState(Identifier dialogueId, DialogueTemplate template, String selectedState,
                                     @Nullable UUID interlocutorUuid) {
    }
}
