package com.jobfit.resume;

import com.jobfit.common.util.SecurityUtils;
import com.jobfit.resume.dto.ResumeDetailResponse;
import com.jobfit.resume.dto.ResumeSummaryResponse;
import com.jobfit.resume.dto.ResumeUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@Tag(name = "Resumes", description = "Upload, review, and manage resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResumeSummaryResponse upload(@RequestParam("file") MultipartFile file) {
        return resumeService.upload(SecurityUtils.currentUserId(), file);
    }

    @GetMapping
    public List<ResumeSummaryResponse> list() {
        return resumeService.list(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    public ResumeDetailResponse get(@PathVariable Long id) {
        return resumeService.getDetail(SecurityUtils.currentUserId(), id);
    }

    @PatchMapping("/{id}")
    public ResumeDetailResponse update(@PathVariable Long id, @Valid @RequestBody ResumeUpdateRequest request) {
        return resumeService.update(SecurityUtils.currentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resumeService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
