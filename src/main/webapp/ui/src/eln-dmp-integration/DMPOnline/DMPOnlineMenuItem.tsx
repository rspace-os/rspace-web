import React, { useState, useEffect } from "react";
import DMPDialog from "./DMPDialog";
import MenuItem from "@mui/material/MenuItem";
import { fetchIntegrationInfo } from "../../common/integrationHelpers";
import Alerts from "../../components/Alerts/Alerts";
import ErrorBoundary from "../../components/ErrorBoundary";

type DmpOnlineMenuItemArgs = {
  onClick: (completed: boolean) => void;
};

export default function DmpOnlineMenuItem({
  onClick,
}: DmpOnlineMenuItemArgs): React.ReactNode {
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
    <ErrorBoundary topOfViewport>
      <MenuItem
        onClick={() => {
          onClick(false);
          setShowDMPDialog(true);
        }}
      >
        DMP from DMPonline
      </MenuItem>
      <Alerts>
        <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
      </Alerts>
    </ErrorBoundary>
  ) : null;
}
