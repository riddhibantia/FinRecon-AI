// P10 contract tests: presentational helpers never invent values.
// Compiled from lib/ by `tsc -p tsconfig.test.json` before node --test.
import { test } from "node:test";
import assert from "node:assert/strict";
import { display, formatMoney, shortId, statusTone } from "../test-dist/lib/format.js";

test("display passes values through and blanks unknowns", () => {
  assert.equal(display("TXN-1"), "TXN-1");
  assert.equal(display(0), "0");
  assert.equal(display(null), "—");
  assert.equal(display(undefined), "—");
  assert.equal(display(""), "—");
});

test("formatMoney appends currency or blanks", () => {
  assert.equal(formatMoney("95.00", "INR"), "95.00 INR");
  assert.equal(formatMoney("95.00", null), "95.00");
  assert.equal(formatMoney(null, "INR"), "—");
});

test("shortId truncates long ids only", () => {
  assert.equal(shortId("12345678-abcd"), "12345678");
  assert.equal(shortId("abc"), "abc");
  assert.equal(shortId(null), "—");
});

test("statusTone maps workflow states", () => {
  assert.equal(statusTone("OPEN"), "open");
  assert.equal(statusTone("INVESTIGATING"), "working");
  assert.equal(statusTone("HUMAN_REVIEW"), "working");
  assert.equal(statusTone("MATCHED"), "done");
  assert.equal(statusTone("RESOLVED"), "done");
  assert.equal(statusTone("UP"), "done");
  assert.equal(statusTone("MANUAL_REVIEW"), "bad");
  assert.equal(statusTone("ESCALATED"), "bad");
  assert.equal(statusTone("DOWN"), "bad");
  assert.equal(statusTone("SOMETHING_NEW"), "neutral");
});
