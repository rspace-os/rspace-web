package com.researchspace.conversion;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dependency-aware probes for container orchestration. */
@RestController
@RequestMapping("/health")
final class ConversionHealthController {

  private final LibreOfficeSandbox sandbox;
  private final GotenbergProxy gotenberg;
  private final OfficeConversionLimiter limiter;

  ConversionHealthController(
      LibreOfficeSandbox sandbox, GotenbergProxy gotenberg, OfficeConversionLimiter limiter) {
    this.sandbox = sandbox;
    this.gotenberg = gotenberg;
    this.limiter = limiter;
  }

  @GetMapping("/live")
  Map<String, String> live() {
    return Map.of("status", "UP");
  }

  @GetMapping("/ready")
  ResponseEntity<Map<String, String>> ready() {
    return response(sandbox.isReady() && gotenberg.isReady());
  }

  @GetMapping("/ready/word")
  ResponseEntity<Map<String, String>> wordReady() {
    return response(sandbox.isReady() && limiter.hasWordCapacity());
  }

  @GetMapping("/ready/pdf")
  ResponseEntity<Map<String, String>> pdfReady() {
    return response(gotenberg.isReady() && limiter.hasPdfCapacity());
  }

  private ResponseEntity<Map<String, String>> response(boolean ready) {
    return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", ready ? "UP" : "DOWN"));
  }
}
