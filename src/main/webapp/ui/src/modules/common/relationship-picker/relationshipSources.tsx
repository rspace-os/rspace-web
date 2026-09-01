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
};

export type RelationshipSource = {
  /** Stable source identity, also used to partition query caches. */
  id: string;
  /** Optional REST API v2 resource name for collection-backed sources. */
  resourceName?: string;
  /** Global-ID prefix owned by this resource, used to recognize a pasted global ID. */
  globalIdPrefix?: string;
  /** Searches this source. The source owns its URL, scope, and query encoding. */
  search: (term: string, token: string | undefined, signal: AbortSignal) => Promise<readonly unknown[]>;
  /** Resolves a stored value when a picker is restored without a prior search. */
  resolve?: (value: string, token: string | undefined, signal: AbortSignal) => Promise<unknown | null>;
  /** Returns whether a stored value belongs to this source. */
  ownsValue: (value: string) => boolean;
  /** Validates one source document and renders it as a selectable option. */
  toOption: (document: unknown, context: RelationshipOptionContext) => RelationshipOption;
};

export type RelationshipOptionWithSource = RelationshipOption & {
  sourceId: string;
  /** Original validated source document, available to consumers needing domain metadata. */
  sourceDocument?: unknown;
};

const InstrumentSchema = v.object({
  id: v.number(),
  name: v.string(),
  globalId: v.string(),
});

const instruments: RelationshipSource = {
  id: "instruments",
  resourceName: "instruments",
  globalIdPrefix: "IN",
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

function databaseIdFromGlobalId(value: string, prefix: string): number | null {
  const match = new RegExp(`^${prefix}(\\d+)$`, "i").exec(value.trim());
  return match ? Number(match[1]) : null;
}

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
