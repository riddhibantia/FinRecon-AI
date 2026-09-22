package com.finrecon.reporting.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

// FR-13: operational KPIs, ageing, and impact summaries. Read-only over the
// shared schema. Every number is a stored fact or an explicit ratio of two
// stored facts. Ageing uses an injected boundary so the SQL stays portable
// and the rule ("older than two calendar days", escalation matrix) is visible.
@Service
public class ReportingService {

    static final int AGEING_DAYS = 2;

    private final JdbcTemplate jdbc;

    public ReportingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CategoryCount(String category, long count) {
    }

    public record SeverityCount(String severity, long count) {
    }

    public record RunKpis(long total, long completed, long failed) {
    }

    public record ResultKpis(long total, long matched, long mismatched, String autoMatchRate) {
    }

    public record CaseKpis(long total, long open, long investigating, long resolved,
                           long escalated, List<CategoryCount> byCategory,
                           List<SeverityCount> bySeverity) {
    }

    public record ImpactKpis(String absoluteUnresolvedDifference, long highSeverityUnresolved) {
    }

    public record FeedbackKpis(long total, long corrections) {
    }

    public record KpiReport(OffsetDateTime generatedAt, RunKpis runs, ResultKpis results,
                            CaseKpis cases, ImpactKpis impact, FeedbackKpis feedback) {
    }

    public record AgeingReport(OffsetDateTime generatedAt, long unresolvedTotal, long olderThanDays,
                               long openOlderThanBoundary, OffsetDateTime oldestUnresolvedCreatedAt) {
    }

    public KpiReport kpis() {
        Map<String, Long> runStatus = counts(
                "SELECT status, COUNT(*) FROM reconciliation_runs GROUP BY status", "status");
        Map<String, Long> matchStatus = counts(
                "SELECT match_status, COUNT(*) FROM reconciliation_results GROUP BY match_status",
                "match_status");
        long resultsTotal = sum(matchStatus);
        long matched = matchStatus.getOrDefault("MATCHED", 0L);
        long mismatched = matchStatus.getOrDefault("MISMATCHED", 0L);
        Map<String, Long> caseStatus = counts(
                "SELECT status, COUNT(*) FROM exceptions GROUP BY status", "status");
        return new KpiReport(
                OffsetDateTime.now(),
                new RunKpis(sum(runStatus), runStatus.getOrDefault("COMPLETED", 0L),
                        runStatus.getOrDefault("FAILED", 0L)),
                new ResultKpis(resultsTotal, matched, mismatched, ratio(matched, resultsTotal)),
                new CaseKpis(sum(caseStatus), caseStatus.getOrDefault("OPEN", 0L),
                        caseStatus.getOrDefault("INVESTIGATING", 0L),
                        caseStatus.getOrDefault("RESOLVED", 0L),
                        caseStatus.getOrDefault("ESCALATED", 0L),
                        rows("SELECT category, COUNT(*) FROM exceptions GROUP BY category"
                                        + " ORDER BY category LIMIT 500",
                                (rs, rowNum) -> new CategoryCount(rs.getString(1), rs.getLong(2))),
                        rows("SELECT severity, COUNT(*) FROM exceptions GROUP BY severity"
                                        + " ORDER BY severity LIMIT 500",
                                (rs, rowNum) -> new SeverityCount(rs.getString(1), rs.getLong(2)))),
                new ImpactKpis(
                        money("SELECT COALESCE(SUM(ABS(r.amount_difference)), 0) FROM exceptions e"
                                + " JOIN reconciliation_results r ON r.result_id = e.result_id"
                                + " WHERE e.status <> 'RESOLVED'"),
                        count("SELECT COUNT(*) FROM exceptions e WHERE e.status <> 'RESOLVED'"
                                + " AND e.severity = 'HIGH'")),
                new FeedbackKpis(count("SELECT COUNT(*) FROM analyst_feedback"),
                        count("SELECT COUNT(*) FROM analyst_feedback WHERE original_value IS NOT NULL"
                                + " AND corrected_value IS NOT NULL"
                                + " AND original_value <> corrected_value")));
    }

    public AgeingReport ageing() {
        OffsetDateTime boundary = OffsetDateTime.now().minusDays(AGEING_DAYS);
        OffsetDateTime oldest = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM exceptions WHERE status <> 'RESOLVED'",
                OffsetDateTime.class);
        return new AgeingReport(OffsetDateTime.now(),
                count("SELECT COUNT(*) FROM exceptions WHERE status <> 'RESOLVED'"),
                AGEING_DAYS,
                count("SELECT COUNT(*) FROM exceptions WHERE status <> 'RESOLVED' AND created_at < ?",
                        boundary),
                oldest);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String money(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private Map<String, Long> counts(String sql, String keyColumn) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getString(keyColumn), rs.getLong(2));
        });
        return out;
    }

    private <T> List<T> rows(String sql, RowMapper<T> mapper) {
        return jdbc.query(sql, mapper);
    }

    private static long sum(Map<String, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }

    // Ratios are explicit strings with four decimals: the dashboard repeats
    // them verbatim and never recomputes financial or rate figures.
    private static String ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .toPlainString();
    }
}