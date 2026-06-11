import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Search from "../../../../stores/models/Search";
import AlwaysNewFactory from "../../../../stores/models/Factory/AlwaysNewFactory";
import InventoryPicker from "../../Picker/Picker";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import AlwaysNewWindowNavigationContext from "../../../../components/AlwaysNewWindowNavigationContext";

export interface LinkTargetBrowserProps {
  open: boolean;
  onPick: (target: { globalId: string; name: string }) => void;
  onCancel: () => void;
}

function LinkTargetBrowser(props: LinkTargetBrowserProps): React.ReactElement {
  const [search] = useState(
    new Search({
      factory: new AlwaysNewFactory(),
      fetcherParams: {
        resultType: "ALL",
        pageSize: 10,
        orderBy: "name",
        order: "asc",
      },
      uiConfig: {
        allowedSearchModules: new Set([
          "TYPE",
          "OWNER",
          "SAVEDSEARCHES",
          "TAG",
        ]),
        allowedTypeFilters: new Set([
          "ALL",
          "SAMPLE",
          "SUBSAMPLE",
          "CONTAINER",
          "TEMPLATE",
        ]),
        selectionMode: "SINGLE",
        // mirror Browse ELN: clicking a result only selects it, and the
        // picker's Choose button (enabled once a row is selected) confirms
        instantConfirm: false,
      },
    }),
  );

  useEffect(() => {
    if (props.open) void search.fetcher.performInitialSearch(null);
  }, [props.open, search]);

  const handleAddition = (records: Array<InventoryRecord>) => {
    const [first] = records;
    if (!first || !first.globalId) {
      // defensive: Choose is disabled while nothing is selected, but an empty
      // confirmation must close the dialog rather than commit an empty pick
      props.onCancel();
      return;
    }
    props.onPick({
      globalId: first.globalId,
      name: first.name ?? "",
    });
  };

  return (
    <Dialog
      open={props.open}
      onClose={props.onCancel}
      fullWidth
      maxWidth="md"
      aria-label="Browse Inventory for link target"
    >
      <DialogTitle>Browse Inventory</DialogTitle>
      <DialogContent sx={{ height: "60vh", p: 1 }}>
        <AlwaysNewWindowNavigationContext>
          <InventoryPicker
            search={search}
            paddingless
            onAddition={handleAddition}
            onCancel={props.onCancel}
            showActions
            // clear the picker's active result after each pick/cancel so reopening
            // the dialog starts fresh rather than with the prior selection
            resetActiveResultOnClose
          />
        </AlwaysNewWindowNavigationContext>
      </DialogContent>
    </Dialog>
  );
}

export default observer(LinkTargetBrowser);
