package org.synanton.equalix.adapter.in.rest;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import jakarta.validation.Valid;
import java.util.UUID;
import org.synanton.equalix.adapter.in.rest.dto.CreateTaskRequest;
import org.synanton.equalix.domain.model.Task;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;
import jakarta.inject.Inject;

@Controller("/api/v1/tasks")
public class TaskIngestionController {

    @Inject
    public TaskIngestionController(TaskIngestionPort ingestionPort) {
        this.ingestionPort = ingestionPort;
    }

    private final TaskIngestionPort ingestionPort;

    @Post
    @Status(HttpStatus.CREATED)
    public UUID createTask(@Valid @Body CreateTaskRequest request) {
        Task task = ingestionPort.createTask(
            request.getFairnessKey(),
            request.getWeight(),
            request.getPayload(),
            request.isSequential(),
            request.getSequenceNumber(),
            request.getDependsOnTaskId(),
            request.isRequiresPreviousResult()
        );
        return task.getId();
    }
}
