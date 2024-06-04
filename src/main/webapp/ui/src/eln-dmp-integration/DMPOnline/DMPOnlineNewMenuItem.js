// @flow

import React, { type Node } from "react";
import DMPDialog from "./DMPDialog";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import { fetchIntegrationInfo } from "../../common/integrationHelpers";
import DMPonlineIcon from "../../eln/apps/icons/dmponline.svg";
import { COLOR } from "../../eln/apps/integrations/DMPonline";
import CardMedia from "@mui/material/CardMedia";

export default function DMPonlineNewMenuItem(): Node {
  const [DMPAppEnabled, setDMPAppEnabled] = React.useState(false);
  const [showDMPDialog, setShowDMPDialog] = React.useState(false);

  React.useEffect(() => {
    fetchIntegrationInfo("DMPONLINE")
      .then((r) => setDMPAppEnabled(r.enabled))
      .catch((e) =>
        console.error("Cannot establish if DmpOnline app is enabled", e)
      );
  }, []);

  return DMPAppEnabled ? (
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
      />
      <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
    </>
  ) : null;
}
