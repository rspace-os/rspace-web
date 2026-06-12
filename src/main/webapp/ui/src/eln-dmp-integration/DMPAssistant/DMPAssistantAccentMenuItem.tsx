import CardMedia from "@mui/material/CardMedia";
import React from "react";
import { LOGO_COLOR } from "../../assets/branding/dmpassistant";
import DMPAssistantIcon from "../../assets/branding/dmpassistant/logo.svg";
import AccentMenuItem from "../../components/AccentMenuItem";
import EventBoundary from "../../components/EventBoundary";
import DMPDialog from "./DMPDialog";

type DMPAssistantAccentMenuItemArgs = {
  onDialogClose: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMP Assistant.
 */
export default function DMPAssistantAccentMenuItem({ onDialogClose }: DMPAssistantAccentMenuItemArgs): React.ReactNode {
  const [showDialog, setShowDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="DMP Assistant"
        avatar={<CardMedia image={DMPAssistantIcon} />}
        backgroundColor={LOGO_COLOR}
        foregroundColor={{ ...LOGO_COLOR, lightness: 30 }}
        onClick={() => {
          setShowDialog(true);
        }}
        aria-haspopup="dialog"
        compact
      />
      <EventBoundary>
        <DMPDialog
          open={showDialog}
          setOpen={(b) => {
            setShowDialog(b);
            onDialogClose();
          }}
        />
      </EventBoundary>
    </>
  );
}
