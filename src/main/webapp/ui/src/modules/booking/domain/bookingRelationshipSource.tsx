import * as v from "valibot";
import { listBookingConfigurations } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { BookingCatalogueManagerLocationSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { databaseIdFromGlobalId, type RelationshipSource } from "@/modules/common/relationship-picker/sources";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { instrumentRelationshipOption } from "@/modules/inventory/instrumentRelationshipSource";
import { fetchBookingCatalogue, fetchBookingCatalogueLocations } from "./bookingCatalogue";
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
  browsable: true,
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
  toOption: instrumentRelationshipOption,
};

const locationId = (value: string) => databaseIdFromGlobalId(value, "IC") ?? databaseIdFromGlobalId(value, "BE");
const canonicalLocation = (value: string) => {
  const id = locationId(value);
  return id === null ? null : `IC${id}`;
};
export const bookingLocationSource: RelationshipSource = {
  id: "locations",
  globalIdPrefix: "IC",
  browsable: true,
  normalizeValue: canonicalLocation,
  ownsValue: (value) => locationId(value) !== null,
  search: async (term, signal) => (await fetchBookingCatalogueLocations({ q: term, pageSize: 20 }, signal)).items,
  batchSize: 100,
  resolveMany: async (values, signal) => {
    const globalIds = [...new Set(values.map(canonicalLocation).filter((value): value is string => value !== null))];
    if (globalIds.length === 0) return {};
    const page = await fetchBookingCatalogueLocations({ globalIds, pageSize: globalIds.length }, signal);
    const requested = new Set(globalIds);
    return Object.fromEntries(
      page.items.flatMap((location) => {
        const key = canonicalLocation(location.globalId);
        return key !== null && requested.has(key) ? [[key, location]] : [];
      }),
    );
  },
  toOption: (document, context) => {
    const location = v.parse(v.required(v.pick(BookingCatalogueManagerLocationSchema, ["globalId", "name"])), document);
    const value = canonicalLocation(location.globalId);
    if (value === null) throw new Error("Booking location has an invalid global ID");
    return {
      value,
      label: location.name,
      content: <InventoryItem name={location.name} globalId={location.globalId} compact={context.compact} size="xs" />,
    };
  },
};
export const bookingRelationshipSources = { instruments: bookingInstrumentSource, containers: bookingLocationSource };
