import React from "react";
import DSWImportDialog from "./DSWImportDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import DSWIcon from "../../assets/branding/dsw/logo.svg";
import { LOGO_COLOR } from "../../assets/branding/dsw";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";
import AnalyticsContext from "../../stores/contexts/Analytics";

export type DswConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
}

type DSWAccentMenuItemArgs = {
  onDialogClose: () => void;
  connection: DswConfig;
};

/**
 * The menu item for the create menu for importing DMPs from DSW/FAIR Wizard.
 */
export default function DSWAccentMenuItem({
  onDialogClose,
    connection
}: DSWAccentMenuItemArgs): React.ReactNode {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [showDSWDialog, setShowDSWDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title={connection.DSW_ALIAS}
        subheader="DSW / FAIR Wizard"
        avatar={<CardMedia image={DSWIcon} />}
        backgroundColor={LOGO_COLOR}
        foregroundColor={{ ...LOGO_COLOR, lightness: 30 }}
        onClick={() => {
          trackEvent("user:open:dsw_import:gallery");
          setShowDSWDialog(true);
        }}
        aria-haspopup="dialog"
        compact
      />
      <EventBoundary>
        <DSWImportDialog
          open={showDSWDialog}
          setOpen={(b) => {
            setShowDSWDialog(b);
            onDialogClose();
          }}
          connection={connection}
        />
      </EventBoundary>
    </>
  );
}
