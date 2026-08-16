package com.jobfit.resumeats;

/**
 * PASS = parses reliably. WARN = a real risk some ATS platforms will
 * mis-parse or penalize this, worth fixing before applying broadly.
 * FAIL = a strong likelihood the resume is unreadable or auto-rejected by
 * keyword/field-matching ATS software regardless of how good the content is.
 */
public enum AtsCheckStatus {
    PASS, WARN, FAIL
}
