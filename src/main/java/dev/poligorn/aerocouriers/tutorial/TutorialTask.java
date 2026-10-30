package dev.poligorn.aerocouriers.tutorial;

import dev.poligorn.aerocouriers.task.TaskView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** The four onboarding steps as a {@link TaskView} (teal in the panel, same data as the Profile checklist). */
final class TutorialTask {
    static final int STEPS = 4;

    private TutorialTask() {
    }

    static TaskView of(int current, Optional<UUID> orderId) {
        List<TaskView.Step> steps = new ArrayList<>(STEPS);
        for (int i = 0; i < STEPS; i++) {
            TaskView.StepState state = i < current ? TaskView.StepState.DONE
                    : i == current ? TaskView.StepState.CURRENT : TaskView.StepState.TODO;
            int zone = i == 2 ? 1 : i == 3 ? 2 : 0;
            steps.add(new TaskView.Step(Component.translatable("tutorial.aerocouriers.step." + (i + 1)), state, zone));
        }
        return new TaskView("tutorial", TaskView.Source.TUTORIAL,
                Component.translatable("tutorial.aerocouriers.task.title", current, STEPS), steps,
                current / (float) STEPS, -1L, false, false, false,
                orderId.isPresent() ? TaskView.Action.SHOW_ON_MAP : TaskView.Action.NONE, orderId, 0L, false);
    }
}
