import type Resources from "@/modules/common/i18n/resources";
import type { AdjustableTableRowLabel } from "@/stores/definitions/Tables";

type ColumnTranslationKey =
  | `sortProperties.${keyof Resources["inventory"]["sortProperties"]}`
  | `tableColumns.${keyof Resources["inventory"]["tableColumns"]}`;

const columnLabelKeys = {
  containerType: "tableColumns.containerType",
  contents: "tableColumns.contents",
  created: "sortProperties.created",
  currentLocation: "tableColumns.currentLocation",
  expiryDate: "tableColumns.expiryDate",
  globalId: "sortProperties.globalId",
  gridCoordinates: "tableColumns.gridCoordinates",
  lastModified: "sortProperties.lastModified",
  lastMoved: "tableColumns.lastMoved",
  name: "sortProperties.name",
  numberOfEmptyLocations: "tableColumns.numberOfEmptyLocations",
  owner: "tableColumns.owner",
  previousLocation: "tableColumns.previousLocation",
  quantity: "tableColumns.quantity",
  sample: "tableColumns.sample",
  subsamplesCount: "tableColumns.subsamplesCount",
  tags: "tableColumns.tags",
  type: "sortProperties.type",
  version: "tableColumns.version",
} satisfies Record<string, ColumnTranslationKey>;

type ColumnLabel = keyof typeof columnLabelKeys;

const hasTranslatedColumnLabel = (label: AdjustableTableRowLabel): label is ColumnLabel => label in columnLabelKeys;

export const translateAdjustableTableLabel = (
  label: AdjustableTableRowLabel,
  translate: (key: ColumnTranslationKey) => string,
): string => {
  return hasTranslatedColumnLabel(label) ? translate(columnLabelKeys[label]) : label;
};
