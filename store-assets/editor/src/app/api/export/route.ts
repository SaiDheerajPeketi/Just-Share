import { promises as fs } from "node:fs";
import path from "node:path";
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const EXPORT_ROOT = path.resolve(process.cwd(), "..", "exports");
const MAX_BASE64_LENGTH = 50 * 1024 * 1024;

export async function POST(req: Request) {
  let body: { path?: unknown; base64?: unknown };
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ ok: false, error: "Invalid JSON" }, { status: 400 });
  }

  if (typeof body.path !== "string" || typeof body.base64 !== "string") {
    return NextResponse.json({ ok: false, error: "Path and image data are required" }, { status: 400 });
  }
  if (!body.path || body.path.includes("..") || path.isAbsolute(body.path)) {
    return NextResponse.json({ ok: false, error: "Invalid export path" }, { status: 400 });
  }
  if (body.base64.length > MAX_BASE64_LENGTH) {
    return NextResponse.json({ ok: false, error: "Export is too large" }, { status: 413 });
  }

  const outputPath = path.resolve(EXPORT_ROOT, body.path);
  if (!outputPath.startsWith(`${EXPORT_ROOT}${path.sep}`)) {
    return NextResponse.json({ ok: false, error: "Invalid export path" }, { status: 400 });
  }

  try {
    await fs.mkdir(path.dirname(outputPath), { recursive: true });
    await fs.writeFile(outputPath, Buffer.from(body.base64, "base64"));
    return NextResponse.json({ ok: true });
  } catch (error) {
    return NextResponse.json(
      { ok: false, error: error instanceof Error ? error.message : String(error) },
      { status: 500 },
    );
  }
}
