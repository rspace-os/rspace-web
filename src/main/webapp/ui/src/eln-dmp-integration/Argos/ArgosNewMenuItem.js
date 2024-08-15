//@flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import ArgosIcon from "../../eln/apps/icons/Argos.svg";
import { COLOR } from "../../eln/apps/integrations/Argos";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type ArgosNewMenuItemArgs = {|
  onDialogClose: () => void,
|};

export default function ArgosNewMenuItem({
  onDialogClose,
}: ArgosNewMenuItemArgs): Node {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <NewMenuItem
        title="Argos"
        avatar={<CardMedia image={ArgosIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 28 }}
        subheader="Import from argos.eu"
        onClick={() => {
          setShowDMPDialog(true);
        }}
        aria-haspopup="dialog"
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
