import { escapeRegExp } from "es-toolkit";
import { cmp, eq, escapeValue, Operation } from "rsql-builder";
import * as v from "valibot";
import {
  type ApiV2ReadOperation,
  type ApiV2ResourceEntry,
  apiV2Resources,
} from "@/modules/common/api/v2/apiV2Resources";
import type {
  getListInstrumentsQueryKey,
  listInstruments,
} from "@/modules/common/api/v2/generated/instruments/instruments";
import { InstrumentsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import type { RelationshipFilterPicker } from "@/modules/common/collection/schema";
import type { RelationshipOption } from "@/modules/common/relationship-picker/types";
import { InventoryItem } from "@/modules/common/ui/inventory-item";

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

function instrumentDocument(document: unknown) {
  const instrument = v.parse(InstrumentsReadSchema, document);
  if (instrument.name === undefined || instrument.globalId === undefined) {
    throw new Error("Relationship option response contains an incomplete instrument");
  }
  return { ...instrument, name: instrument.name, globalId: instrument.globalId };
}

export function instrumentRelationshipOption(
  document: unknown,
  context: RelationshipOptionContext,
): RelationshipOption {
  const instrument = instrumentDocument(document);
  return {
    value: instrument.globalId,
    label: instrument.name,
    content: (
      <InventoryItem name={instrument.name} globalId={instrument.globalId} compact={context.compact} size="xs" />
    ),
  };
}

export function instrumentRelationshipSource(
  resource: ApiV2ResourceEntry<
    string,
    { list: ApiV2ReadOperation<typeof listInstruments, typeof getListInstrumentsQueryKey> }
  >,
): RelationshipSource {
  return {
    id: "instruments",
    resourceName: "instruments",
    globalIdPrefix: "IN",
    normalizeValue: (value) => {
      const id = databaseIdFromGlobalId(value, "IN");
      return id === null ? null : `IN${id}`;
    },
    search: async (term, signal) => {
      const value = term.trim();
      const id = databaseIdFromGlobalId(value, "IN");
      const body = await resource.list.request(
        {
          page: 1,
          limit: 20,
          fields: { instruments: "id,name,globalId" },
          where:
            id === null
              ? cmp("name", new Operation(escapeValue(value), "=contains=")).toString()
              : cmp("id", eq(id)).toString(),
        },
        { signal },
      );
      return body.docs ?? [];
    },
    batchSize: 100,
    resolveMany: async (values, signal) => {
      const ids = [...new Set(values.map((value) => databaseIdFromGlobalId(value, "IN")))].filter(
        (id): id is number => id !== null,
      );
      if (ids.length === 0) return {};
      const body = await resource.list.request(
        {
          page: 1,
          limit: Math.min(ids.length, 100),
          where: `id=in=(${ids.join(",")})`,
          fields: { instruments: "id,name,globalId" },
        },
        { signal },
      );
      const requested = new Set(ids.map((id) => `IN${id}`));
      const instruments = (body.docs ?? []).map((document) => {
        const instrument = instrumentDocument(document);
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
    toOption: instrumentRelationshipOption,
  };
}

const instruments = instrumentRelationshipSource(apiV2Resources.instruments);

/**
 * Pickable relationship targets, keyed by the `relationTo` a relationship field already declares,
 * so a collection configuration needs nothing extra to reach the right backend collection.
 *
 * ponytail: one entry, extended per target as more relationships become pickable. Searching every
 * target at once needs a different backend query, not a longer registry.
 */
export const relationshipSources: Readonly<Record<string, RelationshipSource>> = { instruments };

export function relationshipSourceForPicker(
  picker: RelationshipFilterPicker,
  sources: Readonly<Record<string, RelationshipSource>>,
): RelationshipSource | undefined {
  const source = sources[picker.resource];
  return source?.globalIdPrefix === picker.globalIdPrefix ? source : undefined;
}
