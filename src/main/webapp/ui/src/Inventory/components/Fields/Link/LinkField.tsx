import EditIcon from "@mui/icons-material/Edit";
import HistoryIcon from "@mui/icons-material/History";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import type { ExtraInventoryLink } from "../../../../stores/definitions/ExtraField";
import ElnRecordInfoDialog from "./ElnRecordInfoDialog";
import InventoryInfoDialog from "./InventoryInfoDialog";
import { iconForGlobalId, isInventoryGlobalId, openUrlForTarget, supportsVersionPin } from "./iconForGlobalId";
import { GLOBAL_ID_PATTERN } from "./linkTarget";
import useLinkTargetSummary from "./useLinkTargetSummary";

// Read-only versioned viewer route added in RSDEV-1141 (matches MoreInfoSidebar/VersionHistory's
// `/inventory/{recordType}/{id}?version=N`). Keyed by inventory Global ID prefix.
const INVENTORY_PREFIX_TO_ROUTE: Record<string, string> = {
  SA: "sample",
  SS: "subsample",
  IC: "container",
  IN: "instrument",
};

/**
 * The URL the Open action points at, so it can be a plain anchor rather than a
 * scripted handler. A version-pinned inventory target opens the read-only
 * versioned viewer added in RSDEV-1141 (`/inventory/{type}/{id}?version=N`);
 * every other case uses the standard target route (`openUrlForTarget`: the
 * Gallery item route for GL, `/globalId/<gid>[vN]` for the rest).
 */
function openHrefForLink(link: ExtraInventoryLink, targetIsInventory: boolean): string {
  if (targetIsInventory && link.versionPin != null) {
    const match = GLOBAL_ID_PATTERN.exec(link.targetGlobalId);
    const routeType = match ? INVENTORY_PREFIX_TO_ROUTE[match[1]] : undefined;
    if (match && routeType) {
      return `/inventory/${routeType}/${match[2]}?version=${link.versionPin}`;
    }
  }
  return openUrlForTarget(link.targetGlobalId, link.versionPin);
}

export interface LinkFieldProps {
  /** Field name (the user-supplied label of the ExtraLinkField row) */
  name: string;
  link: ExtraInventoryLink;
  /** When false, the edit affordance is hidden. */
  editable: boolean;
  /**
   * Opens the link editor. Optional: custom (extra) Link fields are edited via
   * the field's settings cog (consistent with other custom field types), so
   * they omit this and no inline Edit button is shown. Template Link fields have
   * no cog, so they pass it and the inline Edit button is their edit affordance.
   */
  onEdit?: () => void;
}

