package com.siberalt.singularity.scheduler.simulation;

import java.time.Instant;
import java.util.*;

public class TaskExecutionTable {
    protected SortedMap<Instant, Set<UUID>> schedulesByTime = new TreeMap<>();
    protected Map<UUID, Runnable> tasksByScheduleId = new HashMap<>();

    public void registerTask(UUID scheduleId, Runnable task) {
        tasksByScheduleId.put(scheduleId, task);
    }

    public void unregisterTask(UUID scheduleId) {
        schedulesByTime.entrySet().removeIf(entry -> {
            entry.getValue().remove(scheduleId);
            return entry.getValue().isEmpty();
        });
        tasksByScheduleId.remove(scheduleId);
    }

    public void unregisterTasks() {
        schedulesByTime.clear();
        tasksByScheduleId.clear();
    }

    public void cancelExecution(Instant time) {
        schedulesByTime.remove(time);
    }

    public void cancelExecution(UUID scheduleId) {
        schedulesByTime.entrySet().removeIf(entry -> {
            entry.getValue().remove(scheduleId);
            return entry.getValue().isEmpty();
        });
    }

    public Runnable getTask(UUID scheduleId) {
        return tasksByScheduleId.get(scheduleId);
    }

    public void finishPlannedExecutions(Instant time) {
        schedulesByTime.remove(time);
    }

    public Set<UUID> getPlannedExecutions(Instant executionTime) {
        return schedulesByTime.getOrDefault(executionTime, Collections.emptySet());
    }

    public boolean hasPlannedExecutions(Instant executionTime) {
        return schedulesByTime.containsKey(executionTime);
    }

    public boolean isExecutionPlanned(UUID scheduleId) {
        return tasksByScheduleId.containsKey(scheduleId);
    }

    public boolean isExecutionPlanned(Instant executionTime, UUID scheduleId) {
        return schedulesByTime.getOrDefault(executionTime, Collections.emptySet()).contains(scheduleId);
    }

    public void planExecution(Instant executionTime, UUID scheduleId) {
        if (executionTime == null) {
            throw new IllegalArgumentException("Execution time cannot be null");
        }
        if (!tasksByScheduleId.containsKey(scheduleId)) {
            throw new IllegalArgumentException("Task with scheduleId " + scheduleId + " not found");
        }

        schedulesByTime.computeIfAbsent(executionTime, k -> new HashSet<>()).add(scheduleId);
    }
}
