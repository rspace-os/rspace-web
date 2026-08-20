/**
 * Pure helpers for the derived sample name (adr/0004). The new sample's name is seeded from the
 * origin sample's name and the operation's process name, then de-duplicated with a numeric suffix so
 * repeated runs of the same process do not all land on an identical name. Inventory sample names are
 * not uniqueness-constrained, so the suffix is a usability nicety, not an integrity requirement; the
 * field stays editable.
 */

// A trailing subsample serial (".01", ".12") the backend adds to each subsample of a sample.
const SUBSAMPLE_SERIAL = /\.\d+$/;
// A trailing dedup suffix ("_1", "_2") firstAvailableName appends to disambiguate a name.
const DEDUP_SUFFIX = /_\d+$/;

/**
 * "<origin name> <process name>", trimming each part and dropping an empty one (no dangling space) -
 * except when the process name is already the tail of the origin name, in which case it is NOT
 * appended again. Otherwise repeated runs of the same process would grow the name without bound
 * ("SUB PROC" -> "SUB PROC PROC" -> ...). The check ignores a trailing subsample serial (".01") and
 * dedup suffix ("_1") and is case-insensitive; when the process is already present the stripped base
 * is returned, so the caller's de-duplication turns "SUB PROC" into "SUB PROC_1", "SUB PROC_2", etc.
 */
export function derivedSampleName(originName: string, processName: string): string {
  const origin = originName.trim();
  const process = processName.trim();
  if (process === "") return origin;
  const stripped = origin.replace(SUBSAMPLE_SERIAL, "").replace(DEDUP_SUFFIX, "").trimEnd();
  const tail = stripped.toLowerCase();
  const proc = process.toLowerCase();
  // The process must be the whole tail or follow a space, so "SUBPROC" does not match "PROC".
  if (tail === proc || tail.endsWith(` ${proc}`)) return stripped;
  return origin === "" ? process : `${origin} ${process}`;
}

/**
 * The base name if it is available, otherwise `base_N` for the lowest N >= 1 that is (filling gaps,
 * so a taken base + free `_1` yields `_1`). Availability is probed one candidate at a time via the
 * injected `isAvailable` (the wizard passes an exact name-existence check; see
 * `operationsApi.sampleNameAvailable`), which keeps this logic pure and unit-testable.
 */
export async function firstAvailableName(
  base: string,
  isAvailable: (name: string) => Promise<boolean>,
): Promise<string> {
  if (await isAvailable(base)) return base;
  for (let n = 1; ; n++) {
    const candidate = `${base}_${n}`;
    if (await isAvailable(candidate)) return candidate;
  }
}
