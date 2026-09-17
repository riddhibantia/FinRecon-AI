import { buildRunResultsUrl, reconApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { runId: string } }) {
  return proxyJson(buildRunResultsUrl(reconApiOrigin(), params.runId));
}
