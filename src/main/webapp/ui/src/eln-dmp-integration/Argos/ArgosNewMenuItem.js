//@flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import ArgosIcon from "../../eln/apps/icons/Argos.svg";
import { COLOR } from "../../eln/apps/integrations/Argos";
import CardMedia from "@mui/material/CardMedia";

export default function ArgosNewMenuItem(): Node {
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  return (
    <>
      <NewMenuItem
        title="ARGOS"
        avatar={<CardMedia image={ArgosIcon} />}
        backgroundColor={COLOR}
        foregroundColor={{ ...COLOR, lightness: 28 }}
        subheader="Import from argos.eu"
        onClick={() => {
          setShowDMPDialog(true);
        }}
      />
      <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
    </>
  );
}
