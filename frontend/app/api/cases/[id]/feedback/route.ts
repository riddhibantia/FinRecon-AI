import { NextRequest } from "next/server";
import { buildFeedbackUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

// FR-11: append-only analyst feedback. The proxy forwards the body verbatim;
// validation and audit live in the exception service.
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  const body = await request.text();
  const { id } = await params;
  return proxyJson(buildFeedbackUrl(caseApiOrigin(), id), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  });
}
