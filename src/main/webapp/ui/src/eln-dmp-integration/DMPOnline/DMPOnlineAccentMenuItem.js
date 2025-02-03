// @flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import AccentMenuItem from "../../components/AccentMenuItem";
import DMPonlineIcon from "../../eln/apps/icons/dmponline.svg";
import { COLOR } from "../../eln/apps/integrations/DMPonline";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type DMPonlineAccentMenuItemArgs = {|
  onDialogClose: () => void,
|};

export default function DMPonlineAccentMenuItem({
  onDialogClose,
}: DMPonlineAccentMenuItemArgs): Node {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <AccentMenuItem
        title="DMPonline"
        avatar={<CardMedia image={DMPonlineIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 30 }}
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
