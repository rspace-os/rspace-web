import { type RecordIconData } from "../../../../stores/definitions/BaseRecord";

const GLOBAL_ID_PATTERN = /^([A-Z]{2})\d+(?:v\d+)?$/;

const PREFIX_ICON_DATA: Record<string, RecordIconData> = {
  SA: { iconName: "sample", recordTypeLabel: "Sample" },
  SS: { iconName: "subsample", recordTypeLabel: "Subsample" },
  IC: { iconName: "container", recordTypeLabel: "Container" },
  IN: { iconName: "container", recordTypeLabel: "Instrument" },
};

/**
 * Returns the RecordTypeIcon data appropriate for the supplied Inventory
 * Global ID, or null when the prefix is not an inventory item.
 */
export function iconForInventoryGlobalId(
  globalId: string,
): RecordIconData | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  return PREFIX_ICON_DATA[match[1]] ?? null;
}
