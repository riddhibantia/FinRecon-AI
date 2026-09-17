import { NextRequest } from "next/server";
import { buildCaseQueueUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  return proxyJson(buildCaseQueueUrl(caseApiOrigin(), url.searchParams));
}
