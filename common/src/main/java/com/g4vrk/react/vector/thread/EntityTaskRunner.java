package com.g4vrk.react.vector.thread;

import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class EntityTaskRunner {

    private static final Method GET_SCHEDULER = findGetScheduler();
    private static final Method RUN = findRun();

    private final Plugin plugin;
    private final Scheduler fallback;

    public EntityTaskRunner(final @NotNull Plugin plugin, final @NotNull Scheduler fallback) {
        this.plugin = plugin;
        this.fallback = fallback;
    }

    public void run(final @NotNull Entity entity, final @NotNull Runnable task) {
        if (!scheduleOnEntity(entity, task)) {
            fallback.scheduleEntity(entity, task, TickSchedule.instant());
        }
    }

    private boolean scheduleOnEntity(final Entity entity, final Runnable task) {
        if (GET_SCHEDULER == null || RUN == null) {
            return false;
        }
        final Consumer<Object> action = ignored -> task.run();
        final Runnable retired = () -> {
        };
        try {
            RUN.invoke(GET_SCHEDULER.invoke(entity), plugin, action, retired);
            return true;
        } catch (final IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return false;
        }
    }

    private static Method findGetScheduler() {
        try {
            return Entity.class.getMethod("getScheduler");
        } catch (final NoSuchMethodException exception) {
            return null;
        }
    }

    private static Method findRun() {
        try {
            return Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler")
                    .getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        } catch (final ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

}
