import type { Buffer } from "node:buffer";
import { readFile } from "node:fs/promises";
import AdmZip from "adm-zip";

/** Reads matching export entries without extracting an untrusted archive onto disk. */
export async function readArchiveDocuments(path: string, format: "html" | "xml"): Promise<string[]> {
  const archive = new AdmZip(await readFile(path));
  const entries = archive.getEntries().filter((entry) => !entry.isDirectory && entry.entryName.endsWith(`.${format}`));
  if (!entries.length) throw new Error(`The export archive contains no .${format} documents.`);
  return entries.map((entry) => entry.getData().toString("utf8"));
}

// Reads a zip's central directory to list entry names, without decompressing any entry.
export function listZipEntries(buffer: Buffer): string[] {
  return new AdmZip(buffer).getEntries().map((entry) => entry.entryName);
}
