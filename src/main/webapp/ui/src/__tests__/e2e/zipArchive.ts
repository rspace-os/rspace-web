import type { Buffer } from "node:buffer";
import AdmZip from "adm-zip";

// Reads a zip's central directory to list entry names, without decompressing any entry.
export function listZipEntries(buffer: Buffer): string[] {
  return new AdmZip(buffer).getEntries().map((entry) => entry.entryName);
}
