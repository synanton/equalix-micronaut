package org.synanton.equalix.adapter.in.rest;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.synanton.equalix.adapter.in.rest.dto.TaskStatusResponse;
import org.synanton.equalix.domain.model.TaskStatus;
import org.synanton.equalix.domain.port.in.TaskManagementPort;
import jakarta.inject.Inject;

@Controller("/api/v1/tasks")
public class TaskManagementController {

    @Inject
    public TaskManagementController(TaskManagementPort managementPort) {
        this.managementPort = managementPort;
    }

    private final TaskManagementPort managementPort;

    @Get("/{taskId}")
    public TaskStatusResponse getTask(@PathVariable UUID taskId) {
        return TaskStatusResponse.from(managementPort.getTask(taskId));
    }

    @Get
    public List<TaskStatusResponse> getTasksByClient(
        @QueryValue String fairnessKey,
        @Nullable @QueryValue(value = "status") TaskStatus status
    ) {
        return managementPort.getTasksByFairnessKey(fairnessKey, status)
            .stream().map(TaskStatusResponse::from).toList();
    }
}
