/**
 * Pure helpers for the derived sample name (adr/0004). The new sample's name is seeded from the
 * origin sample's name and the operation's process name, then de-duplicated with a numeric suffix so
 * repeated runs of the same process do not all land on an identical name. Inventory sample names are
 * not uniqueness-constrained, so the suffix is a usability nicety, not an integrity requirement; the
 * field stays editable.
 */

/** "<origin name> <process name>", trimming each part and dropping an empty one (no dangling space). */
export function derivedSampleName(originName: string, processName: string): string {
  return [originName, processName]
    .map((part) => part.trim())
    .filter((part) => part !== "")
    .join(" ");
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
