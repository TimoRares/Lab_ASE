package com.instakill.analytics;

import com.instakill.infrastructure.security.JwtUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsRestController {

    private final AnalyticsRepository analytics;

    public AnalyticsRestController(AnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    /** Statisticile utilizatorului autentificat (/api/** cere autentificare, vezi SecurityConfig). */
    @GetMapping("/me")
    public AnalyticsResponse myAnalytics(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return analytics.forUser(principal.id());
    }
}
