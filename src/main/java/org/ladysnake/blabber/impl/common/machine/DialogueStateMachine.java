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
package org.ladysnake.blabber.impl.common.machine;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.blabber.Blabber;
import org.ladysnake.blabber.api.DialogueActionV2;
import org.ladysnake.blabber.api.illustration.DialogueIllustration;
import org.ladysnake.blabber.api.layout.DialogueLayout;
import org.ladysnake.blabber.impl.common.InstancedDialogueAction;
import org.ladysnake.blabber.impl.common.model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class DialogueStateMachine {
    private static final DynamicCommandExceptionType INVALID_PREDICATE_EXCEPTION = new DynamicCommandExceptionType(id -> Text.translatable("blabber:commands.dialogue.start.predicate.invalid", String.valueOf(id)));

    private final Identifier id;
    private final DialogueTemplate template;
    private final Map<String, Int2BooleanMap> conditionalChoices;
    private @Nullable String currentStateKey;
    private ImmutableList<AvailableChoice> availableChoices = ImmutableList.of();
    private int textPicked=-2;
    private @Nullable String stateLog;

    public DialogueStateMachine(Identifier id, DialogueTemplate template, @Nullable String start) {
        this.template = template;
        this.id = id;
        this.conditionalChoices = gatherConditionalChoices(template);
        this.selectState(start == null ? template.start() : start);
    }

    public DialogueStateMachine(PacketByteBuf buf) {
        this(buf.readIdentifier(), new DialogueTemplate(buf), buf.readString());
    }

    private static Map<String, Int2BooleanMap> gatherConditionalChoices(DialogueTemplate template) {
        Map<String, Int2BooleanMap> conditionalChoices = new HashMap<>();
        for (Map.Entry<String, DialogueState> entry : template.states().entrySet()) {
            List<DialogueChoice> choices = entry.getValue().choices();
            Int2BooleanMap m = new Int2BooleanOpenHashMap();
            for (int i = 0; i < choices.size(); i++) {
                DialogueChoice choice = choices.get(i);
                if (choice.condition().isPresent()) {
                    m.put(i, false);
                }
            }
            if (!m.isEmpty()) {
                conditionalChoices.put(entry.getKey(), m);
            }
        }
        return conditionalChoices;
    }

    public static void writeToPacket(PacketByteBuf buf, DialogueStateMachine dialogue) {
        buf.writeIdentifier(dialogue.getId());
        DialogueTemplate.writeToPacket(buf, dialogue.template);
        buf.writeString(dialogue.getCurrentStateKey());
    }

    private static boolean runTest(LootCondition condition, LootContext context) {
        // MH
        return condition.test(context);
    }

    private static Optional<Text> defaultLockedMessage() {
        return Optional.of(Text.translatable("blabber:dialogue.locked_choice"));
    }

    private Map<String, DialogueState> getStates() {
        return this.template.states();
    }

    private DialogueState getCurrentState() {
        return getStates().get(this.getCurrentStateKey());
    }

    public Identifier getId() {
        return this.id;
    }

    public DialogueLayout<?> getLayout() {
        return this.template.layout();
    }

    public Text getCurrentText() {
        if(!Objects.equals(stateLog, this.currentStateKey)){
            textPicked= -2;
            stateLog = this.currentStateKey;
        }
        if(textPicked==-2) {
            switch (this.getCurrentState().text().size()) {
                case 0:
                    textPicked = -1;
                    break;
                case 1:
                    textPicked = 0;
                    break;
                default:
                    textPicked = new Random().nextInt(this.getCurrentState().text().size());
                    break;
            }
        }
        if (textPicked == -1) {
            return Text.literal("");
        }
        return this.getCurrentState().text().get(textPicked).text();

    }

    public List<String> getCurrentIllustrations() {
        return this.getCurrentState().illustrations();
    }

    public Map<String, DialogueIllustration> getIllustrations() {
        return this.template.illustrations();
    }

    public ImmutableList<AvailableChoice> getAvailableChoices() {
        return this.availableChoices;
    }

    public boolean hasConditions() {
        return !this.conditionalChoices.isEmpty();
    }

    public @NotNull PacketByteBuf updateConditions(LootContext context) throws CommandSyntaxException {
        Map<String, Int2BooleanMap> updatedChoice = new HashMap<>();
        for (Map.Entry<String, Int2BooleanMap> conditionalState : this.conditionalChoices.entrySet()) {
            List<DialogueChoice> availableChoices = getStates().get(conditionalState.getKey()).choices();
            for (Int2BooleanMap.Entry conditionalChoice : conditionalState.getValue().int2BooleanEntrySet()) {
                Identifier predicateId = availableChoices.get(conditionalChoice.getIntKey()).condition().orElseThrow().predicate();
                LootCondition condition = context.getWorld().getServer().getPredicateManager().get(predicateId);
                if (condition == null) throw INVALID_PREDICATE_EXCEPTION.create(predicateId);
                boolean testResult = runTest(condition, context);
                if (testResult != conditionalChoice.setValue(testResult)) {
                    updatedChoice.putIfAbsent(conditionalState.getKey(), conditionalState.getValue());
                    updatedChoice.putIfAbsent(conditionalState.getKey(), new Int2BooleanOpenHashMap()).put(conditionalChoice.getIntKey(), testResult);
                }
            }
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeMap(
                updatedChoice,
                PacketByteBuf::writeString,
                (b, updatedChoices) -> b.writeMap(updatedChoices, PacketByteBuf::writeVarInt, PacketByteBuf::writeBoolean)
        );
        return out;
    }

    public Map<String, Int2BooleanMap> createFullAvailabilityUpdatePacket() {
        return this.conditionalChoices;
    }

    public void applyAvailabilityUpdate(PacketByteBuf payload) {
        Map<String, Int2BooleanMap> map = payload.readMap(
                PacketByteBuf::readString,
                b -> b.readMap(Int2BooleanOpenHashMap::new, PacketByteBuf::readVarInt, PacketByteBuf::readBoolean)
        );
        map.forEach((stateKey, choiceIndices) -> {
            Int2BooleanMap conditionalState = this.conditionalChoices.get(stateKey);
            for (Int2BooleanMap.Entry updatedChoice : choiceIndices.int2BooleanEntrySet()) {
                conditionalState.put(updatedChoice.getIntKey(), updatedChoice.getBooleanValue());
            }
            this.availableChoices = this.rebuildAvailableChoices();
        });
    }

    public boolean isAvailable(int choice) {
        return this.conditionalChoices.getOrDefault(this.currentStateKey, Int2BooleanMaps.EMPTY_MAP).getOrDefault(choice, true);
    }

    public Optional<InstancedDialogueAction<?>> getStartAction() {
        return this.getStates().get(this.template.start()).action();
    }

    /**
     * @throws IllegalStateException if making an invalid choice
     */
    public ChoiceResult choose(int choice, Consumer<DialogueActionV2> actionRunner) {
        if (choice == AvailableChoice.ESCAPE_HATCH.originalChoiceIndex() && IntStream.range(0, this.getCurrentState().choices().size()).noneMatch(this::isAvailable)) {
            Blabber.LOGGER.warn("(Blabber) Escape hatch used on {}#{}", this.getId(), this.currentStateKey);
            return ChoiceResult.END_DIALOGUE;
        }

        this.validateChoice(choice);
        DialogueState nextState = this.selectState(this.getCurrentState().getNextState(choice));
        nextState.action().map(InstancedDialogueAction::action).ifPresent(actionRunner);
        return nextState.type();
    }

    private void validateChoice(int choice) {
        if (choice < 0 || choice >= this.getCurrentState().choices().size()) {
            throw new IllegalStateException("only choices 0 to %d available".formatted(this.getCurrentState().choices().size() - 1));
        } else if (!this.isAvailable(choice)) {
            throw new IllegalStateException("condition %s is not fulfilled".formatted(this.getCurrentState().choices().get(choice).condition()));
        }
    }

    public DialogueState selectState(String state) {
        if (!this.getStates().containsKey(state)) {
            throw new IllegalArgumentException(state + " is not an available dialogue state");
        }
        this.currentStateKey = state;
        DialogueState currentState = this.getStates().get(state);
        this.availableChoices = rebuildAvailableChoices();
        return currentState;
    }

    private ImmutableList<AvailableChoice> rebuildAvailableChoices() {
        ImmutableList.Builder<AvailableChoice> newChoices = ImmutableList.builder();
        List<DialogueChoice> availableChoices = this.getCurrentState().choices();
        boolean allUnavailable = true;
        for (int i = 0; i < availableChoices.size(); i++) {
            DialogueChoice c = availableChoices.get(i);
            boolean available = conditionalChoices.getOrDefault(this.currentStateKey, Int2BooleanMaps.EMPTY_MAP).getOrDefault(i, true);
            Optional<UnavailableAction> whenUnavailable = c.condition().map(DialogueChoiceCondition::whenUnavailable);
            allUnavailable &= !available;
            if (available || (whenUnavailable.filter(t -> t.display() == UnavailableDisplay.GRAYED_OUT).isPresent())) {
                newChoices.add(new AvailableChoice(
                        i,
                        c.text(),
                        c.illustrations(),
                        whenUnavailable.filter(t -> !available).flatMap(a -> a.message().or(DialogueStateMachine::defaultLockedMessage))
                ));
            }
        }
        if (allUnavailable) {
            Blabber.LOGGER.warn("[Blabber] No choice available in state '{}' of {} ({} were all unavailable)", this.currentStateKey, this.id, availableChoices);
            newChoices.add(AvailableChoice.ESCAPE_HATCH);
        }
        return newChoices.build();
    }

    public String getCurrentStateKey() {
        return Objects.requireNonNull(this.currentStateKey, () -> this + " has not been initialized !");
    }

    public boolean isUnskippable() {
        return this.template.unskippable();
    }

    @Override
    public String toString() {
        return "DialogueStateMachine" + this.getStates();
    }
}
