/**
 * Pure helper for the wizard's optional documentation step (adr/0003). The chosen document is
 * remembered as part of the single per-process "remember" bundle (see processValues.ts); this only
 * guards a stored value back into shape.
 */
import type { DocumentationSelection } from "./DocumentationStep";

/** Guard a remembered documentation value loaded from the preference store back into a valid shape. */
export function normalizeDocumentation(stored: unknown): DocumentationSelection {
  const s = stored as { globalId?: unknown; name?: unknown } | null | undefined;
  return s && typeof s.globalId === "string" && typeof s.name === "string"
    ? { globalId: s.globalId, name: s.name }
    : null;
}
