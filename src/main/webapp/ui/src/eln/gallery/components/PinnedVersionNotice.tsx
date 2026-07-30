import Alert from "@mui/material/Alert";
import type React from "react";
import { useTranslation } from "react-i18next";
import { GalleryItemLink, galleryItemHref } from "./GalleryItemLink";

/**
 * Warns that the reference lists below it are the item's, not the displayed
 * version's. One notice above both lists, so it plainly covers Linked Documents
 * and Related Inventory Items together. See CONTEXT.md, "Item-level reference",
 * for why a version-filtered list is unrepresentable.
 */
export function ItemLevelReferencesNotice(): React.ReactNode {
  const { t } = useTranslation("gallery");
  return (
    <Alert severity="info" icon={false} sx={{ mt: 1 }}>
      {t("pinnedVersion.referencesAreItemLevel")}
    </Alert>
  );
}

/**
 * Says which version of a Gallery item is on screen, that it is locked, and how
 * to get back to the live one. Shown in the InfoPanel. A null `fileId` means the
 * item has no id, so there is nothing to link back to.
 */
export default function PinnedVersionNotice({
  version,
  fileId,
}: {
  version: number;
  fileId: string | null;
}): React.ReactNode {
  const { t } = useTranslation("gallery");
  return (
    <Alert severity="info">
      {t("pinnedVersion.notice", { version })}{" "}
      {fileId !== null && (
        <GalleryItemLink href={galleryItemHref(fileId)}>{t("pinnedVersion.viewLatest")}</GalleryItemLink>
      )}
    </Alert>
  );
}
