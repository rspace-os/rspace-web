import { useTranslation } from "react-i18next";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import type { AllBookableItem, AvailabilityQuickFilter } from "./availabilityQuickFilters";

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

function createAllBookableItemsConfig(
  availableNow: string,
  freeLaterToday: string,
  openRecordLabel: (globalId: string) => string,
) {
  return resolveCollectionConfig({
    slug: "all-bookable-items",
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:allBookableItems.singular",
      pluralKey: "booking:allBookableItems.plural",
    },
    defaultColumns: ["target"],
    listSearchableFields: ["target.name"],
    relationshipSources: bookingRelationshipSources,
    fields: [
      { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
      {
        name: "target",
        type: "relationship",
        relationTo: "booking-instruments",
        hasMany: false,
        labelKey: "booking:bookableItems.fields.target",
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: {
          width: 280,
          minWidth: 240,
          renderCell: ({ row }: { row: AllBookableItem }) =>
            row.target ? (
              <InventoryItem
                name={row.target.value.name}
                globalId={row.target.globalId}
                href={`/globalId/${row.target.globalId}`}
                idLinkLabel={openRecordLabel(row.target.globalId)}
                size="xs"
              >
                <InstrumentLocation
                  name={row.target.value.parentContainerName}
                  globalId={row.target.value.parentContainerGlobalId}
                />
              </InventoryItem>
            ) : (
              <UnknownItem size="xs" />
            ),
        },
      },
      {
        name: "availability",
        type: "select",
        labelKey: "booking:calendar.availability",
        options: [
          { label: availableNow, value: "available-now" },
          { label: freeLaterToday, value: "free-later-today" },
        ],
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: false,
        form: false,
      },
    ],
  } as const satisfies CollectionConfig<AllBookableItem>);
}

function comparisons(
  expression: FilterExpression<AllBookableItem> | null,
): readonly Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>[] {
  if (!expression) return [];
  if (expression.kind === "comparison") return [expression];
  if (expression.kind === "or") return [];
  return expression.children.flatMap(comparisons);
}

function filterValue(
  expression: FilterExpression<AllBookableItem> | null,
  field: "availability" | "target",
): string | undefined {
  const value = comparisons(expression).find(
    (comparison) => comparison.field === field && comparison.operator === "equals",
  )?.value;
  return typeof value === "string" ? value : undefined;
}

function availabilityMode(expression: FilterExpression<AllBookableItem> | null): AvailabilityQuickFilter | undefined {
  const values = new Set(
    comparisons(expression)
      .filter((rule) => rule.field === "availability")
      .map((rule) => rule.value),
  );
  if (values.size !== 1) return undefined;
  const value = filterValue(expression, "availability");
  return value === "available-now" || value === "free-later-today" ? value : undefined;
}

function filterExpression(
  target: string | undefined,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  const values: FilterExpression<AllBookableItem>[] = [];
  if (target) values.push({ kind: "comparison", field: "target", operator: "equals", value: target });
  if (availability) {
    values.push({ kind: "comparison", field: "availability", operator: "equals", value: availability });
  }
  return values.length === 0 ? null : values.length === 1 ? values[0] : { kind: "and", children: values };
}

export { availabilityMode, createAllBookableItemsConfig, filterExpression };
