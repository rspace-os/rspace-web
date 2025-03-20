import React from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import DMPonlineIcon from "../../assets/branding/dmponline/logo.svg";
import { LOGO_COLOR } from "../../assets/branding/dmponline";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type DMPonlineAccentMenuItemArgs = {
  onDialogClose: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from DMPonline.
 */
export default function DMPonlineAccentMenuItem({
  onDialogClose,
}: DMPonlineAccentMenuItemArgs): React.ReactNode {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="DMPonline"
        avatar={<CardMedia image={DMPonlineIcon} />}
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
