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
  /** REST API v2 resource name: the picker reads `/api/v2/<resourceName>`. */
  resourceName: string;
  /** Global-ID prefix owned by this resource, used to recognize a pasted global ID. */
  globalIdPrefix: string;
  /** The narrow sparse fieldset: only the fields an option needs to render. */
  fields: readonly string[];
  /** The field searched with `=contains=` when the input is not a global ID. */
  searchField: string;
  /** Validates one API document and renders it as a selectable option. */
  toOption: (document: unknown, context: RelationshipOptionContext) => RelationshipOption;
};

const InstrumentSchema = v.object({
  id: v.number(),
  name: v.string(),
  globalId: v.string(),
});

const instruments: RelationshipSource = {
  resourceName: "instruments",
  globalIdPrefix: "IN",
  fields: ["id", "name", "globalId"],
  searchField: "name",
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

/**
 * Pickable relationship targets, keyed by the `relationTo` a relationship field already declares,
 * so a collection configuration needs nothing extra to reach the right backend collection.
 *
 * ponytail: one entry, extended per target as more relationships become pickable. Searching every
 * target at once needs a different backend query, not a longer registry.
 */
export const relationshipSources: Readonly<Record<string, RelationshipSource>> = { instruments };
