package com.nuclyon.technicallycoded.inventoryrollback.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Cross-platform scheduler abstraction that uses the Paper Scheduler API
 * (io.papermc.paper.threadedregions.scheduler) when available - which is required for Folia support -
 * and falls back to the Bukkit scheduler on plain Spigot/CraftBukkit servers.
 */
public final class SchedulerUtil {

    private static final boolean PAPER_SCHEDULER_AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            available = true;
        } catch (ClassNotFoundException ignored) {
            available = false;
        }
        PAPER_SCHEDULER_AVAILABLE = available;
    }

    private SchedulerUtil() {
    }

    public static boolean isPaperSchedulerAvailable() {
        return PAPER_SCHEDULER_AVAILABLE;
    }

    /**
     * Run a task on the main server thread (or the global region on Folia/Paper).
     */
    public static void runTask(Plugin plugin, Runnable runnable) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Run a task on the main server thread after the given delay in ticks.
     */
    public static void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    /**
     * Run a task asynchronously.
     */
    public static void runTaskAsynchronously(Plugin plugin, Runnable runnable) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    /**
     * Run a task asynchronously after the given delay in ticks.
     */
    public static void runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delayTicks) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> runnable.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        }
    }

    /**
     * Run a repeating task on the main server thread. The task may cancel itself via {@link Cancellable#cancel()}.
     */
    public static void runTaskTimer(Plugin plugin, Consumer<Cancellable> task, long delayTicks, long periodTicks) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            if (delayTicks <= 0) {
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                    boolean[] cancelled = {false};
                    task.accept(() -> cancelled[0] = true);
                    if (!cancelled[0]) {
                        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                                s -> task.accept(s::cancel), periodTicks, periodTicks);
                    }
                });
            } else {
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.accept(scheduledTask::cancel), delayTicks, periodTicks);
            }
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    task.accept(this::cancel);
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    /**
     * Cancel all tasks scheduled by the given plugin.
     */
    public static void cancelTasks(Plugin plugin) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    /**
     * Run a callable on the main server thread and return a {@link Future} for its result.
     * This is the equivalent of {@code BukkitScheduler#callSyncMethod}.
     */
    public static <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> callable) {
        if (PAPER_SCHEDULER_AVAILABLE) {
            CompletableFuture<T> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                try {
                    future.complete(callable.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } else {
            return Bukkit.getScheduler().callSyncMethod(plugin, callable);
        }
    }

    public interface Cancellable {
        void cancel();
    }

}