export default function LinkField(props: LinkFieldProps): React.ReactElement {
  const { t } = useTranslation(["inventory", "common"]);
  const versionLabel =
    props.link.versionPin != null
      ? t("fields.link.editor.pinnedVersion", { version: props.link.versionPin })
      : t("fields.link.editor.latest");
  const iconData = iconForGlobalId(props.link.targetGlobalId);
  const targetIsInventory = isInventoryGlobalId(props.link.targetGlobalId);
  const [infoOpen, setInfoOpen] = useState(false);
  // current state of the target; null while loading or unresolvable, which
  // renders the card exactly as before the summary existed (no pill, Open on)
  const targetSummary = useLinkTargetSummary(props.link.targetGlobalId);
  const targetDeleted = targetSummary?.deleted === true;
  // ELN targets the viewer cannot read get a "No access" pill; inventory
  // targets ignore readability because every logged-in user keeps the
  // limited-read view. A redacted summary always has deleted=false, so the
  // two pills can never co-occur.
  const noAccess = targetSummary?.readable === false && !targetIsInventory;
  // deleted inventory items live on in the trash and their viewer works, so
  // only deleted or unreadable ELN targets lose Open (their routes are just
  // error pages)
  const openBlocked = !targetIsInventory && (targetDeleted || noAccess);

  const openHref = openHrefForLink(props.link, targetIsInventory);
  return (
    <Card variant="outlined" aria-label={props.name ? `Link field ${props.name}` : "Link field"}>
      <CardContent sx={{ "&:last-child": { pb: 2 } }}>
        <Box
          sx={{
            display: "flex",
            gap: 1,
            flexWrap: "wrap",
            alignItems: "center",
          }}
          data-test-id="LinkField-row"
        >
          {props.name && (
            <Typography variant="subtitle1" component="span" sx={{ fontWeight: 700 }}>
              {props.name}
            </Typography>
          )}
          <Chip size="small" label={props.link.relationType} data-test-id="LinkField-relation" />
          <Chip
            size="small"
            icon={iconData ? <RecordTypeIcon record={iconData} aria-hidden /> : undefined}
            label={props.link.targetGlobalId}
            data-test-id="LinkField-target"
          />
          <IconButton
            size="small"
            aria-label={`Show info for ${props.link.targetGlobalId}`}
            // an unreadable ("No access") target has nothing to show and its
            // info route is just an error page, so grey out info alongside the
            // (already-disabled) version-pin clock. Recomputed from the target
            // summary, so changing the link's target clears it and reverting
            // (e.g. Discard) to the unshared target reimposes it.
            disabled={noAccess}
            onClick={() => setInfoOpen(true)}
          >
            <InfoOutlinedIcon fontSize="small" />
          </IconButton>
          {props.editable && supportsVersionPin(props.link.targetGlobalId) && (
            // a disabled affordance only: like the other link properties,
            // the pin is changed in the link editor (Edit) and committed
            // with Update, not directly from the view card. The tooltip
            // wrapper says so, since a disabled button cannot.
            <Tooltip title={t("fields.link.linkField.editToChangePinned")}>
              <span>
                <IconButton size="small" aria-label={`Pin version for ${props.link.targetGlobalId}`} disabled>
                  <HistoryIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          <Chip size="small" variant="outlined" label={versionLabel} data-test-id="LinkField-version" />
          {targetDeleted && (
            <Chip
              size="small"
              color="warning"
              label={t("fields.link.linkField.targetDeleted")}
              data-test-id="LinkField-targetDeleted"
            />
          )}
          {noAccess && (
            // no "no longer" in the tooltip: the pill also shows for
            // viewers who never had access (ADR-0002)
            <Tooltip title={t("fields.link.linkField.noPermission")}>
              <Chip
                size="small"
                color="warning"
                label={t("fields.link.linkField.noAccess")}
                data-test-id="LinkField-noAccess"
              />
            </Tooltip>
          )}
          {!openBlocked && (
            <Button
              size="small"
              startIcon={<OpenInNewIcon />}
              href={openHref}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Open ${props.link.targetGlobalId}`}
            >
              {t("actions.open", { ns: "common" })}
            </Button>
          )}
          {props.editable && props.onEdit && (
            <Button
              size="small"
              startIcon={<EditIcon />}
              onClick={props.onEdit}
              aria-label={t("fields.link.linkField.editLink")}
            >
              {t("actions.edit", { ns: "common" })}
            </Button>
          )}
        </Box>
      </CardContent>
      {targetIsInventory ? (
        <InventoryInfoDialog
          open={infoOpen}
          globalId={props.link.targetGlobalId}
          versionPin={props.link.versionPin}
          onClose={() => setInfoOpen(false)}
        />
      ) : (
        <ElnRecordInfoDialog
          open={infoOpen}
          globalId={props.link.targetGlobalId}
          versionPin={props.link.versionPin}
          // a deleted or unreadable ELN target only routes to an error page, so
          // the dialog drops its Open button (matching the card). Deleted
          // inventory targets stay viewable in the trash and use
          // InventoryInfoDialog, which has no Open button.
          targetDeleted={targetDeleted}
          noAccess={noAccess}
          onClose={() => setInfoOpen(false)}
        />
      )}
    </Card>
  );
}
