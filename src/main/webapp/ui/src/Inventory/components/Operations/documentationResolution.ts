import type { DocumentationSelection } from "./DocumentationStep";

export function normalizeDocumentation(stored: unknown): DocumentationSelection {
  const s = stored as { globalId?: unknown; name?: unknown } | null | undefined;
  return s && typeof s.globalId === "string" && typeof s.name === "string"
    ? { globalId: s.globalId, name: s.name }
    : null;
}
