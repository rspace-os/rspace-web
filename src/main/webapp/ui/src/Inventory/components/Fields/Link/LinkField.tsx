import React, { useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import EditIcon from "@mui/icons-material/Edit";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import HistoryIcon from "@mui/icons-material/History";
import { type ExtraInventoryLink } from "../../../../stores/definitions/ExtraField";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import { iconForInventoryGlobalId } from "./iconForGlobalId";
import InventoryInfoDialog from "./InventoryInfoDialog";
import VersionLockDialog from "./VersionLockDialog";

export interface LinkFieldProps {
  /** Field name (the user-supplied label of the ExtraLinkField row) */
  name: string;
  link: ExtraInventoryLink;
  /** When true, the target inventory record has been soft-deleted on the server. */
  targetDeleted: boolean;
  /** When false, the edit affordance is hidden. */
  editable: boolean;
  onPeek: () => void;
  onOpen: () => void;
  onEdit: () => void;
  /** Optional. When provided and editable=true, the clock icon is enabled. */
  onVersionPinChange?: (versionPin: number | null) => void;
}

export default function LinkField(props: LinkFieldProps): React.ReactElement {
  const versionLabel =
    props.link.versionPin != null ? `Pinned to v${props.link.versionPin}` : "Latest";
  const iconData = iconForInventoryGlobalId(props.link.targetGlobalId);
  const [infoOpen, setInfoOpen] = useState(false);
  const [versionDialogOpen, setVersionDialogOpen] = useState(false);
  return (
    <Card variant="outlined" aria-label={`Link field ${props.name}`}>
      <CardActionArea onClick={props.onPeek} disabled={false}>
        <CardContent>
          <Typography variant="subtitle1" component="div">
            {props.name}
          </Typography>
          <Box sx={{ display: "flex", gap: 1, mt: 1, flexWrap: "wrap" }}>
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
              onClick={(e) => {
                e.stopPropagation();
                setInfoOpen(true);
              }}
            >
              <InfoOutlinedIcon fontSize="small" />
            </IconButton>
            {props.editable && (
              <IconButton
                size="small"
                aria-label={`Pin version for ${props.link.targetGlobalId}`}
                onClick={(e) => {
                  e.stopPropagation();
                  setVersionDialogOpen(true);
                }}
              >
                <HistoryIcon fontSize="small" />
              </IconButton>
            )}
            <Chip
              size="small"
              variant="outlined"
              label={versionLabel}
              data-test-id="LinkField-version"
            />
            {props.targetDeleted && (
              <Chip
                size="small"
                color="warning"
                label="Target deleted"
                data-test-id="LinkField-targetDeleted"
              />
            )}
          </Box>
        </CardContent>
      </CardActionArea>
      <CardActions>
        {!props.targetDeleted && (
          <Tooltip
            title={
              props.link.versionPin != null
                ? "Version-specific view is not yet supported in Inventory; opening would show the latest version. Tracked in RSDEV-1131 follow-up."
                : ""
            }
          >
            <span>
              <Button
                size="small"
                startIcon={<OpenInNewIcon />}
                onClick={props.onOpen}
                disabled={props.link.versionPin != null}
                aria-label="Open in Inventory"
              >
                Open in Inventory
              </Button>
            </span>
          </Tooltip>
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
      </CardActions>
      <InventoryInfoDialog
        open={infoOpen}
        globalId={props.link.targetGlobalId}
        onClose={() => setInfoOpen(false)}
      />
      <VersionLockDialog
        open={versionDialogOpen}
        globalId={props.link.targetGlobalId}
        currentVersionPin={props.link.versionPin}
        onConfirm={(versionPin) => {
          setVersionDialogOpen(false);
          props.onVersionPinChange?.(versionPin);
        }}
        onCancel={() => setVersionDialogOpen(false)}
      />
    </Card>
  );
}
