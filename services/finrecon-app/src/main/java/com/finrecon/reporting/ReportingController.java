package com.finrecon.reporting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finrecon.reporting.reports.ReportingService;

// FR-13 read-only reporting API. No write route exists on this service.
@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportingService reports;

    public ReportingController(ReportingService reports) {
        this.reports = reports;
    }

    @GetMapping("/kpis")
    public ReportingService.KpiReport kpis() {
        return reports.kpis();
    }

    @GetMapping("/ageing")
    public ReportingService.AgeingReport ageing() {
        return reports.ageing();
    }
}