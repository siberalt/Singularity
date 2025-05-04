package com.siberalt.singularity.scheduler.simulation;

import java.time.Instant;
import java.util.*;

public class TaskExecutionTable<idType> {
    protected SortedMap<Instant, Set<idType>> schedulesByTime = new TreeMap<>();
    protected Map<idType, Runnable> tasksByScheduleId = new HashMap<>();

    public void registerTask(idType scheduleId, Runnable task) {
        tasksByScheduleId.put(scheduleId, task);
    }

    public void unregisterTask(idType scheduleId) {
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

    public void cancelExecution(idType scheduleId) {
        schedulesByTime.entrySet().removeIf(entry -> {
            entry.getValue().remove(scheduleId);
            return entry.getValue().isEmpty();
        });
    }

    public Runnable getTask(idType scheduleId) {
        return tasksByScheduleId.get(scheduleId);
    }

    public void finishPlannedExecutions(Instant time) {
        schedulesByTime.remove(time);
    }

    public Set<idType> getPlannedExecutions(Instant executionTime) {
        return schedulesByTime.getOrDefault(executionTime, Collections.emptySet());
    }

    public boolean hasPlannedExecutions(Instant executionTime) {
        return schedulesByTime.containsKey(executionTime);
    }

    public boolean isExecutionPlanned(idType scheduleId) {
        return tasksByScheduleId.containsKey(scheduleId);
    }

    public boolean isExecutionPlanned(Instant executionTime, idType scheduleId) {
        return schedulesByTime.getOrDefault(executionTime, Collections.emptySet()).contains(scheduleId);
    }

    public void planExecution(Instant executionTime, idType scheduleId) {
        if (executionTime == null) {
            throw new IllegalArgumentException("Execution time cannot be null");
        }
        if (!tasksByScheduleId.containsKey(scheduleId)) {
            throw new IllegalArgumentException("Task with scheduleId " + scheduleId + " not found");
        }

        schedulesByTime.computeIfAbsent(executionTime, k -> new HashSet<>()).add(scheduleId);
    }
}
