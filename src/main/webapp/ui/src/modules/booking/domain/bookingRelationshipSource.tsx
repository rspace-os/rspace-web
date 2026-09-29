import { cmp, eq } from "rsql-builder";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  databaseIdFromGlobalId,
  type RelationshipSource,
} from "@/modules/common/relationship-picker/relationshipSources";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { bookingApiV2Headers } from "./apiV2";
import { fetchBookingCatalogue, fetchBookingCatalogueLocations } from "./bookingCatalogue";

const targetSchema = v.object({ id: v.number(), name: v.string(), globalId: v.string() });
const referenceSchema = v.object({
  docs: v.array(
    v.object({
      target: v.object({
        globalId: v.string(),
        value: v.object({ id: v.number(), name: v.string() }),
      }),
    }),
  ),
});
const validId = (value: string) => databaseIdFromGlobalId(value, "IN") !== null;
const canonicalId = (value: string) => {
  const id = databaseIdFromGlobalId(value, "IN");
  return id === null ? null : `IN${id}`;
};

/** Booking access owns discovery and reference resolution through configurations. */
export const bookingInstrumentSource: RelationshipSource = {
  id: "booking-instruments",
  globalIdPrefix: "IN",
  // The catalogue answers an empty term with its first page, so the filter lists items at once.
  browsable: true,
  normalizeValue: canonicalId,
  ownsValue: validId,
  search: async (term, token, signal) => {
    const value = term.trim();
    const page = await fetchBookingCatalogue(
      {
        ...(validId(value) ? { target: value.toUpperCase() } : { q: value }),
        pageSize: 20,
      },
      token ?? "",
      signal,
    );
    return page.items.map((item) => ({ id: item.targetId, name: item.name, globalId: item.globalId }));
  },
  resolve: async (value, token, signal) => {
    if (!validId(value)) return null;
    const parameters = new URLSearchParams({
      where: cmp("target", eq(value.toUpperCase())).toString(),
      limit: "1",
      depth: "1",
      "fields[booking-configurations]": "id,target",
    });
    const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
      headers: bookingApiV2Headers(token ?? ""),
      signal,
    });
    if (!response.ok) throw new Error(`Booking reference request failed (${response.status})`);
    const target = parseOrThrow(referenceSchema, await response.json()).docs[0]?.target;
    return target ? { ...target.value, globalId: target.globalId } : null;
  },
  batchSize: 100,
  resolveMany: async (values, token, signal) => {
    const targets = [...new Set(values.map(canonicalId).filter((value): value is string => value !== null))];
    if (targets.length === 0) return {};
    const parameters = new URLSearchParams({
      where: `target=in=(${targets.join(",")})`,
      limit: String(Math.min(targets.length, 100)),
      depth: "1",
      "fields[booking-configurations]": "id,target",
    });
    const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
      headers: bookingApiV2Headers(token ?? ""),
      signal,
    });
    if (!response.ok) throw new Error(`Booking reference request failed (${response.status})`);
    const documents = parseOrThrow(referenceSchema, await response.json()).docs;
    const requested = new Set(targets);
    return Object.fromEntries(
      documents.flatMap(({ target }) => {
        const key = canonicalId(target.globalId);
        return key !== null && requested.has(key) ? [[key, { ...target.value, globalId: key }]] : [];
      }),
    );
  },
  toOption: (document, context) => {
    const target = parseOrThrow(targetSchema, document);
    return {
      value: target.globalId,
      label: target.name,
      content: <InventoryItem name={target.name} globalId={target.globalId} compact={context.compact} size="xs" />,
    };
  },
};

const locationSchema = v.object({ globalId: v.string(), name: v.string() });
/** Containers and workbenches share one ID space, so either global ID names the same row. */
const locationId = (value: string) => databaseIdFromGlobalId(value, "IC") ?? databaseIdFromGlobalId(value, "BE");
const canonicalLocation = (value: string) => {
  const id = locationId(value);
  return id === null ? null : `IC${id}`;
};

/**
 * Immediate Inventory parents of bookable items, among the Containers the caller can read. The
 * filter value is `IC<id>`, which also addresses a workbench; the option still shows its `BE` ID.
 */
export const bookingLocationSource: RelationshipSource = {
  id: "booking-locations",
  globalIdPrefix: "IC",
  browsable: true,
  normalizeValue: canonicalLocation,
  ownsValue: (value) => locationId(value) !== null,
  search: async (term, token, signal) =>
    (await fetchBookingCatalogueLocations({ q: term, pageSize: 20 }, token ?? "", signal)).items,
  batchSize: 100,
  resolveMany: async (values, token, signal) => {
    const globalIds = [...new Set(values.map(canonicalLocation).filter((value): value is string => value !== null))];
    if (globalIds.length === 0) return {};
    const page = await fetchBookingCatalogueLocations({ globalIds, pageSize: globalIds.length }, token ?? "", signal);
    const requested = new Set(globalIds);
    return Object.fromEntries(
      page.items.flatMap((location) => {
        const key = canonicalLocation(location.globalId);
        return key !== null && requested.has(key) ? [[key, location]] : [];
      }),
    );
  },
  toOption: (document, context) => {
    const location = parseOrThrow(locationSchema, document);
    const value = canonicalLocation(location.globalId);
    if (value === null) throw new Error("Booking location has an invalid global ID");
    return {
      value,
      label: location.name,
      content: <InventoryItem name={location.name} globalId={location.globalId} compact={context.compact} size="xs" />,
    };
  },
};

export const bookingRelationshipSources = {
  "booking-instruments": bookingInstrumentSource,
  "booking-locations": bookingLocationSource,
};
