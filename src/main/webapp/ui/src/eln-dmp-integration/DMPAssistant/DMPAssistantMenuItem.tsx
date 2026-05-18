import React, { useState, useEffect } from "react";
import DMPDialog from "./DMPDialog";
import MenuItem from "@mui/material/MenuItem";
import { fetchIntegrationInfo } from "../../hooks/api/integrationHelpers";
import Alerts from "../../components/Alerts/Alerts";
import ErrorBoundary from "../../components/ErrorBoundary";

type DMPAssistantMenuItemArgs = {
  onClick: (completed: boolean) => void;
};

export default function DMPAssistantMenuItem({
  onClick,
}: DMPAssistantMenuItemArgs): React.ReactNode {
  const [enabled, setEnabled] = useState(false);
  const [showDialog, setShowDialog] = useState(false);

  useEffect(() => {
    fetchIntegrationInfo("DMPASSISTANT")
      .then((r) => setEnabled(r.enabled))
      .catch((e) =>
        console.error("Cannot establish if DMP Assistant app is enabled", e),
      );
  }, []);

  return enabled ? (
    <ErrorBoundary topOfViewport>
      <MenuItem
        onClick={() => {
          onClick(false);
          setShowDialog(true);
        }}
      >
        DMP from DMP Assistant
      </MenuItem>
      <Alerts>
        <DMPDialog open={showDialog} setOpen={setShowDialog} />
      </Alerts>
    </ErrorBoundary>
  ) : null;
}
