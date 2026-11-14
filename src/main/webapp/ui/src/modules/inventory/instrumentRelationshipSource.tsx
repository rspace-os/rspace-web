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
import {
  databaseIdFromGlobalId,
  type RelationshipOptionContext,
  type RelationshipSource,
} from "@/modules/common/relationship-picker/sources";
import type { RelationshipOption } from "@/modules/common/relationship-picker/types";
import { InventoryItem } from "@/modules/common/ui/inventory-item";

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

/** Instruments as a pickable relationship target, keyed by the `relationTo` that names them. */
export const instrumentRelationshipSources: Readonly<Record<string, RelationshipSource>> = {
  instruments: instrumentRelationshipSource(apiV2Resources.instruments),
};
