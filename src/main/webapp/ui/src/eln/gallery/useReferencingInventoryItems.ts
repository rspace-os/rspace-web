import React from "react";
import axios from "@/common/axios";
import Result from "../../util/result";
import * as Parsers from "../../util/parsers";
import { type LinkableRecord } from "../../stores/definitions/LinkableRecord";

const INVENTORY_ICON_DATA: Record<
  string,
  { iconName: string; label: string }
> = {
  SA: { iconName: "sample", label: "Sample" },
  SS: { iconName: "subsample", label: "Subsample" },
  IC: { iconName: "container", label: "Container" },
  IN: { iconName: "container", label: "Instrument" },
};

function prefixOf(globalId: string): string | null {
  const match = /^([A-Z]{2})\d+/.exec(globalId);
  return match ? match[1] : null;
}

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

class LinkableInventoryRecord implements LinkableRecord {
  id: number | null;
  globalId: string | null;
  name: string;

  constructor({
    id,
    globalId,
    name,
  }: {
    id: number | null;
    globalId: string;
    name: string;
  }) {
    this.id = id;
    this.globalId = globalId;
    this.name = name;
  }

  get recordTypeLabel(): string {
    return INVENTORY_ICON_DATA[prefixOf(this.globalId ?? "") ?? ""]?.label ?? "Item";
  }

  get iconName(): string {
    return (
      INVENTORY_ICON_DATA[prefixOf(this.globalId ?? "") ?? ""]?.iconName ??
      "container"
    );
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

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
  const [items, setItems] = React.useState<
    ReadonlyArray<ReferencingInventoryItem>
  >([]);
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null);

  const fetchReferencingItems = React.useCallback(async (): Promise<void> => {
    if (!globalId) {
      setItems([]);
      setLoading(false);
      return;
    }
    setItems([]);
    setLoading(true);
    setErrorMessage(null);
    try {
      const { data } = await axios.get<unknown>(
        `/workspace/getReferencingInventoryItems/${globalId}`,
      );
      const rows: Array<ReferencingInventoryItem> = [];
      Parsers.objectPath(["referencingItems"], data)
        .flatMap(Parsers.isArray)
        .do((arr) => {
          for (const item of arr) {
            Parsers.isObject(item)
              .flatMap(Parsers.isNotNull)
              .flatMap((obj) => {
                const sourceGlobalId = Parsers.getValueWithKey("sourceGlobalId")(
                  obj,
                ).flatMap(Parsers.isString);
                const name = Parsers.getValueWithKey("sourceName")(obj).flatMap(
                  Parsers.isString,
                );
                const type = Parsers.getValueWithKey("sourceType")(obj).flatMap(
                  Parsers.isString,
                );
                const relationType = Parsers.getValueWithKey("relationType")(
                  obj,
                ).flatMap(Parsers.isString);
                return Result.all(
                  sourceGlobalId,
                  name,
                  type,
                  relationType,
                ).map(([g, n, t, r]) => ({
                  globalId: g,
                  name: n,
                  type: t,
                  relationType: r,
                  permalinkHref: `/globalId/${g}`,
                  linkableRecord: new LinkableInventoryRecord({
                    id: parseInt(g.replace(/^[A-Z]{2}/, ""), 10) || null,
                    globalId: g,
                    name: n,
                  }),
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
