package org.synanton.equalix.adapter.in.rest;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.synanton.equalix.adapter.in.rest.dto.SystemStatusResponse;
import org.synanton.equalix.domain.port.out.CMSProviderPort;
import org.synanton.equalix.domain.service.AdaptiveRpsController;
import jakarta.inject.Inject;

@Controller("/api/v1/status")
public class SystemStatusController {

    @Inject
    public SystemStatusController(CMSProviderPort cms, AdaptiveRpsController adaptiveRpsController) {
        this.cms = cms;
        this.adaptiveRpsController = adaptiveRpsController;
    }

    private final CMSProviderPort cms;
    private final AdaptiveRpsController adaptiveRpsController;

    @Get
    public SystemStatusResponse getStatus() {
        return new SystemStatusResponse(cms.totalInFlight(), adaptiveRpsController.getCurrentRps());
    }
}
