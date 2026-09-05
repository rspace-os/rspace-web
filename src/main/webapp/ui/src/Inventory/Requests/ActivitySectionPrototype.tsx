/** @prototype Storybook-only UI exploration; not production-ready. */
import AccountTreeIcon from "@mui/icons-material/AccountTree";
import CancelIcon from "@mui/icons-material/Cancel";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import MoveToInboxIcon from "@mui/icons-material/MoveToInbox";
import OutboxIcon from "@mui/icons-material/Outbox";
import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import TaskAltIcon from "@mui/icons-material/TaskAlt";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import StepperPanel from "../components/Stepper/StepperPanel";
import type { ActivityEntry } from "./types";

const ICONS: Record<ActivityEntry["kind"], React.ReactNode> = {
  raised: <MoveToInboxIcon fontSize="small" />,
  approved: <TaskAltIcon fontSize="small" />,
  produced: <AccountTreeIcon fontSize="small" />,
  prepared: <Inventory2Icon fontSize="small" />,
  fulfilled: <SwapHorizIcon fontSize="small" />,
  rejected: <CancelIcon fontSize="small" />,
  requestable: <OutboxIcon fontSize="small" />,
};

/**
 * Request-related audit entries for a sample, newest first. In production this
 * reads from the audit trail the request workflow writes to.
 */
export default function ActivitySectionPrototype({
  entries,
  compact = false,
  title,
}: {
  entries: ReadonlyArray<ActivityEntry>;
  compact?: boolean;
  title?: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <StepperPanel
      icon="sample"
      title={title ?? t("requests.activity.title")}
      sectionName="activity"
      recordType="sample"
    >
      <List
        disablePadding
        dense
        aria-label={title ?? t("requests.activity.title")}
        sx={{ containerType: "inline-size" }}
      >
        {entries.map((entry, index) => (
          <ListItem
            key={entry.id}
            divider={index < entries.length - 1}
            disableGutters
            sx={{ display: "grid", gridTemplateColumns: "24px minmax(0, 1fr) auto", columnGap: 1, alignItems: "start" }}
          >
            <ListItemIcon sx={{ minWidth: 0, mt: 0.5, color: "text.secondary" }}>{ICONS[entry.kind]}</ListItemIcon>
            <ListItemText
              primary={
                compact && entry.text.length > 240 ? (
                  <details>
                    <summary>{`${entry.text.slice(0, 180)}…`}</summary>
                    {entry.text}
                  </details>
                ) : (
                  entry.text
                )
              }
              sx={{ minWidth: 0 }}
              slotProps={{ primary: { component: "div", variant: "body2", sx: { overflowWrap: "anywhere" } } }}
            />
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ mt: 0.5, "@container (max-width: 420px)": { gridColumn: "2 / -1", mt: 0 } }}
            >
              {entry.when}
            </Typography>
          </ListItem>
        ))}
      </List>
    </StepperPanel>
  );
}
