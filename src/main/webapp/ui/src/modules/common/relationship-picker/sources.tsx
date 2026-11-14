import { escapeRegExp } from "es-toolkit";
import type { RelationshipFilterPicker } from "@/modules/common/collection/schema";
import type { RelationshipOption } from "@/modules/common/relationship-picker/types";

/**
 * What the picker owns and this layer cannot supply: the wording, because this layer has no i18n of
 * its own, and the density, because only the picker knows how much width it has.
 */
export type RelationshipOptionContext = {
  /** Renders each option on one line, for a picker in a narrow control such as a filter row. */
  compact?: boolean;
  unavailableLabel?: (value: string) => string;
  /** Generic, non-sensitive restore failure message for saved values. */
  failedLabel?: (value: string) => string;
};

export type RelationshipSource = {
  /** Stable source identity, also used to partition query caches. */
  id: string;
  /** Optional REST API v2 resource name for collection-backed sources. */
  resourceName?: string;
  /** Global-ID prefix owned by this resource, used to recognize a pasted global ID. */
  globalIdPrefix?: string;
  /** Converts a source-owned value to its canonical form, or rejects it when it is malformed. */
  normalizeValue?: (value: string) => string | null;
  /** Searches this source. The source owns its URL, scope, and query encoding. */
  search: (term: string, signal: AbortSignal) => Promise<readonly unknown[]>;
  /**
   * Resolves canonical source values in bounded batches. Return documents keyed by canonical value;
   * omitted keys and null values both mean the value is unavailable. Keep batchSize at or below 100.
   */
  resolveMany?: (values: readonly string[], signal: AbortSignal) => Promise<Readonly<Record<string, unknown | null>>>;
  browsable?: boolean;
  /** Maximum values passed to resolveMany, capped at 100 by the picker. */
  batchSize?: number;
  /** Returns whether a stored value belongs to this source. */
  ownsValue: (value: string) => boolean;
  /** Validates one source document and renders it as a selectable option. */
  toOption: (document: unknown, context: RelationshipOptionContext) => RelationshipOption;
};

export type RelationshipOptionWithSource = RelationshipOption & {
  sourceId: string;
  /** Safe state when a saved value could not be restored; the original value remains in `value`. */
  restoreStatus?: "loading" | "missing" | "failed" | "invalid" | "unknown" | "unresolved";
};

/** Returns a non-zero JavaScript-safe database ID from a resource global ID. */
export function databaseIdFromGlobalId(value: string, prefix: string): number | null {
  const escapedPrefix = escapeRegExp(prefix);
  const match = new RegExp(`^${escapedPrefix}(-?\\d+)$`, "i").exec(value.trim());
  if (!match) return null;
  const id = Number(match[1]);
  return Number.isSafeInteger(id) && id !== 0 ? id : null;
}

export function relationshipSourceForPicker(
  picker: RelationshipFilterPicker,
  sources: Readonly<Record<string, RelationshipSource>>,
): RelationshipSource | undefined {
  const source = sources[picker.resource];
  return source?.globalIdPrefix === picker.globalIdPrefix ? source : undefined;
}
