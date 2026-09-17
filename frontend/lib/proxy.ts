import { NextResponse } from "next/server";
import { REQUEST_ID_HEADER } from "@/lib/backend";

// Thin same-origin proxy. Forwards method, body, and correlation ID,
// passes upstream status and body through untouched, and reports an
// unreachable backend as 502 without fabricating domain data.
export async function proxyJson(url: string, init?: RequestInit): Promise<NextResponse> {
  const headers = new Headers(init?.headers);
  if (!headers.has(REQUEST_ID_HEADER)) {
    headers.set(REQUEST_ID_HEADER, crypto.randomUUID());
  }
  let upstream: Response;
  try {
    upstream = await fetch(url, { ...init, headers, cache: "no-store" });
  } catch {
    return NextResponse.json(
      {
        error: "BACKEND_UNAVAILABLE",
        message: `Upstream unreachable: ${url}. Financial data below is unavailable, not zero.`,
      },
      { status: 502 },
    );
  }
  const body = await upstream.text();
  const out = new Headers();
  const contentType = upstream.headers.get("content-type");
  if (contentType) out.set("content-type", contentType);
  const correlation = upstream.headers.get(REQUEST_ID_HEADER) ?? headers.get(REQUEST_ID_HEADER);
  if (correlation) out.set(REQUEST_ID_HEADER, correlation);
  return new NextResponse(body, { status: upstream.status, headers: out });
}
