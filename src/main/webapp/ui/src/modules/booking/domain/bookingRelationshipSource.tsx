import * as v from "valibot";
import { listBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { InstrumentsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { databaseIdFromGlobalId, type RelationshipSource } from "@/modules/common/relationship-picker/sources";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { fetchBookingCatalogue } from "./bookingCatalogue";
import { requireBookingValue as required } from "./bookingMapping";

const validId = (value: string) => databaseIdFromGlobalId(value, "IN") !== null;
const canonicalId = (value: string) => {
  const id = databaseIdFromGlobalId(value, "IN");
  return id === null ? null : `IN${id}`;
};
/** Booking access owns discovery and reference resolution through configurations. */
export const bookingInstrumentSource: RelationshipSource = {
  id: "items",
  globalIdPrefix: "IN",
  normalizeValue: canonicalId,
  ownsValue: validId,
  batchSize: 100,
  search: async (term, signal) => {
    const value = term.trim();
    const page = await fetchBookingCatalogue(
      { ...(validId(value) ? { target: value.toUpperCase() } : { q: value }), pageSize: 20 },
      signal,
    );
    return page.items.map((item) => ({ id: item.targetId, name: item.name, globalId: item.globalId }));
  },
  resolveMany: async (values, signal) => {
    const targets = [...new Set(values.map(canonicalId).filter((value): value is string => value !== null))];
    if (targets.length === 0) return {};
    const result = await listBookingConfigurations(
      {
        where: `target=in=(${targets.join(",")})`,
        limit: Math.min(targets.length, 100),
        depth: 1,
        fields: { "booking-configurations": "id,target" },
      },
      { signal },
    );
    const requested = new Set(targets);
    return Object.fromEntries(
      result.docs.flatMap(({ target }) => {
        const key = target ? canonicalId(target.globalId) : null;
        if (!target || key === null || !requested.has(key)) return [];
        if (typeof target.value === "number") throw new Error("Booking reference is not expanded");
        return [[key, { ...target.value, name: required(target.value.name, "target.name"), globalId: key }]];
      }),
    );
  },
  toOption: (document, context) => {
    const target = v.parse(v.required(v.pick(InstrumentsReadSchema, ["id", "name", "globalId"])), document);
    return {
      value: target.globalId,
      label: target.name,
      content: (
        <InventoryItem
          name={target.name}
          globalId={target.globalId}
          href={`/globalId/${target.globalId}`}
          idLinkLabel={context.idLinkLabel(target.globalId)}
          compact={context.compact}
          size="xs"
        />
      ),
    };
  },
};

export const bookingRelationshipSources = { instruments: bookingInstrumentSource };
