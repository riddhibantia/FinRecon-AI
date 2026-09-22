import { NextRequest, NextResponse } from "next/server";
import { buildRunStartUrl, reconApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function POST(request: NextRequest) {
  const url = new URL(request.url);
  const raw = (url.searchParams.get("sourceSet") ?? "DASHBOARD").trim() || "DASHBOARD";
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(raw)) {
    return NextResponse.json(
      { error: "BAD_REQUEST", message: "Invalid sourceSet." },
      { status: 400 },
    );
  }
  return proxyJson(buildRunStartUrl(reconApiOrigin(), raw), { method: "POST" });
}
