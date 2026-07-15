/**
 * Pure helpers for the wizard's optional documentation step (adr/0003), mirroring
 * templateResolution.ts. The documentation choice is remembered per user, per operation, and (for
 * operations with a process name) per process name - keyed the same way as the template default.
 */
import type { DocumentationSelection } from "./DocumentationStep";

/** Guard a remembered documentation value loaded from the preference store back into a valid shape. */
export function normalizeDocumentation(stored: unknown): DocumentationSelection {
  const s = stored as { globalId?: unknown; name?: unknown } | null | undefined;
  return s && typeof s.globalId === "string" && typeof s.name === "string"
    ? { globalId: s.globalId, name: s.name }
    : null;
}

/**
 * The documentation defaults after Perform. When "remember" is ticked and a document is chosen, store
 * it under this key; otherwise drop any previously-remembered document for the key, so unticking (or
 * clearing) is itself remembered. Persisted only on Perform, never on cancel.
 */
export function docDefaultsAfterPerform(
  current: Record<string, DocumentationSelection>,
  key: string,
  documentation: DocumentationSelection,
  remember: boolean,
): Record<string, DocumentationSelection> {
  const next = { ...current };
  if (remember && documentation) next[key] = documentation;
  else delete next[key];
  return next;
}
