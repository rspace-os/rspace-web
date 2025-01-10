//@flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import ArgosIcon from "../../eln/apps/icons/Argos.svg";
import { COLOR } from "../../eln/apps/integrations/Argos";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type ArgosAccentMenuItemArgs = {|
  onDialogClose: () => void,
|};

export default function ArgosMenuItem({
  onDialogClose,
}: ArgosAccentMenuItemArgs): Node {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="Argos"
        avatar={<CardMedia image={ArgosIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 28 }}
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
