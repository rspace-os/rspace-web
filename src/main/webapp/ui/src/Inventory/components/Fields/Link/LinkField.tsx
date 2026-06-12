import React, { useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Tooltip from "@mui/material/Tooltip";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import EditIcon from "@mui/icons-material/Edit";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import HistoryIcon from "@mui/icons-material/History";
import { type ExtraInventoryLink } from "../../../../stores/definitions/ExtraField";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import {
  iconForGlobalId,
  isInventoryGlobalId,
  supportsVersionPin,
} from "./iconForGlobalId";
import InventoryInfoDialog from "./InventoryInfoDialog";
import ElnRecordInfoDialog from "./ElnRecordInfoDialog";
import useLinkTargetSummary from "./useLinkTargetSummary";

// Read-only versioned viewer route added in RSDEV-1141 (matches MoreInfoSidebar/VersionHistory's
// `/inventory/{recordType}/{id}?version=N`). Keyed by inventory Global ID prefix.
const INVENTORY_PREFIX_TO_ROUTE: Record<string, string> = {
  SA: "sample",
  SS: "subsample",
  IC: "container",
  IN: "instrument",
};

export interface LinkFieldProps {
  /** Field name (the user-supplied label of the ExtraLinkField row) */
  name: string;
  link: ExtraInventoryLink;
  /** When false, the edit affordance is hidden. */
  editable: boolean;
  onOpen: () => void;
  onEdit: () => void;
}

export default function LinkField(props: LinkFieldProps): React.ReactElement {
  const versionLabel =
    props.link.versionPin != null ? `Pinned to v${props.link.versionPin}` : "Latest";
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

  const handleOpen = () => {
    // A version-pinned inventory target opens the read-only versioned viewer (RSDEV-1141)
    // rather than the live record; every other case defers to the parent open handler.
    if (targetIsInventory && props.link.versionPin != null) {
      const match = /^([A-Z]{2})(\d+)/.exec(props.link.targetGlobalId);
      const routeType = match ? INVENTORY_PREFIX_TO_ROUTE[match[1]] : undefined;
      if (match && routeType) {
        window.open(
          `/inventory/${routeType}/${match[2]}?version=${props.link.versionPin}`,
          "_blank",
        );
        return;
      }
    }
    props.onOpen();
  };
  return (
    <Card
      variant="outlined"
      aria-label={props.name ? `Link field ${props.name}` : "Link field"}
    >
      <CardContent>
          <Box
            sx={{
              display: "flex",
              gap: 1,
              flexWrap: "wrap",
              alignItems: "center",
            }}
            data-test-id="LinkField-row"
          >
            <Typography
              variant="subtitle1"
              component="span"
              sx={{ fontWeight: 700 }}
            >
              Link
            </Typography>
            {props.name && (
              <Typography
                variant="subtitle1"
                component="span"
                sx={{ fontWeight: 700 }}
              >
                {props.name}
              </Typography>
            )}
            <Chip
              size="small"
              label={props.link.relationType}
              data-test-id="LinkField-relation"
            />
            <Chip
              size="small"
              icon={
                iconData ? (
                  <RecordTypeIcon record={iconData} aria-hidden />
                ) : undefined
              }
              label={props.link.targetGlobalId}
              data-test-id="LinkField-target"
            />
            <IconButton
              size="small"
              aria-label={`Show info for ${props.link.targetGlobalId}`}
              onClick={() => setInfoOpen(true)}
            >
              <InfoOutlinedIcon fontSize="small" />
            </IconButton>
            {props.editable && supportsVersionPin(props.link.targetGlobalId) && (
              // a disabled affordance only: like the other link properties,
              // the pin is changed in the link editor (Edit) and committed
              // with Update, not directly from the view card. The tooltip
              // wrapper says so, since a disabled button cannot.
              <Tooltip title="Edit the link to change the pinned version">
                <span>
                  <IconButton
                    size="small"
                    aria-label={`Pin version for ${props.link.targetGlobalId}`}
                    disabled
                  >
                    <HistoryIcon fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            <Chip
              size="small"
              variant="outlined"
              label={versionLabel}
              data-test-id="LinkField-version"
            />
            {targetDeleted && (
              <Chip
                size="small"
                color="warning"
                label="Target deleted"
                data-test-id="LinkField-targetDeleted"
              />
            )}
            {noAccess && (
              // no "no longer" in the tooltip: the pill also shows for
              // viewers who never had access (ADR-0002)
              <Tooltip title="You do not have permission to view this item">
                <Chip
                  size="small"
                  color="warning"
                  label="No access"
                  data-test-id="LinkField-noAccess"
                />
              </Tooltip>
            )}
            {!openBlocked && (
              <Button
                size="small"
                startIcon={<OpenInNewIcon />}
                onClick={handleOpen}
                aria-label="Open"
              >
                Open
              </Button>
            )}
            {props.editable && (
              <Button
                size="small"
                startIcon={<EditIcon />}
                onClick={props.onEdit}
                aria-label="Edit link"
              >
                Edit
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
          onClose={() => setInfoOpen(false)}
        />
      )}
    </Card>
  );
}
