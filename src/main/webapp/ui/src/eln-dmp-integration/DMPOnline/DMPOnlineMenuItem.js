// @flow

import React, { useState, useEffect, type Node } from "react";
import DMPDialog from "./DMPDialog";
import MenuItem from "@mui/material/MenuItem";
import { fetchIntegrationInfo } from "../../common/integrationHelpers";

type DmpOnlineMenuItemArgs = {|
  onClick: (boolean) => void,
|};

export default function DmpOnlineMenuItem({
  onClick,
}: DmpOnlineMenuItemArgs): Node {
  const [DMPAppEnabled, setDMPAppEnabled] = useState(false);
  const [showDMPDialog, setShowDMPDialog] = useState(false);

  useEffect(() => {
    fetchIntegrationInfo("DMPONLINE")
      .then((r) => setDMPAppEnabled(r.enabled))
      .catch((e) =>
        console.error("Cannot establish if DmpOnline app is enabled", e)
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
        DMP from DMPonline
      </MenuItem>
      <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
    </>
  ) : null;
}
