import { buildKpisUrl, reportingApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

// FR-13 read-only KPI projection from the reporting service.
export async function GET() {
  return proxyJson(buildKpisUrl(reportingApiOrigin()));
}
