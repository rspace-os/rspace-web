import Alert from "@mui/material/Alert";
import type React from "react";
import { useTranslation } from "react-i18next";
import { GalleryItemLink, galleryItemHref } from "./GalleryItemLink";

/**
 * Warns that the reference lists below it are the item's, not the displayed
 * version's.
 *
 * One notice above both lists rather than a caption inside each, so it plainly
 * covers Linked Documents and Related Inventory Items together, and stated at
 * body size rather than as small print because it corrects what the lists
 * otherwise imply.
 *
 * Nothing records the version a link or attachment was made against
 * (`inventoryFileDao.findByMediaFileId` takes an id alone), so a version-filtered
 * list is unrepresentable rather than merely unbuilt.
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
 * to get back to the live one. Shown in the InfoPanel, beside the item's other
 * details.
 *
 * This is the only notice: which version is shown is also carried by the item's
 * own version badge, and this is the only place offering the way back, alongside
 * the version history itself.
 *
 * @param version The version being displayed.
 * @param fileId  The item's id, for the link back to its live view. Null only if
 *                the item has no id, in which case there is nothing to link to.
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
