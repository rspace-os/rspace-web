/**
 * Shared client-side validation for link targets, used by both the extra-field
 * link editor (UpdateField) and the template-field link editor (LinkFieldValue).
 * Mirrors the backend's InventoryLinkValidator: syntactic Global ID parse, a
 * supported target prefix, and no self-links. Existence checks require a server
 * round-trip and live in checkLinkTargetExists.
 */

// Inventory items (SA/SS/IC/IN, plus IT sample templates) and ELN documents (SD),
// notebooks (NB) and gallery files (GL).
export const ALLOWED_TARGET_PREFIXES: ReadonlySet<string> = new Set([
  "SA",
  "SS",
  "IC",
  "IN",
  "IT",
  "SD",
  "NB",
  "GL",
]);

export const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v(\d+))?$/;

/** True when target points at the same item as source, ignoring any version suffix. */
export function isSelfLink(
  sourceGlobalId: string,
  targetGlobalId: string,
): boolean {
  const source = GLOBAL_ID_PATTERN.exec(sourceGlobalId);
  const target = GLOBAL_ID_PATTERN.exec(targetGlobalId);
  return Boolean(
    source && target && source[1] === target[1] && source[2] === target[2],
  );
}

/** Validates only the target Global ID; relation-type validity is reported on its own field. */
export function validateTarget(
  targetGlobalId: string,
  sourceGlobalId: string,
): { ok: boolean; reason: string } {
  const parsed = GLOBAL_ID_PATTERN.exec(targetGlobalId);
  if (!parsed) return { ok: false, reason: "Target Global ID is required" };
  if (!ALLOWED_TARGET_PREFIXES.has(parsed[1]))
    return {
      ok: false,
      reason:
        "Target must be an Inventory item or an ELN document, notebook or gallery file",
    };
  if (parsed[3] !== undefined)
    // versions are pinned through the version dialog, never typed: the dialog
    // only offers versions the target actually has, and pinning also captures
    // the matching audit revision
    return {
      ok: false,
      reason:
        "Enter the Global ID without a version. To pin the link to a version, use the clock icon.",
    };
  if (isSelfLink(sourceGlobalId, targetGlobalId))
    return { ok: false, reason: "An item cannot link to itself." };
  return { ok: true, reason: "" };
}
