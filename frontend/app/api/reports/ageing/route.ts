import { buildAgeingUrl, reportingApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

// FR-13 read-only ageing projection from the reporting service.
export async function GET() {
  return proxyJson(buildAgeingUrl(reportingApiOrigin()));
}
