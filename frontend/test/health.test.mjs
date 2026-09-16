// P0 contract test: frontend skeleton exposes the health contract.
// Runs with `node --test` and no dependencies so `npm test` stays green
// before and after `npm install`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

test("health route returns UP contract", () => {
  const path = join(root, "app", "api", "health", "route.ts");
  assert.equal(existsSync(path), true);
  const source = readFileSync(path, "utf8");
  assert.match(source, /status/);
  assert.match(source, /"UP"/);
  assert.match(source, /service/);
  assert.match(source, /frontend/);
});

test("home page is a P0 placeholder", () => {
  const path = join(root, "app", "page.tsx");
  assert.equal(existsSync(path), true);
  const source = readFileSync(path, "utf8");
  assert.match(source, /FinRecon AI/);
});
