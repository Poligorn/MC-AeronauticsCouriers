package dev.poligorn.aerocouriers.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One entry of the shared task list. Computed on the server from contracts, inbox, profile and tutorial state; only
 * the player's tracked / pinned choice is stored. Timers ({@code deadline}) and distances are computed client-side.
 */
public record TaskView(
        String id,
        Source source,
        Component title,
        List<Step> steps,
        float progress,
        long deadline,
        boolean urgent,
        boolean tracked,
        boolean pinned,
        Action action,
        Optional<UUID> contractId,
        long createdAt,
        boolean done
) {
    public static final int MAX_TASKS = 64;
    public static final int MAX_STEPS = 8;

    public enum Source {
        ORDER_COURIER, ORDER_CUSTOMER, TUTORIAL, RANK_GOAL, MAIL;

        static Source byOrdinal(int i) {
            Source[] all = values();
            return i >= 0 && i < all.length ? all[i] : ORDER_COURIER;
        }
    }

    public enum Action {
        NONE, SHOW_ON_MAP, OPEN_ORDER, OPEN_MAIL;

        static Action byOrdinal(int i) {
            Action[] all = values();
            return i >= 0 && i < all.length ? all[i] : NONE;
        }
    }

    public enum StepState { DONE, CURRENT, TODO }

    /** {@code zone}: 0 = none, 1 = pickup A, 2 = delivery B (client adds the live distance from its zones). */
    public record Step(Component text, StepState state, int zone) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Step> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    ComponentSerialization.STREAM_CODEC.encode(buf, s.text());
                    ByteBufCodecs.VAR_INT.encode(buf, s.state().ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf, s.zone());
                },
                buf -> new Step(ComponentSerialization.STREAM_CODEC.decode(buf),
                        StepState.values()[Math.floorMod(ByteBufCodecs.VAR_INT.decode(buf), StepState.values().length)],
                        ByteBufCodecs.VAR_INT.decode(buf)));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TaskView> STREAM_CODEC = StreamCodec.of(
            (buf, t) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, t.id());
                ByteBufCodecs.VAR_INT.encode(buf, t.source().ordinal());
                ComponentSerialization.STREAM_CODEC.encode(buf, t.title());
                Step.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STEPS)).encode(buf, t.steps());
                ByteBufCodecs.FLOAT.encode(buf, t.progress());
                ByteBufCodecs.VAR_LONG.encode(buf, t.deadline());
                ByteBufCodecs.BOOL.encode(buf, t.urgent());
                ByteBufCodecs.BOOL.encode(buf, t.tracked());
                ByteBufCodecs.BOOL.encode(buf, t.pinned());
                ByteBufCodecs.VAR_INT.encode(buf, t.action().ordinal());
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).encode(buf, t.contractId());
                ByteBufCodecs.VAR_LONG.encode(buf, t.createdAt());
                ByteBufCodecs.BOOL.encode(buf, t.done());
            },
            buf -> new TaskView(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    Source.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    Step.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STEPS)).decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    Action.byOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC).decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    public TaskView withPrefs(boolean tracked, boolean pinned) {
        return new TaskView(id, source, title, steps, progress, deadline, urgent, tracked, pinned, action, contractId,
                createdAt, done);
    }

    /** Index of the first CURRENT step, else the first TODO, else -1. */
    public int currentStep() {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).state() == StepState.CURRENT) {
                return i;
            }
        }
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).state() == StepState.TODO) {
                return i;
            }
        }
        return -1;
    }

    public int doneSteps() {
        int n = 0;
        for (Step step : steps) {
            if (step.state() == StepState.DONE) {
                n++;
            }
        }
        return n;
    }

    static List<Step> steps(int current, Component... texts) {
        List<Step> out = new ArrayList<>(texts.length);
        for (int i = 0; i < texts.length; i++) {
            StepState state = i < current ? StepState.DONE : i == current ? StepState.CURRENT : StepState.TODO;
            out.add(new Step(texts[i], state, 0));
        }
        return out;
    }
}
