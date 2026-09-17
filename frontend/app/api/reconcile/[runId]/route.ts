import { buildRunUrl, reconApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { runId: string } }) {
  return proxyJson(buildRunUrl(reconApiOrigin(), params.runId));
}
