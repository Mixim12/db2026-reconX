package com.dbtraining.reconx.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV080 — API versioning audit
 *
 * Verifies that every data-contract @RestController carries a versioned
 * path prefix (/v1/, /v0/ etc). AuthController is intentionally excluded
 * because login is an infrastructure endpoint, not a data-contract surface.
 * ============================================================================
 */
class ApiVersioningTest {

    /**
     * All known @RestController classes in the controller package.
     * Update this list when a new controller is added.
     */
    private static final List<Class<?>> CONTROLLER_CLASSES = List.of(
            TradeController.class,
            ReconController.class,
            AuditController.class,
            AuthController.class,
            DeprecatedTradeController.class
    );

    /**
     * Controllers that are intentionally NOT versioned (infrastructure endpoints).
     */
    private static final Set<String> UNVERSIONED_ALLOWED = Set.of(
            "AuthController"
    );

    @Test
    void all_data_controllers_carry_versioned_prefix() {
        List<String> violations = new ArrayList<>();

        for (Class<?> ctrl : CONTROLLER_CLASSES) {
            // Skip non-RestController classes (shouldn't happen but defensive)
            if (!ctrl.isAnnotationPresent(RestController.class)) {
                continue;
            }

            if (UNVERSIONED_ALLOWED.contains(ctrl.getSimpleName())) {
                continue;
            }

            RequestMapping mapping = ctrl.getAnnotation(RequestMapping.class);
            if (mapping == null) {
                violations.add(ctrl.getSimpleName() + ": missing @RequestMapping");
                continue;
            }

            String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
            if (paths.length == 0) {
                violations.add(ctrl.getSimpleName() + ": @RequestMapping has no path");
                continue;
            }

            boolean hasVersion = false;
            for (String path : paths) {
                if (path.matches("/v\\d+/.*")) {
                    hasVersion = true;
                    break;
                }
            }
            if (!hasVersion) {
                violations.add(ctrl.getSimpleName()
                        + ": path(s) " + List.of(paths)
                        + " do not start with a version prefix like /v1/");
            }
        }

        assertThat(violations)
                .as("All data-contract controllers must carry a versioned prefix")
                .isEmpty();
    }
}
