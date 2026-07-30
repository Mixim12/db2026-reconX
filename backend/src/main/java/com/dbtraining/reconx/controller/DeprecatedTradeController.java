package com.dbtraining.reconx.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ============================================================================
 * TICKET-ADV080 — Deprecated v0 trade endpoint
 *
 * WHAT:    Example of a cleanly deprecated endpoint surface area. The old
 *          /v0/trades path returns 410 Gone with standard deprecation headers
 *          so that callers can discover the successor URL.
 * WHY:     A 410 Gone with Deprecation/Sunset/Link headers is the correct
 *          HTTP signal for a retired endpoint. A 404 would falsely suggest
 *          the surface area never existed, which is worse for API consumers.
 * OBSERVE: curl -i /api/v0/trades returns 410 with deprecation headers;
 *          curl -i /api/v1/trades returns the normal 200 response.
 * ============================================================================
 */
@RestController
@RequestMapping("/v0/trades")
@Tag(name = "trades (deprecated)", description = "Deprecated v0 trade endpoints — use /v1/trades instead")
public class DeprecatedTradeController {

    @Deprecated(since = "v1.4.0", forRemoval = true)
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "DEPRECATED — list trades (v0)",
               description = "This endpoint is deprecated. Use GET /api/v1/trades instead.")
    public ResponseEntity<Void> listTradesDeprecated(HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", "Sat, 01 Jan 2028 00:00:00 GMT");
        response.setHeader("Link", "</api/v1/trades>; rel=\"successor-version\"");
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
