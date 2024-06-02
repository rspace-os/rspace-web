// @flow

import React, { useState, useEffect, type Node } from "react";
import DMPDialog from "./DMPDialog";
import MenuItem from "@mui/material/MenuItem";
import { fetchIntegrationInfo } from "../../common/integrationHelpers";

type ArgosMenuItemArgs = {|
  onClick: (boolean) => void,
|};

export default function ArgosMenuItem({ onClick }: ArgosMenuItemArgs): Node {
  const [DMPAppEnabled, setDMPAppEnabled] = useState(false);
  const [showDMPDialog, setShowDMPDialog] = useState(false);

  useEffect(() => {
    fetchIntegrationInfo("ARGOS")
      .then((r) => setDMPAppEnabled(r.enabled))
      .catch((e) =>
        console.error("Cannot establish if Argos app is enabled", e)
      );
  }, []);

  return DMPAppEnabled ? (
    <>
      <MenuItem
        onClick={() => {
          onClick(false);
          setShowDMPDialog(true);
        }}
      >
        DMP from Argos
      </MenuItem>
      <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
    </>
  ) : null;
}
