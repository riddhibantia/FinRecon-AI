package com.finrecon.reconciliation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.finrecon.shared.domain.ReconciliationRun;
import com.finrecon.shared.domain.ReconciliationRunRepository;
import com.finrecon.reconciliation.reconcile.ReconciliationService;

// P3 API: start a deterministic run, read a run, read its results.
// No exception or case logic here (P4).
@RestController
@RequestMapping("/api/reconcile")
public class ReconciliationController {

    private final ReconciliationService service;
    private final ReconciliationRunRepository runs;

    public ReconciliationController(ReconciliationService service,
                                    ReconciliationRunRepository runs) {
        this.service = service;
        this.runs = runs;
    }

    private static final java.util.regex.Pattern SOURCE_SET =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @PostMapping
    public ReconciliationService.RunSummary reconcile(
            @RequestParam(defaultValue = "ALL") String sourceSet) {
        return service.run(normalizeSourceSet(sourceSet));
    }

    @GetMapping("/runs/{runId}")
    public ReconciliationRun run(@PathVariable UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Unknown run: " + runId));
    }

    @GetMapping("/runs/{runId}/results")
    public List<ReconciliationService.ResultView> results(@PathVariable UUID runId) {
        if (!runs.existsById(runId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown run: " + runId);
        }
        return service.resultsFor(runId);
    }

    private static String normalizeSourceSet(String sourceSet) {
        String value = sourceSet == null ? "ALL" : sourceSet.trim();
        if (value.isEmpty()) {
            value = "ALL";
        }
        if (!SOURCE_SET.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "sourceSet must match [A-Za-z0-9_-]{1,64}");
        }
        return value;
    }
}
