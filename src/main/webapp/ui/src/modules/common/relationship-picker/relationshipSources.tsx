import { cmp, eq, escapeValue, Operation } from "rsql-builder";
import * as v from "valibot";
import type { RelationshipOption } from "@/modules/common/collection-form/RenderFields.types";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { InventoryItem } from "@/modules/common/ui/inventory-item";

/**
 * What the picker owns and this layer cannot supply: the wording, because this layer has no i18n of
 * its own, and the density, because only the picker knows how much width it has.
 */
export type RelationshipOptionContext = {
  idLinkLabel: (globalId: string) => string;
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
  search: (term: string, token: string | undefined, signal: AbortSignal) => Promise<readonly unknown[]>;
  /** Resolves a stored value when a picker is restored without a prior search. */
  resolve?: (value: string, token: string | undefined, signal: AbortSignal) => Promise<unknown | null>;
  /**
   * Resolves canonical source values in bounded batches. Return documents keyed by canonical value;
   * omitted keys and null values both mean the value is unavailable. Keep batchSize at or below 100.
   */
  resolveMany?: (
    values: readonly string[],
    token: string | undefined,
    signal: AbortSignal,
  ) => Promise<Readonly<Record<string, unknown | null>>>;
  /** Maximum values passed to resolveMany, capped at 100 by the picker. */
  batchSize?: number;
  /** Returns whether a stored value belongs to this source. */
  ownsValue: (value: string) => boolean;
  /** Validates one source document and renders it as a selectable option. */
  toOption: (document: unknown, context: RelationshipOptionContext) => RelationshipOption;
};

export type RelationshipOptionWithSource = RelationshipOption & {
  sourceId: string;
  /** Original validated source document, available to consumers needing domain metadata. */
  sourceDocument?: unknown;
  /** Safe state when a saved value could not be restored; the original value remains in `value`. */
  restoreStatus?: "loading" | "missing" | "failed" | "invalid" | "unknown" | "unresolved";
};

const InstrumentSchema = v.object({
  id: v.number(),
  name: v.string(),
  globalId: v.string(),
});

/** Returns a positive JavaScript-safe database ID from a resource global ID. */
export function databaseIdFromGlobalId(value: string, prefix: string): number | null {
  const escapedPrefix = prefix.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = new RegExp(`^${escapedPrefix}(\\d+)$`, "i").exec(value.trim());
  if (!match) return null;
  const id = Number(match[1]);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

const instruments: RelationshipSource = {
  id: "instruments",
  resourceName: "instruments",
  globalIdPrefix: "IN",
  normalizeValue: (value) => {
    const id = databaseIdFromGlobalId(value, "IN");
    return id === null ? null : `IN${id}`;
  },
  search: async (term, token, signal) => {
    const value = term.trim();
    const id = databaseIdFromGlobalId(value, "IN");
    const params = new URLSearchParams({ page: "1", limit: "20" });
    params.set("fields[instruments]", "id,name,globalId");
    params.set(
      "where",
      id === null
        ? cmp("name", new Operation(escapeValue(value), "=contains=")).toString()
        : cmp("id", eq(id)).toString(),
    );
    const response = await fetch(`/api/v2/instruments?${params}`, {
      headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      signal,
    });
    if (!response.ok) throw new Error(`Relationship option request failed with status ${response.status}`);
    const body = (await response.json()) as { docs?: unknown };
    return Array.isArray(body.docs) ? body.docs : [];
  },
  resolve: async (value, token, signal) => {
    const id = databaseIdFromGlobalId(value, "IN");
    if (id === null) return null;
    const params = new URLSearchParams({ "fields[instruments]": "id,name,globalId" });
    const response = await fetch(`/api/v2/instruments/${id}?${params}`, {
      headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      signal,
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`Relationship option request failed with status ${response.status}`);
    return response.json();
  },
  batchSize: 100,
  resolveMany: async (values, token, signal) => {
    const ids = [...new Set(values.map((value) => databaseIdFromGlobalId(value, "IN")))].filter(
      (id): id is number => id !== null,
    );
    if (ids.length === 0) return {};
    const params = new URLSearchParams({
      page: "1",
      limit: String(Math.min(ids.length, 100)),
      where: `id=in=(${ids.join(",")})`,
      "fields[instruments]": "id,name,globalId",
    });
    const response = await fetch(`/api/v2/instruments?${params}`, {
      headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      signal,
    });
    if (!response.ok) throw new Error(`Relationship option request failed with status ${response.status}`);
    const body: unknown = await response.json();
    if (!v.is(v.object({ docs: v.array(v.unknown()) }), body)) {
      throw new Error("Relationship option response has an invalid envelope");
    }
    const requested = new Set(ids.map((id) => `IN${id}`));
    const instruments = body.docs.map((document) => {
      const instrument = parseOrThrow(InstrumentSchema, document);
      if (databaseIdFromGlobalId(instrument.globalId, "IN") !== instrument.id) {
        throw new Error("Relationship option response contains an invalid instrument");
      }
      return instrument;
    });
    return Object.fromEntries(
      instruments
        .filter((instrument) => requested.has(`IN${instrument.id}`))
        .map((instrument) => [`IN${instrument.id}`, instrument]),
    );
  },
  ownsValue: (value) => databaseIdFromGlobalId(value, "IN") !== null,
  toOption: (document, context) => {
    const instrument = parseOrThrow(InstrumentSchema, document);
    return {
      value: instrument.globalId,
      label: instrument.name,
      content: (
        <InventoryItem
          name={instrument.name}
          globalId={instrument.globalId}
          href={`/globalId/${instrument.globalId}`}
          idLinkLabel={context.idLinkLabel(instrument.globalId)}
          compact={context.compact}
          size="xs"
        />
      ),
    };
  },
};

const GranteeSchema = v.object({
  kind: v.picklist(["USER", "GROUP", "AUDIENCE"]),
  id: v.union([v.number(), v.string()]),
  key: v.string(),
  name: v.string(),
  detail: v.optional(v.nullable(v.string())),
});

export function granteeRelationshipSource(resource: string, resourceId: number): RelationshipSource {
  const id = `grantees:${resource}:${resourceId}`;
  return {
    id,
    search: async (term, token, signal) => {
      const query = term.trim();
      if (query.length < 2) return [];
      const params = new URLSearchParams({ query, limit: "20" });
      const response = await fetch(`/api/v2/${resource}/${resourceId}/access/grantees?${params}`, {
        headers: { "X-Requested-With": "XMLHttpRequest", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        signal,
      });
      if (!response.ok) throw new Error(`Relationship option request failed with status ${response.status}`);
      const body: unknown = await response.json();
      return Array.isArray(body) ? body : [];
    },
    ownsValue: (value) => /^(user|group):\d+$/i.test(value),
    toOption: (document) => {
      const grantee = parseOrThrow(GranteeSchema, document);
      return { value: grantee.key, label: grantee.name };
    },
  };
}

/**
 * Pickable relationship targets, keyed by the `relationTo` a relationship field already declares,
 * so a collection configuration needs nothing extra to reach the right backend collection.
 *
 * ponytail: one entry, extended per target as more relationships become pickable. Searching every
 * target at once needs a different backend query, not a longer registry.
 */
export const relationshipSources: Readonly<Record<string, RelationshipSource>> = { instruments };
