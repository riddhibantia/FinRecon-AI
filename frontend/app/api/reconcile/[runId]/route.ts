import { buildRunUrl, reconApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: Promise<{ runId: string }> }) {
  const { runId } = await params;
  return proxyJson(buildRunUrl(reconApiOrigin(), runId));
}
