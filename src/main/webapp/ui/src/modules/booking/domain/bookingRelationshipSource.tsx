import { cmp, eq } from "rsql-builder";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  databaseIdFromGlobalId,
  type RelationshipSource,
} from "@/modules/common/relationship-picker/relationshipSources";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { bookingApiV2Headers } from "./apiV2";
import { fetchBookingCatalogue } from "./bookingCatalogue";

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

/** Booking access owns discovery and reference resolution through configurations. */
export const bookingInstrumentSource: RelationshipSource = {
  id: "booking-instruments",
  globalIdPrefix: "IN",
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
  toOption: (document, context) => {
    const target = parseOrThrow(targetSchema, document);
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

export const bookingRelationshipSources = { "booking-instruments": bookingInstrumentSource };
