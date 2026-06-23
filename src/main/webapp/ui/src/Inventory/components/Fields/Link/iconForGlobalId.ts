import type { RecordIconData } from "../../../../stores/definitions/BaseRecord";

import { GLOBAL_ID_PATTERN } from "./linkTarget";

export const INVENTORY_PREFIX_ICON_DATA: Record<string, RecordIconData> = {
  SA: { iconName: "sample", recordTypeLabel: "Sample" },
  SS: { iconName: "subsample", recordTypeLabel: "Subsample" },
  IC: { iconName: "container", recordTypeLabel: "Container" },
  IN: { iconName: "container", recordTypeLabel: "Instrument" },
  IT: { iconName: "template", recordTypeLabel: "Sample template" },
};

// RecordTypeIcon supports "document" and "gallery" but has no dedicated notebook
// icon, so notebooks reuse the document icon (with a Notebook label).
const ELN_PREFIX_ICON_DATA: Record<string, RecordIconData> = {
  SD: { iconName: "document", recordTypeLabel: "Document" },
  NB: { iconName: "document", recordTypeLabel: "Notebook" },
  GL: { iconName: "gallery", recordTypeLabel: "Gallery file" },
};

/** Returns the two-letter prefix of a Global ID (ignoring any version suffix), or null. */
export function prefixOf(globalId: string): string | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  return match ? match[1] : null;
}

/**
 * Returns the RecordTypeIcon data appropriate for the supplied Inventory Global ID, or null when
 * the prefix is not an inventory item.
 */
export function iconForInventoryGlobalId(globalId: string): RecordIconData | null {
  const prefix = prefixOf(globalId);
  return prefix ? (INVENTORY_PREFIX_ICON_DATA[prefix] ?? null) : null;
}

/**
 * Returns RecordTypeIcon data for any supported link target (Inventory item or ELN document,
 * notebook or gallery file), or null when the prefix is not a supported target. Only ever returns
 * iconName values that RecordTypeIcon knows about, since that component throws on unknown names.
 */
export function iconForGlobalId(globalId: string): RecordIconData | null {
  const prefix = prefixOf(globalId);
  if (!prefix) return null;
  return INVENTORY_PREFIX_ICON_DATA[prefix] ?? ELN_PREFIX_ICON_DATA[prefix] ?? null;
}

/** True when the Global ID is an Inventory item (sample, subsample, container, instrument). */
export function isInventoryGlobalId(globalId: string): boolean {
  const prefix = prefixOf(globalId);
  return prefix != null && prefix in INVENTORY_PREFIX_ICON_DATA;
}

/**
 * Targets that support version pinning in the link UI: Inventory items and ELN documents (SD).
 * Notebooks (NB) and gallery files (GL) do not, so the version-pin affordance must not be rendered
 * for them.
 */
export function supportsVersionPin(globalId: string): boolean {
  return isInventoryGlobalId(globalId) || prefixOf(globalId) === "SD";
}

/**
 * URL to OPEN a link target in a new tab. A Gallery file (GL) opens at its
 * location in the Gallery (/gallery/item/<id>) rather than downloading via
 * /globalId, which is the misleading default the backend resolves to Streamfile.
 * Every other target keeps the existing /globalId/<gid>[v<n>] behaviour, which
 * the backend resolves per type (e.g. SD documents, inventory items). GL cannot
 * be version-pinned (see supportsVersionPin), so the gallery route ignores
 * versionPin by design.
 */
export function openUrlForTarget(globalId: string, versionPin: number | null): string {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (match && match[1] === "GL") {
    return `/gallery/item/${match[2]}`;
  }
  // rebuild from the parsed base id: rows written before the backend
  // normalised stored ids can already carry a "vN" suffix, which must not be
  // doubly encoded (e.g. /globalId/SS42v7v7)
  const base = match ? `${match[1]}${match[2]}` : globalId;
  const versionSuffix = versionPin != null ? `v${versionPin}` : "";
  return `/globalId/${base}${versionSuffix}`;
}
