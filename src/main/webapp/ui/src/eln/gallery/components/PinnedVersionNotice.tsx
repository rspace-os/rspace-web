import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";
import React from "react";
import { useTranslation } from "react-i18next";
import NavigateContext from "../../../stores/contexts/Navigate";

/**
 * Says which version of a Gallery item is on screen, that it is locked, and how
 * to get back to the live one.
 *
 * Shown both in the InfoPanel and as a page-level banner, because on a small
 * viewport the InfoPanel is a drawer that defaults to closed, and a notice the
 * user has to open a drawer to find is no notice at all. The way out has to be
 * beside the notice for the same reason.
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
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  /* No version segment: the live view is the ordinary, editable item view. */
  const liveHref = fileId === null ? null : `/gallery/item/${fileId}`;

  return (
    <Alert severity="info" sx={{ borderRadius: 0 }}>
      {t("pinnedVersion.notice", { version })}{" "}
      {liveHref !== null && (
        /* a real href, so the link can be copied and opened in a new tab, with
           the click handled in-app to avoid a full page load */
        <Link
          href={liveHref}
          onClick={(e: React.MouseEvent) => {
            e.preventDefault();
            navigate(liveHref);
          }}
        >
          {t("pinnedVersion.viewLatest")}
        </Link>
      )}
    </Alert>
  );
}
