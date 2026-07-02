import type { AdjustableTableRowLabel } from "@/stores/definitions/Tables";

const columnLabelKeys = {
  Name: "sortProperties.name",
  Type: "sortProperties.type",
  "Global ID": "sortProperties.globalId",
  Created: "sortProperties.created",
  "Last Modified": "sortProperties.lastModified",
  Owner: "tableColumns.owner",
  Tags: "tableColumns.tags",
  Contents: "tableColumns.contents",
  "Number of Empty Locations": "tableColumns.numberOfEmptyLocations",
  "Container Type": "tableColumns.containerType",
  "Expiry Date": "tableColumns.expiryDate",
  "Subsamples Count": "tableColumns.subsamplesCount",
  Sample: "tableColumns.sample",
} as const;

type ColumnLabel = keyof typeof columnLabelKeys;
type ColumnTranslationKey = (typeof columnLabelKeys)[ColumnLabel];

const hasTranslatedColumnLabel = (label: AdjustableTableRowLabel): label is ColumnLabel => label in columnLabelKeys;

export const translateAdjustableTableLabel = (
  label: AdjustableTableRowLabel,
  translate: (key: ColumnTranslationKey) => string,
): string => {
  return hasTranslatedColumnLabel(label) ? translate(columnLabelKeys[label]) : label;
};
