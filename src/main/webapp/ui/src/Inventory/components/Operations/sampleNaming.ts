const SUBSAMPLE_SERIAL = /\.\d+$/;
const DEDUP_SUFFIX = /_\d+$/;

export function derivedSampleName(originName: string, processName: string): string {
  const origin = originName.trim();
  const process = processName.trim();
  if (process === "") return origin;
  const stripped = origin.replace(SUBSAMPLE_SERIAL, "").replace(DEDUP_SUFFIX, "").trimEnd();
  const tail = stripped.toLowerCase();
  const proc = process.toLowerCase();
  if (tail === proc || tail.endsWith(` ${proc}`)) return stripped;
  return origin === "" ? process : `${origin} ${process}`;
}

/**
 * The probe is BOUNDED: the availability endpoint also answers "not available" for an invalid name
 * (e.g. one over the length limit), in which case every suffixed candidate fails identically and an
 * unbounded loop would fire HTTP requests forever.
 */
const MAX_DEDUP_ATTEMPTS = 25;

export async function firstAvailableName(
  base: string,
  isAvailable: (name: string) => Promise<boolean>,
): Promise<string> {
  if (await isAvailable(base)) return base;
  for (let n = 1; n <= MAX_DEDUP_ATTEMPTS; n++) {
    const candidate = `${base}_${n}`;
    if (await isAvailable(candidate)) return candidate;
  }
  return base;
}
