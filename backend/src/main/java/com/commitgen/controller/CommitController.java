package com.commitgen.controller;

import com.commitgen.dto.CommitRequest;
import com.commitgen.dto.CommitResponse;
import com.commitgen.exception.ErrorResponse;
import com.commitgen.service.CommitService;
import com.commitgen.service.RateLimitService;
import com.commitgen.service.RateLimitService.RateLimitInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommitController {

    private final CommitService commitService;
    private final RateLimitService rateLimitService;

    @PostMapping("/generate")
    public ResponseEntity<Object> generate(
            @Valid @RequestBody CommitRequest request,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        RateLimitInfo info = rateLimitService.consume(ip);

        if (!info.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(h -> addRateLimitHeaders(h, info))
                    .header("Retry-After", String.valueOf(info.getRetryAfterSeconds()))
                    .body(ErrorResponse.of(
                            429,
                            "Rate limit excedido. Você pode fazer "
                                    + info.getLimit()
                                    + " requisições por hora. Tente novamente mais tarde."
                    ));
        }

        CommitResponse response = commitService.generateCommitMessages(request);

        return ResponseEntity.ok()
                .headers(h -> addRateLimitHeaders(h, info))
                .body(response);
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> getRateLimit(HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        RateLimitInfo info = rateLimitService.getInfo(ip);

        return ResponseEntity.ok()
                .headers(h -> addRateLimitHeaders(h, info))
                .body(Map.of(
                        "remaining", info.getRemaining(),
                        "limit", info.getLimit(),
                        "resetAt", info.getResetAtEpochSeconds()
                ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "commit-message-generator"
        ));
    }

    private void addRateLimitHeaders(org.springframework.http.HttpHeaders headers,
                                     RateLimitInfo info) {
        headers.set("X-RateLimit-Limit", String.valueOf(info.getLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(info.getRemaining()));
        headers.set("X-RateLimit-Reset", String.valueOf(info.getResetAtEpochSeconds()));
    }

    private String getClientIp(HttpServletRequest request) {
        // CF-Connecting-IP → header real do Cloudflare
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim(); // pega o IP original, não o proxy
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
