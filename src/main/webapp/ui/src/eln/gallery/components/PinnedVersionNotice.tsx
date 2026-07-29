import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";
import React from "react";
import { useTranslation } from "react-i18next";
import NavigateContext from "../../../stores/contexts/Navigate";

/* No version segment: the live view is the ordinary, editable item view. */
const liveHrefFor = (fileId: string | null) => (fileId === null ? null : `/gallery/item/${fileId}`);

/**
 * A link to the live item, handled in-app.
 *
 * It keeps a real `href` so it can be copied or opened in a new tab, and calls
 * preventDefault so an ordinary click does not reload the page.
 */
function LiveVersionLink({ href, children }: { href: string; children: React.ReactNode }): React.ReactNode {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  return (
    <Link
      href={href}
      onClick={(e: React.MouseEvent) => {
        e.preventDefault();
        navigate(href);
      }}
    >
      {children}
    </Link>
  );
}

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
  const href = liveHrefFor(fileId);

  return (
    <Alert severity="info" sx={{ borderRadius: 0 }}>
      {t("pinnedVersion.notice", { version })}{" "}
      {href !== null && <LiveVersionLink href={href}>{t("pinnedVersion.viewLatest")}</LiveVersionLink>}
    </Alert>
  );
}
