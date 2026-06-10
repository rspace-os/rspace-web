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
      },
    }),
  );

  useEffect(() => {
    if (props.open) void search.fetcher.performInitialSearch(null);
  }, [props.open, search]);

  const handleAddition = (records: Array<InventoryRecord>) => {
    const [first] = records;
    if (!first || !first.globalId) {
      // the picker's Cancel button reports through onAddition([]) when the
      // search uses instantConfirm (the default), so an empty pick is a cancel
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
            // clear the picker's active result after each pick/cancel so reopening the
            // dialog starts fresh; otherwise the reused Search model re-emits the prior
            // selection (instantConfirm + SINGLE auto-confirm), re-populating the target
            // and immediately closing the dialog
            resetActiveResultOnClose
          />
        </AlwaysNewWindowNavigationContext>
      </DialogContent>
    </Dialog>
  );
}

export default observer(LinkTargetBrowser);
