import { useTranslation } from "react-i18next";
import { BookableItemName } from "@/modules/booking/components/BookableItemName";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import type { CollectionConfig, CollectionTranslationKey } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { BookingConfiguration as AllBookableItem } from "../bookable-items/bookingConfiguration";
import { bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { ownerPresetField } from "../ownerPreset";

/** The instrument's Inventory location; the tooltip explains a bare container name such as "WB user1a". */
function InstrumentLocation({ name, globalId }: { name?: string | null; globalId?: string | null }) {
  const { t } = useTranslation("booking");
  if (name == null || globalId == null) return null;
  const workbench = name.startsWith("WB ");
  return (
    <Tooltip>
      <TooltipTrigger render={<span className="inline-flex min-w-0 items-center gap-1" />}>
        <InventoryLocationLink name={name} globalId={globalId} />
      </TooltipTrigger>
      <TooltipContent role="tooltip" className="rounded-sm">
        {workbench ? t("allBookableItems.location.workbenchDescription") : t("allBookableItems.location.description")}
      </TooltipContent>
    </Tooltip>
  );
}

function createAllBookableItemsConfig(openRecordLabel: (globalId: string) => string) {
  return resolveCollectionConfig({
    slug: "all-bookable-items",
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:allBookableItems.singular" satisfies CollectionTranslationKey,
      pluralKey: "booking:allBookableItems.plural" satisfies CollectionTranslationKey,
    },
    defaultColumns: ["target", "state"],
    listSearchableFields: ["target.name"],
    relationshipSources: bookingRelationshipSources,
    fields: [
      ownerPresetField,
      ...bookingConfigurationConfig.fields.filter((field) => !["id", "target", "enabled"].includes(field.name)),
      { name: "enabled", type: "boolean", labelKey: "booking:allBookableItems.fields.enabled", list: {}, form: false },
      {
        name: "id",
        type: "number",
        labelKey: "booking:allBookableItems.fields.id" satisfies CollectionTranslationKey,
        list: false,
        form: false,
      },
      {
        name: "target",
        type: "relationship",
        relationTo: "instruments",
        hasMany: false,
        labelKey: "booking:allBookableItems.fields.target" satisfies CollectionTranslationKey,
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: {
          width: 280,
          minWidth: 240,
          renderCell: ({ row }: { row: AllBookableItem }) =>
            row.target ? (
              <div className="w-60 max-w-full whitespace-normal wrap-break-word">
                <InventoryItem
                  nameClassName="whitespace-normal [&_a]:max-w-full"
                  name={<BookableItemName name={row.target.value.name} globalId={row.target.globalId} />}
                  globalId={row.target.globalId}
                  href={`/globalId/${row.target.globalId}`}
                  idLinkLabel={openRecordLabel(row.target.globalId)}
                  size="xs"
                >
                  <InstrumentLocation
                    name={
                      typeof row.target.value.parentContainer?.value === "object"
                        ? row.target.value.parentContainer.value.name
                        : undefined
                    }
                    globalId={row.target.value.parentContainer?.globalId}
                  />
                </InventoryItem>
              </div>
            ) : (
              <UnknownItem size="xs" />
            ),
        },
      },
    ],
  } as const satisfies CollectionConfig<AllBookableItem>);
}

export { createAllBookableItemsConfig };
