package org.synanton.equalix.adapter.in.rest;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;
import java.util.UUID;
import org.synanton.equalix.adapter.in.rest.dto.CompleteTaskRequest;
import org.synanton.equalix.domain.port.in.TaskCompletionPort;
import jakarta.inject.Inject;

@Controller("/api/v1/tasks")
public class TaskCompletionController {

    @Inject
    public TaskCompletionController(TaskCompletionPort completionPort) {
        this.completionPort = completionPort;
    }

    private final TaskCompletionPort completionPort;

    @Post("/{taskId}/complete")
    public void completeTask(
        @PathVariable UUID taskId,
        @Valid @Body CompleteTaskRequest request
    ) {
        completionPort.completeTask(taskId, request.isSuccess(), request.getResult(), request.getError());
    }
}
