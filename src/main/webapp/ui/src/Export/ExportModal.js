"use strict";

import ExportDialog from "./ExportDialog";
import { createRoot } from "react-dom/client";
import React from "react";
import Alerts from "../components/Alerts/Alerts";
import Analytics from "../components/Analytics";

/*
 * This module initialises the ExportDialog react component within the
 * context of the ELN. ExportModal is a standalone JavaScript asset
 * produced by Webpack that executes the following code: initialising the
 * export dialog in a closed state and exposing a method for any code on
 * any page that includes the JS asset to call to open the export dialog
 * with a particular selection.
 */

const domContainer = document.getElementById("exportModal");
const root = createRoot(domContainer);
root.render(
  <Alerts>
    <ExportDialog
      exportSelection={{
        type: "selection",
        exportTypes: [],
        exportNames: [],
        exportIds: [],
      }}
      open={false}
      allowFileStores={RS.netFileStoresExportEnabled}
    />
  </Alerts>,
);

RS.exportModal = {
  /*
   * JS code across the rest of the ELN can call this method -- assuming that
   * the ExportModal JS asset has been loaded -- to trigger the opening of the
   * ExportDialog with the passed selection. `exportSelection` has the
   * following type:
   *
   * {|
   *   type: "user",
   *   username: string
   * |} | {|
   *   type: "group",
   *   groupId: string,
   *   groupName: string
   * |} | {|
   *   type: "selection",
   *   exportTypes: Array<string>,
   *   exportNames: Array<string>,
   *   exportIds: Array<string>
   * |}
   */
  openWithExportSelection: (exportSelection) => {
    const adjustedSelection = {
      ...exportSelection,
      exportIds: exportSelection.exportIds || [],
    };
    root.render(
      <Alerts>
        <Analytics>
          <ExportDialog
            exportSelection={adjustedSelection}
            open={true}
            allowFileStores={RS.netFileStoresExportEnabled}
          />
        </Analytics>
      </Alerts>,
    );
  },
};
