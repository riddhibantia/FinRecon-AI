// P10 contract tests: backend URL builders and the closed proxy surface.
// The dashboard may only call known backend contracts; anything else must
// resolve to null so the proxy route can refuse it.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CASE_ACTIONS,
  REQUEST_ID_HEADER,
  buildAgeingUrl,
  buildCaseActionUrl,
  buildCaseDetailUrl,
  buildCaseQueueUrl,
  buildFeedbackUrl,
  buildInvestigateUrl,
  buildKpisUrl,
  buildRunResultsUrl,
  buildRunStartUrl,
  buildRunUrl,
  isCaseAction,
  reportingApiOrigin,
  serviceHealthTargets,
} from "../test-dist/lib/backend.js";

test("case actions are a closed set of three", () => {
  assert.deepEqual([...CASE_ACTIONS], ["assign", "resolve", "escalate"]);
  assert.equal(isCaseAction("assign"), true);
  assert.equal(isCaseAction("resolve"), true);
  assert.equal(isCaseAction("escalate"), true);
  assert.equal(isCaseAction("delete"), false);
  assert.equal(isCaseAction("CLASSIFY"), false);
  assert.equal(isCaseAction(null), false);
});

test("unknown case actions resolve to null", () => {
  assert.equal(buildCaseActionUrl("http://x", "id-1", "delete"), null);
  assert.equal(buildCaseActionUrl("http://x", "id-1", ""), null);
  assert.match(
    buildCaseActionUrl("http://x", "id-1", "resolve") ?? "",
    /^http:\/\/x\/api\/cases\/id-1\/resolve$/,
  );
});

test("ids are encoded, never interpolated raw", () => {
  const url = buildCaseDetailUrl("http://x", "a/b?c=d");
  assert.equal(url, "http://x/api/cases/a%2Fb%3Fc%3Dd");
});

test("queue builder forwards filters verbatim", () => {
  const params = new URLSearchParams({ status: "OPEN", category: "FEE_VARIANCE" });
  assert.equal(
    buildCaseQueueUrl("http://x", params),
    "http://x/api/cases?status=OPEN&category=FEE_VARIANCE",
  );
  assert.equal(buildCaseQueueUrl("http://x", new URLSearchParams()), "http://x/api/cases");
});

test("reconcile builders hit the P3 contract paths", () => {
  assert.equal(buildRunStartUrl("http://r", "NIGHTLY"), "http://r/api/reconcile?sourceSet=NIGHTLY");
  assert.match(buildRunUrl("http://r", "run-1"), /\/api\/reconcile\/runs\/run-1$/);
  assert.match(buildRunResultsUrl("http://r", "run-1"), /\/results$/);
});

test("investigate targets only the read-only AI route", () => {
  assert.equal(buildInvestigateUrl("http://ai"), "http://ai/investigate");
});

test("correlation header name is fixed", () => {
  assert.equal(REQUEST_ID_HEADER, "X-Request-Id");
});

test("health targets cover the monolith and AI service", () => {
  const names = serviceHealthTargets().map((t) => t.name);
  for (const expected of [
    "finrecon-app",
    "ai-service",
  ]) {
    assert.ok(names.includes(expected), `missing ${expected}`);
  }
});

test("feedback builder hits the FR-11 contract path and encodes the id", () => {
  assert.equal(buildFeedbackUrl("http://x", "case/1"), "http://x/api/cases/case%2F1/feedback");
});

test("reporting builders hit the FR-13 contract paths", () => {
  assert.equal(buildKpisUrl("http://r"), "http://r/api/reports/kpis");
  assert.equal(buildAgeingUrl("http://r"), "http://r/api/reports/ageing");
  assert.equal(reportingApiOrigin(), "http://localhost:8080");
});
