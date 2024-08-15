// @flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import DMPonlineIcon from "../../eln/apps/icons/dmponline.svg";
import { COLOR } from "../../eln/apps/integrations/DMPonline";
import CardMedia from "@mui/material/CardMedia";
import EventBoundary from "../../components/EventBoundary";

type DMPonlineNewMenuItemArgs = {|
  onDialogClose: () => void,
|};

export default function DMPonlineNewMenuItem({
  onDialogClose,
}: DMPonlineNewMenuItemArgs): Node {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <NewMenuItem
        title="DMPonline"
        avatar={<CardMedia image={DMPonlineIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 30 }}
        subheader="Import from dmponline.dcc.ac.uk"
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
            onDialogClose();
          }}
        />
      </EventBoundary>
    </>
  );
}
