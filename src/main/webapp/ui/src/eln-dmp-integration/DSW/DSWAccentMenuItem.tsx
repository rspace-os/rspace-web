import React from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import DSWIcon from "../../assets/branding/dsw/logo.svg";
import { LOGO_COLOR } from "../../assets/branding/dsw";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

export type DswConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
}

type DSWAccentMenuItemArgs = {
  onDialogClose: () => void;
  connection: DswConfig;
  showAlias: boolean;
};

/**
 * The menu item for the create menu for importing DMPs from DMPonline.
 */
export default function DSWAccentMenuItem({
  onDialogClose,
    connection,
    showAlias
}: DSWAccentMenuItemArgs): React.ReactNode {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);
  console.log("DAMI connection: ", connection);

  return (
    <>
      <AccentMenuItem
        title="DSW / FAIR Wizard"
        subheader={showAlias && connection.DSW_ALIAS}
        avatar={<CardMedia image={DSWIcon} />}
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
          connection={connection}
        />
      </EventBoundary>
    </>
  );
}
