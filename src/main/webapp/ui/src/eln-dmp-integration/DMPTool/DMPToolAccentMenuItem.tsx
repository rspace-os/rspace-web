import React from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import DMPToolIcon from "../../assets/branding/dmptool/logo.svg";
import { LOGO_COLOR } from "../../assets/branding/dmptool";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type DMPToolAccentMenuItemArgs = {
  onDialogClose: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMPTool.
 */
export default function DMPToolAccentMenuItem({
  onDialogClose,
}: DMPToolAccentMenuItemArgs): React.ReactNode {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="DMPTool"
        avatar={<CardMedia image={DMPToolIcon} />}
        backgroundColor={LOGO_COLOR}
        foregroundColor={{ ...LOGO_COLOR, lightness: 30 }}
        onClick={() => {
          setShowDMPDialog(true);
        }}
        aria-haspopup="dialog"
        compact
      />
      <EventBoundary>
        <DMPDialog
          open={showDMPDialog}
          setOpen={(b) => {
            setShowDMPDialog(b);
            onDialogClose();
          }}
        />
      </EventBoundary>
    </>
  );
}
