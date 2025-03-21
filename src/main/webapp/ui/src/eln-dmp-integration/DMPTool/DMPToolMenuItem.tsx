/*
 * This is a menu item that is included in the Import menu of the Gallery's
 * Toolbar. When the DMPTool integration is not enabled this menu item is
 * hidden, and it is shown disabled when the integration is enabled but the
 * user is not authenticated. When enabled and tapped, this menu item renders
 * a dialog that allows the user to browse and pick a DMP to import into the
 * Gallery.
 */

import React, { useState, useEffect } from "react";
import DMPDialog from "./DMPDialog";
import axios from "@/common/axios";
import MenuItem from "@mui/material/MenuItem";
import ListItemText from "@mui/material/ListItemText";
import {
  fetchIntegrationInfo,
  type IntegrationInfo,
} from "../../common/integrationHelpers";
import { mapNullable } from "../../util/Util";
import Alerts from "../../components/Alerts/Alerts";
import ErrorBoundary from "../../components/ErrorBoundary";

type DMPToolMenuItemArgs = {
  /*
   * The rendering of the dialog when the menu item is clicked is managed by
   * this component. This callback should simply be used for any additional
   * behavior that should be triggered, such the closing of the menu.
   */
  onClick: () => void;
};

export default function DMPToolMenuItem({
  onClick,
}: DMPToolMenuItemArgs): React.ReactNode {
  const [dmpToolIntegrationInfo, setDmpToolIntegrationInfo] =
    useState<IntegrationInfo | null>(null);
  const [DMPHost, setDMPHost] = useState<string | null>();
  const [showDMPDialog, setShowDMPDialog] = useState(false);

  useEffect(() => {
    axios
      .get<string>("/apps/dmptool/baseUrlHost")
      .then((r) => setDMPHost(r.data))
      .catch((e) => console.error("Cannot establish DMP host", e));
  }, []);

  useEffect(() => {
    fetchIntegrationInfo("DMPTOOL")
      .then((r) => setDmpToolIntegrationInfo(r))
      .catch((e) =>
        console.error("Cannot establish if DMPTool app is enabled", e)
      );
  }, []);

  if (!dmpToolIntegrationInfo || !dmpToolIntegrationInfo.enabled) return null;
  return (
    <ErrorBoundary topOfViewport>
      <MenuItem
        id="fromDMP"
        data-test-id="gallery-import-dmp"
        onClick={() => {
          onClick();
          setShowDMPDialog(true);
        }}
        disabled={!dmpToolIntegrationInfo.oauthConnected}
      >
        <ListItemText
          primary={`DMP${
            mapNullable((host) => ` from ${host}`, DMPHost) ?? ""
          }`}
          secondary={
            dmpToolIntegrationInfo.oauthConnected
              ? ""
              : "You have not yet authenticated on the apps page."
          }
        />
      </MenuItem>
      <Alerts>
        <DMPDialog open={showDMPDialog} setOpen={setShowDMPDialog} />
      </Alerts>
    </ErrorBoundary>
  );
}
