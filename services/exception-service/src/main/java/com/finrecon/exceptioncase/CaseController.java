package com.finrecon.exceptioncase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finrecon.exceptioncase.cases.CaseService;

// P4 case API: sync cases from a run, analyst queue with filters, case
// detail (exception -> evidence -> source records -> resolution), and the
// OPEN -> INVESTIGATING -> RESOLVED / ESCALATED transitions. No ML (P6+).
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseService cases;

    public CaseController(CaseService cases) {
        this.cases = cases;
    }

    @PostMapping("/sync")
    public CaseService.SyncResult sync(@RequestBody Map<String, UUID> body) {
        UUID runId = body == null ? null : body.get("runId");
        if (runId == null) {
            throw new CaseService.BadCaseRequestException("runId is required");
        }
        return cases.syncRun(runId);
    }

    @GetMapping
    public List<CaseService.CaseSummary> queue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignedTo) {
        return cases.queue(status, category, assignedTo);
    }

    @GetMapping("/{exceptionId}")
    public CaseService.CaseDetail detail(@PathVariable UUID exceptionId) {
        return cases.detail(exceptionId);
    }

    @PostMapping("/{exceptionId}/assign")
    public CaseService.CaseSummary assign(@PathVariable UUID exceptionId,
                                          @RequestBody Map<String, String> body) {
        String assignedTo = body == null ? null : body.get("assignedTo");
        String actorId = body == null ? null : body.get("actorId");
        return cases.assign(exceptionId, assignedTo, actorId);
    }

    @PostMapping("/{exceptionId}/resolve")
    public CaseService.CaseSummary resolve(@PathVariable UUID exceptionId,
                                           @RequestBody Map<String, String> body) {
        Map<String, String> safe = body == null ? Map.of() : body;
        return cases.resolve(exceptionId, safe.get("actionType"),
                safe.get("actorType"), safe.get("actorId"), safe.get("notes"));
    }

    @PostMapping("/{exceptionId}/escalate")
    public CaseService.CaseSummary escalate(@PathVariable UUID exceptionId,
                                            @RequestBody Map<String, String> body) {
        Map<String, String> safe = body == null ? Map.of() : body;
        return cases.escalate(exceptionId, safe.get("actorType"),
                safe.get("actorId"), safe.get("notes"));
    }

    @ExceptionHandler(CaseService.CaseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(CaseService.CaseNotFoundException e) {
        return Map.of("error", "NOT_FOUND", "message", e.getMessage());
    }

    @ExceptionHandler(CaseService.BadCaseRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(CaseService.BadCaseRequestException e) {
        return Map.of("error", "BAD_REQUEST", "message", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleIllegalTransition(IllegalStateException e) {
        return Map.of("error", "ILLEGAL_TRANSITION", "message", e.getMessage());
    }
}
