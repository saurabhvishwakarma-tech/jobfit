package com.jobfit.common.util;

import com.jobfit.user.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Central helper for reading the current authenticated user id. Every
 * service method that loads a user-owned resource (Resume, Job,
 * Application, ...) should scope its query by this id - never trust an id
 * from the request path/body alone (IDOR prevention).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return principal.getId();
    }
}
