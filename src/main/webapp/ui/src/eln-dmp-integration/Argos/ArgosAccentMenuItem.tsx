import React from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import ArgosIcon from "../../assets/branding/argos/logo.svg";
import { LOGO_COLOR } from "../../assets/branding/argos";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type ArgosAccentMenuItemArgs = {
  onDialogClose: () => void;
};

/**
 * The menu item for the create menu for importing DMPs from Argos.
 */
export default function ArgosMenuItem({
  onDialogClose,
}: ArgosAccentMenuItemArgs): React.ReactNode {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="Argos"
        avatar={<CardMedia image={ArgosIcon} />}
        backgroundColor={LOGO_COLOR}
        foregroundColor={{ ...LOGO_COLOR, lightness: 28 }}
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
            if (!b) onDialogClose();
          }}
        />
      </EventBoundary>
    </>
  );
}
