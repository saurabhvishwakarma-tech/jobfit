package com.jobfit.application;

import com.jobfit.application.dto.*;
import com.jobfit.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Track jobs through Saved / Applied / Interview / Offer")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDetailResponse create(@Valid @RequestBody ApplicationCreateRequest request) {
        return applicationService.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping
    public List<ApplicationSummaryResponse> list() {
        return applicationService.list(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public ApplicationDetailResponse get(@PathVariable Long id) {
        return applicationService.getDetail(SecurityUtils.currentUserId(), id);
    }

    @PatchMapping("/{id}/status")
    public ApplicationDetailResponse updateStatus(@PathVariable Long id,
                                                   @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        return applicationService.updateStatus(SecurityUtils.currentUserId(), id, request);
    }

    @PatchMapping("/{id}")
    public ApplicationDetailResponse updateNotes(@PathVariable Long id,
                                                  @RequestBody ApplicationNotesUpdateRequest request) {
        return applicationService.updateNotes(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
