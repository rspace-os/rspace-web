import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// Batch CSV content needs unique usernames every run.
export function writeBatchCsvFixture(csvContent: string): string {
  const dir = mkdtempSync(join(tmpdir(), "rspace-e2e-batch-csv-"));
  const filePath = join(dir, "batch.csv");
  writeFileSync(filePath, csvContent);
  return filePath;
}
