import { NextResponse } from "next/server";
import { REQUEST_ID_HEADER } from "@/lib/backend";

// Thin same-origin proxy. Forwards method, body, and correlation ID,
// passes upstream JSON status and body through, and reports an
// unreachable backend as 502 without fabricating domain data or
// leaking internal origins.
const MAX_BODY_BYTES = 1_000_000;

export async function proxyJson(url: string, init?: RequestInit): Promise<NextResponse> {
  const headers = new Headers(init?.headers);
  if (!headers.has(REQUEST_ID_HEADER)) {
    headers.set(REQUEST_ID_HEADER, crypto.randomUUID());
  }
  headers.delete("host");
  headers.delete("connection");
  let upstream: Response;
  try {
    upstream = await fetch(url, {
      ...init,
      headers,
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });
  } catch {
    return NextResponse.json(
      {
        error: "BACKEND_UNAVAILABLE",
        message: "Backend is unreachable. Financial data below is unavailable, not zero.",
      },
      { status: 502 },
    );
  }
  const contentType = upstream.headers.get("content-type") ?? "";
  // Only JSON passes through; HTML/stack traces never reach the browser.
  if (contentType && !contentType.includes("application/json")) {
    return NextResponse.json(
      { error: "BAD_GATEWAY", message: "Upstream returned a non-JSON response." },
      { status: 502 },
    );
  }
  const body = await upstream.text();
  if (body.length > MAX_BODY_BYTES) {
    return NextResponse.json(
      { error: "BAD_GATEWAY", message: "Upstream response too large." },
      { status: 502 },
    );
  }
  const out = new Headers();
  out.set("content-type", "application/json");
  const correlation = upstream.headers.get(REQUEST_ID_HEADER) ?? headers.get(REQUEST_ID_HEADER);
  if (correlation) out.set(REQUEST_ID_HEADER, correlation);
  return new NextResponse(body, { status: upstream.status, headers: out });
}
