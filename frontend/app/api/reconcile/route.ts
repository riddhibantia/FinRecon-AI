import { NextRequest } from "next/server";
import { buildRunStartUrl, reconApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function POST(request: NextRequest) {
  const url = new URL(request.url);
  const sourceSet = url.searchParams.get("sourceSet") ?? "DASHBOARD";
  return proxyJson(buildRunStartUrl(reconApiOrigin(), sourceSet), { method: "POST" });
}
