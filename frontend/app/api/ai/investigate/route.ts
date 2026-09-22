import { NextResponse } from "next/server";
import { aiApiOrigin, buildInvestigateUrl } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

// Read-only AI access: the dashboard may request investigations, never
// training, persistence, or case mutation through the AI service.
export async function POST(request: Request) {
  const raw = await request.text();
  if (raw.length > 16384) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Investigation payload too large." },
      { status: 400 },
    );
  }
  let body: unknown;
  try {
    body = raw ? JSON.parse(raw) : {};
  } catch {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Invalid JSON payload." },
      { status: 400 },
    );
  }
  const payload = body as { exceptionId?: unknown; as_of?: unknown; tolerance?: unknown };
  if (typeof payload.exceptionId !== "string" || payload.exceptionId.length === 0) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "exceptionId is required." },
      { status: 400 },
    );
  }
  if (payload.as_of !== undefined && typeof payload.as_of !== "string") {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Invalid as_of date." },
      { status: 400 },
    );
  }
  if (
    payload.tolerance !== undefined &&
    typeof payload.tolerance !== "string" &&
    typeof payload.tolerance !== "number"
  ) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Invalid tolerance." },
      { status: 400 },
    );
  }
  return proxyJson(buildInvestigateUrl(aiApiOrigin()), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      exceptionId: payload.exceptionId,
      ...(payload.as_of ? { as_of: payload.as_of } : {}),
      ...(payload.tolerance !== undefined ? { tolerance: payload.tolerance } : {}),
    }),
  });
}
