import { NextRequest, NextResponse } from "next/server";
import { buildCaseQueueUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

const STATUSES = new Set(["OPEN", "INVESTIGATING", "RESOLVED", "ESCALATED"]);
const CATEGORIES = new Set([
  "MISSING_SETTLEMENT",
  "AMOUNT_MISMATCH",
  "FEE_VARIANCE",
  "FX_VARIANCE",
  "DUPLICATE_SETTLEMENT",
  "PARTIAL_SETTLEMENT",
  "STATUS_MISMATCH",
  "LATE_SETTLEMENT",
  "UNKNOWN_EXCEPTION",
]);

export async function GET(request: NextRequest) {
  const incoming = new URL(request.url).searchParams;
  const out = new URLSearchParams();
  const status = incoming.get("status");
  const category = incoming.get("category");
  const assignedTo = incoming.get("assignedTo");
  if (status && STATUSES.has(status)) out.set("status", status);
  if (category && CATEGORIES.has(category)) out.set("category", category);
  if (assignedTo && assignedTo.length <= 128) out.set("assignedTo", assignedTo.trim());
  if ((status && !STATUSES.has(status)) || (category && !CATEGORIES.has(category))) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Invalid status or category filter." },
      { status: 400 },
    );
  }
  return proxyJson(buildCaseQueueUrl(caseApiOrigin(), out));
}
