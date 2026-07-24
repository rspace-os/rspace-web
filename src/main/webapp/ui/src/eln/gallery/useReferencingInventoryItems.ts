import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { INVENTORY_PREFIX_ICON_DATA, prefixOf } from "@/Inventory/components/Fields/Link/iconForGlobalId";
import type { LinkableRecord } from "../../stores/definitions/LinkableRecord";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";

/**
 * An Inventory item that is related to the current ELN record, to the extent the Gallery info panel
 * needs to render a back-reference row. The relation is either a Link (the item's Link field points
 * at the record, carrying a DataCite {@link relationType}) or an Attachment (the item attached this
 * Gallery file, in which case {@link relationType} holds the localised "Attachment" label).
 */
export type ReferencingInventoryItem = {
  globalId: string;
  name: string;
  type: string;
  relationType: string;
  permalinkHref: string;
  linkableRecord: LinkableRecord;
};

/** The Gallery media-file Global ID prefix; only these targets can be attached by Inventory items. */
const GALLERY_FILE_PREFIX = "GL";

/**
 * Given an ELN record's Global ID (e.g. a gallery file GLnnn), fetch the Inventory items related to
 * it for the "Related inventory items" back-reference section. For a Gallery file this fans out to
 * two permission-filtered endpoints and merges them into one list: the Link references and the
 * Attachments. For any other target only Links are fetched, since only Gallery files can be
 * attached. Both endpoints filter server-side, so the caller only ever receives items it may read.
 */
export default function useReferencingInventoryItems(globalId: string | null): {
  items: ReadonlyArray<ReferencingInventoryItem>;
  loading: boolean;
  errorMessage: string | null;
} {
  const [loading, setLoading] = React.useState(true);
  const [items, setItems] = React.useState<ReadonlyArray<ReferencingInventoryItem>>([]);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);
  const { t } = useTranslation(["gallery", "common", "inventory"]);

  React.useEffect(() => {
    // guard against a stale in-flight response overwriting a newer target's rows, and against
    // setState after unmount (matches the InfoPanel convention in this module)
    let cancelled = false;
    const fetchReferencingItems = async (): Promise<void> => {
      if (!globalId) {
        setItems([]);
        setErrorMessage(null);
        setLoading(false);
        return;
      }
      setItems([]);
      setLoading(true);
      setErrorMessage(null);
      try {
        const itemFallbackLabel = t("common:recordTypes.item.singular");
        const isGalleryFile = globalId.startsWith(GALLERY_FILE_PREFIX);
        const linksBody = axios
          .get<unknown>(`/workspace/getReferencingInventoryItems/${encodeURIComponent(globalId)}`)
          .then((r) => r.data);
        // a failed attachments lookup must not blank the links that loaded fine: degrade to
        // links-only by resolving the attachments half to an empty payload on error
        const attachmentsBody: Promise<unknown> = isGalleryFile
          ? axios
              .get<unknown>(`/workspace/getAttachingInventoryItems/${encodeURIComponent(globalId)}`)
              .then((r) => r.data)
              .catch((e: unknown) => {
                console.error(e);
                return { referencingItems: [] };
              })
          : Promise.resolve({ referencingItems: [] });
        const [links, attachments] = await Promise.all([linksBody, attachmentsBody]);
        if (cancelled) return;
        const rows = parseRows(links, null, itemFallbackLabel);
        if (isGalleryFile) {
          // attachments carry no DataCite relation type, so their Relation column shows a fixed label
          const attachmentLabel = t("inventory:fields.link.relatedInventoryItems.attachment");
          rows.push(...parseRows(attachments, attachmentLabel, itemFallbackLabel));
        }
        setItems(rows);
      } catch (e) {
        if (cancelled) return;
        console.error(e);
        setErrorMessage(t("referencingInventoryItems.loadFailed"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void fetchReferencingItems();
    return () => {
      cancelled = true;
    };
  }, [globalId, t]);

  return { items, loading, errorMessage };
}

/**
 * Parse a `{ referencingItems: [...] }` payload into rows. When {@link relationTypeOverride} is
 * given (attachments) it replaces the server's relation on every row; otherwise the server's
 * nullable relationType is used, defaulting to a blank so a relation-less row still renders.
 */
function parseRows(
  data: unknown,
  relationTypeOverride: string | null,
  itemFallbackLabel: string,
): Array<ReferencingInventoryItem> {
  const rows: Array<ReferencingInventoryItem> = [];
  Parsers.objectPath(["referencingItems"], data)
    .flatMap(Parsers.isArray)
    .do((arr) => {
      for (const item of arr) {
        Parsers.isObject(item)
          .flatMap(Parsers.isNotNull)
          .flatMap((obj) => {
            const sourceGlobalId = Parsers.getValueWithKey("sourceGlobalId")(obj).flatMap(Parsers.isString);
            const name = Parsers.getValueWithKey("sourceName")(obj).flatMap(Parsers.isString);
            const type = Parsers.getValueWithKey("sourceType")(obj).flatMap(Parsers.isString);
            // relationType is nullable on the server; a row without one must still render (as the
            // legacy panel does) rather than be silently dropped
            const relationType =
              relationTypeOverride ?? Parsers.getValueWithKey("relationType")(obj).flatMap(Parsers.isString).orElse("");
            return Result.all(sourceGlobalId, name, type).map(([g, n, ty]) => ({
              globalId: g,
              name: n,
              type: ty,
              relationType,
              permalinkHref: `/globalId/${g}`,
              linkableRecord: {
                id: parseInt(g.replace(/^[A-Z]{2}/, ""), 10) || null,
                globalId: g,
                name: n,
                recordTypeLabel: INVENTORY_PREFIX_ICON_DATA[prefixOf(g) ?? ""]?.recordTypeLabel ?? itemFallbackLabel,
                iconName: INVENTORY_PREFIX_ICON_DATA[prefixOf(g) ?? ""]?.iconName ?? "container",
                permalinkURL: `/globalId/${g}`,
              },
            }));
          })
          .do((row) => rows.push(row));
      }
    });
  return rows;
}
