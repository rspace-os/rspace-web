import React from "react";
import axios from "@/common/axios";
import { INVENTORY_PREFIX_ICON_DATA, prefixOf } from "@/Inventory/components/Fields/Link/iconForGlobalId";
import type { LinkableRecord } from "../../stores/definitions/LinkableRecord";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";

/**
 * An Inventory item that links to the current ELN record, to the extent the
 * Gallery info panel needs to render a back-reference row.
 */
export type ReferencingInventoryItem = {
  globalId: string;
  name: string;
  type: string;
  relationType: string;
  permalinkHref: string;
  linkableRecord: LinkableRecord;
};

/**
 * Given an ELN record's Global ID (e.g. a gallery file GLnnn), fetch the Inventory
 * items whose Link extra-field points at it. Backs the "Related inventory items"
 * section on the ELN side. The endpoint is permission-filtered server-side, so the
 * caller only ever receives items the user may read. Mirrors {@link useLinkedDocuments}.
 */
export default function useReferencingInventoryItems(globalId: string | null): {
  items: ReadonlyArray<ReferencingInventoryItem>;
  loading: boolean;
  errorMessage: string | null;
} {
  const [loading, setLoading] = React.useState(true);
  const [items, setItems] = React.useState<ReadonlyArray<ReferencingInventoryItem>>([]);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  const fetchReferencingItems = React.useCallback(async (): Promise<void> => {
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
      const { data } = await axios.get<unknown>(
        `/workspace/getReferencingInventoryItems/${encodeURIComponent(globalId)}`,
      );
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
                // relationType is nullable on the server; a row without one
                // must still render (as the legacy panel does) rather than be
                // silently dropped
                const relationType = Parsers.getValueWithKey("relationType")(obj).flatMap(Parsers.isString).orElse("");
                return Result.all(sourceGlobalId, name, type).map(([g, n, t]) => ({
                  globalId: g,
                  name: n,
                  type: t,
                  relationType,
                  permalinkHref: `/globalId/${g}`,
                  linkableRecord: {
                    id: parseInt(g.replace(/^[A-Z]{2}/, ""), 10) || null,
                    globalId: g,
                    name: n,
                    recordTypeLabel: INVENTORY_PREFIX_ICON_DATA[prefixOf(g) ?? ""]?.recordTypeLabel ?? "Item",
                    iconName: INVENTORY_PREFIX_ICON_DATA[prefixOf(g) ?? ""]?.iconName ?? "container",
                    permalinkURL: `/globalId/${g}`,
                  },
                }));
              })
              .do((row) => rows.push(row));
          }
        });
      setItems(rows);
    } catch (e) {
      console.error(e);
      setErrorMessage("Error loading related inventory items.");
    } finally {
      setLoading(false);
    }
  }, [globalId]);

  React.useEffect(() => {
    void fetchReferencingItems();
  }, [fetchReferencingItems]);

  return { items, loading, errorMessage };
}
