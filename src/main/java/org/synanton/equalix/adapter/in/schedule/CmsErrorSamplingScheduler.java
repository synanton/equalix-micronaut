package org.synanton.equalix.adapter.in.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.synanton.equalix.domain.service.CmsErrorRecorder;

@Singleton
@Requires(property = "app.scheduling.enabled", value = "true", defaultValue = "true")
@Requires(property = "app.queue.cms.error-sampling.enabled", value = "true")
public class CmsErrorSamplingScheduler {

    @Inject
    public CmsErrorSamplingScheduler(CmsErrorRecorder cmsErrorRecorder) {
        this.cmsErrorRecorder = cmsErrorRecorder;
    }

    private final CmsErrorRecorder cmsErrorRecorder;

    @Scheduled(fixedDelay = "${app.queue.cms.error-sampling.interval-ms:1000}ms")
    public void run() {
        cmsErrorRecorder.sample();
    }
}
