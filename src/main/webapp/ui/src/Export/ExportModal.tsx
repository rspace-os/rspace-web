import ExportDialog from "./ExportDialog";
import { createRoot } from "react-dom/client";
import React from "react";
import Alerts from "../components/Alerts/Alerts";
import Analytics from "../components/Analytics";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ExportSelection } from "@/Export/common";

/*
 * This module initialises the ExportDialog react component within the
 * context of the ELN. ExportModal is a standalone JavaScript asset
 * produced by Webpack that executes the following code: initialising the
 * export dialog in a closed state and exposing a method for any code on
 * any page that includes the JS asset to call to open the export dialog
 * with a particular selection.
 */

const queryClient = new QueryClient();

const domContainer = document.getElementById("exportModal");
if (!domContainer) {
  throw new Error("Could not find export modal container");
}

const root = createRoot(domContainer);
root.render(
  <QueryClientProvider client={queryClient}>
    <Alerts>
      <ExportDialog
        exportSelection={{
          type: "selection",
          exportTypes: [],
          exportNames: [],
          exportIds: [],
        }}
        open={false}
        // @ts-expect-error RS is legacy
        allowFileStores={RS.netFileStoresExportEnabled}
      />
    </Alerts>
  </QueryClientProvider>,
);

// @ts-expect-error RS is legacy
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
  openWithExportSelection: (exportSelection: ExportSelection) => {
    const adjustedSelection = {
      ...exportSelection,
      exportIds: exportSelection.exportIds || [],
    };
    root.render(
      <QueryClientProvider client={queryClient}>
        <Alerts>
          <Analytics>
              {/*
               * TODO 07022026: As we're introducing Suspense into ExportDialog itself, we need to design a Suspense
               * boundary by moving the Dialog components up a level.
                */}
            <ExportDialog
              // @ts-expect-error RS is legacy
              exportSelection={adjustedSelection}
              open={true}
              // @ts-expect-error RS is legacy
              allowFileStores={RS.netFileStoresExportEnabled}
            />
          </Analytics>
        </Alerts>
      </QueryClientProvider>,
    );
  },
};
