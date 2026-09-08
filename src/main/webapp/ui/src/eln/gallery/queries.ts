import { useQuery } from "@tanstack/react-query";
import axios from "@/common/axios";
import { INVENTORY_PREFIX_ICON_DATA, prefixOf } from "@/Inventory/components/Fields/Link/iconForGlobalId";
import i18n from "@/modules/common/i18n";
import { getLinkedDocuments as fetchLinkedDocuments } from "@/modules/workspace/linkedRecords";
import type { PrivateLinkedRecordsByOwner } from "@/modules/workspace/schema";
import type { LinkableRecord } from "../../stores/definitions/LinkableRecord";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import { type GalleryFile, idToString } from "./useGalleryListing";

/**
 * Both desktop and mobile info panels remain mounted in the responsive layout. This keeps a
 * completed request fresh while the second observer and React Strict Mode attach.
 */
const INFO_PANEL_STALE_TIME = 5_000;

export type Document = {
  id: number;
  globalId: string;
  name: string;
  permalinkHref: string;
  linkableRecord: LinkableRecord;
};

export type ReferencingInventoryItem = {
  globalId: string;
  name: string;
  type: string;
  relationType: string;
  isAttachment: boolean;
  permalinkHref: string;
  linkableRecord: LinkableRecord;
};

type GetReferencingInventoryItemsParams = {
  globalId: string;
};

type UseReferencingInventoryItemsQueryParams = {
  globalId: string | null;
};

/** The Gallery media-file Global ID prefix; only these targets can be attached by Inventory items. */
const GALLERY_FILE_PREFIX = "GL";

export const galleryQueryKeys = {
  all: ["rspace.gallery"] as const,
  linkedDocuments: (fileId: string) => [...galleryQueryKeys.all, "linkedDocuments", fileId] as const,
  referencingInventoryItems: (globalId: string | null) =>
    [...galleryQueryKeys.all, "referencingInventoryItems", globalId] as const,
};

class LinkableDocument implements LinkableRecord {
  id: number | null;
  globalId: string | null;
  name: string;

  constructor({ id, globalId, name }: { id: number; globalId: string; name: string }) {
    this.id = id;
    this.globalId = globalId;
    this.name = name;
  }

  get recordTypeLabel(): string {
    return i18n.t("common:recordTypes.document.singular");
  }

  get iconName(): string {
    return "document";
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

export type LinkedDocuments = {
  documents: ReadonlyArray<Document>;
  /** Per-owner counts of linking documents the caller cannot read (RSDEV-1329). */
  privateByOwner: ReadonlyArray<PrivateLinkedRecordsByOwner>;
};

/**
 * RSDEV-1329: the endpoint returns owner-only placeholder rows (no id/oid/name) for documents the
 * caller cannot read. Those are surfaced as per-owner counts rather than as table rows.
 */
export async function getLinkedDocuments(fileId: string): Promise<LinkedDocuments> {
  const { readable, privateByOwner } = await fetchLinkedDocuments(Number(fileId));
  return {
    documents: readable.map(({ id, globalId, name }) => ({
      id,
      globalId,
      name,
      permalinkHref: `/globalId/${globalId}`,
      linkableRecord: new LinkableDocument({ id, globalId, name }),
    })),
    privateByOwner,
  };
}

/** Folders and snippets are not media files; the hardened endpoint rejects their ids outright. */
function isLinkableMediaFile(file: GalleryFile): boolean {
  return !(file.isFolder || file.isSnippet || file.isSnippetFolder);
}

export function useLinkedDocumentsQuery(file: GalleryFile) {
  const fileId = idToString(file.id).elseThrow();
  return useQuery({
    queryKey: galleryQueryKeys.linkedDocuments(fileId),
    queryFn: () => getLinkedDocuments(fileId),
    enabled: isLinkableMediaFile(file),
    retry: false,
    staleTime: INFO_PANEL_STALE_TIME,
  });
}

/**
 * Fetch Inventory items that link to the target and, for Gallery files, items that attach it.
 * Attachment lookup failures are supplementary and therefore degrade to a links-only result.
 */
export async function getReferencingInventoryItems({
  globalId,
}: GetReferencingInventoryItemsParams): Promise<ReadonlyArray<ReferencingInventoryItem>> {
  const isGalleryFile = globalId.startsWith(GALLERY_FILE_PREFIX);
  const linksBody = axios
    .get<unknown>(`/workspace/getReferencingInventoryItems/${encodeURIComponent(globalId)}`)
    .then((response) => response.data);
  if (!isGalleryFile) return parseReferencingInventoryItems(await linksBody, false);

  const attachmentsBody = axios
    .get<unknown>(`/workspace/getAttachingInventoryItems/${encodeURIComponent(globalId)}`)
    .then((response) => response.data)
    .catch((error: unknown) => {
      console.error(error);
      return { referencingItems: [] };
    });
  const [links, attachments] = await Promise.all([linksBody, attachmentsBody]);
  return [...parseReferencingInventoryItems(links, false), ...parseReferencingInventoryItems(attachments, true)];
}

export function useReferencingInventoryItemsQuery({ globalId }: UseReferencingInventoryItemsQueryParams) {
  return useQuery({
    queryKey: galleryQueryKeys.referencingInventoryItems(globalId),
    queryFn: () => (globalId ? getReferencingInventoryItems({ globalId }) : []),
    enabled: Boolean(globalId),
    retry: false,
    staleTime: INFO_PANEL_STALE_TIME,
  });
}

/** Parse a `{ referencingItems: [...] }` payload into rows, skipping malformed entries. */
function parseReferencingInventoryItems(data: unknown, isAttachment: boolean): Array<ReferencingInventoryItem> {
  const rows: Array<ReferencingInventoryItem> = [];
  Parsers.objectPath(["referencingItems"], data)
    .flatMap(Parsers.isArray)
    .do((items) => {
      for (const item of items) {
        Parsers.isObject(item)
          .flatMap(Parsers.isNotNull)
          .flatMap((obj) => {
            const sourceGlobalId = Parsers.getValueWithKey("sourceGlobalId")(obj).flatMap(Parsers.isString);
            const name = Parsers.getValueWithKey("sourceName")(obj).flatMap(Parsers.isString);
            const type = Parsers.getValueWithKey("sourceType")(obj).flatMap(Parsers.isString);
            const relationType = Parsers.getValueWithKey("relationType")(obj).flatMap(Parsers.isString).orElse("");

            return Result.all(sourceGlobalId, name, type).map(([globalId, itemName, itemType]) => {
              const iconData = INVENTORY_PREFIX_ICON_DATA[prefixOf(globalId) ?? ""];
              return {
                globalId,
                name: itemName,
                type: itemType,
                relationType,
                isAttachment,
                permalinkHref: `/globalId/${globalId}`,
                linkableRecord: {
                  id: parseInt(globalId.replace(/^[A-Z]{2}/, ""), 10) || null,
                  globalId,
                  name: itemName,
                  get recordTypeLabel() {
                    return iconData?.recordTypeLabel ?? i18n.t("common:recordTypes.item.singular");
                  },
                  iconName: iconData?.iconName ?? "container",
                  permalinkURL: `/globalId/${globalId}`,
                },
              };
            });
          })
          .do((row) => rows.push(row));
      }
    });
  return rows;
}
