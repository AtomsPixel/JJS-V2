package net.jjs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Tiny server-side scheduler. */
public final class Effects {
    private static final List<Task> TASKS = new ArrayList<>();

    private static class Task {
        final int total; int age = 0; final IntConsumer fn;
        Task(int total, IntConsumer fn) { this.total = total; this.fn = fn; }
    }

    public static void run(int duration, IntConsumer perTick) { TASKS.add(new Task(duration, perTick)); }

    public static void later(int ticks, Runnable r) { run(ticks + 1, age -> { if (age == ticks) r.run(); }); }

    public static void tick() {
        if (TASKS.isEmpty()) return;
        for (Task t : new ArrayList<>(TASKS)) {
            try { t.fn.accept(t.age); } catch (Exception e) { e.printStackTrace(); t.age = t.total; }
            t.age++;
            if (t.age >= t.total) TASKS.remove(t);
        }
    }

    public static void clear() { TASKS.clear(); }
    private Effects() {}
}
