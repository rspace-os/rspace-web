//@flow

import React, { type ComponentType, forwardRef, useState } from "react";
import useStores from "../../../stores/use-stores";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import PrintIcon from "@mui/icons-material/Print";
import { Observer } from "mobx-react-lite";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import PrintDialog from "../Print/PrintDialog";

type PrintBarcodeActionArgs = {|
  as: ContextMenuRenderOptions,
  closeMenu: () => void,
  disabled: string,
  selectedResults: Array<InventoryRecord>,
|};

const PrintBarcodeAction: ComponentType<PrintBarcodeActionArgs> = forwardRef(
  (
    { as, closeMenu, disabled, selectedResults }: PrintBarcodeActionArgs,
    ref
  ) => {
    const { searchStore, trackingStore } = useStores();

    const [showPrintDialog, setShowPrintDialog] = useState(false);

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [
        () => selectedResults.some((r) => r.recordType === "sampleTemplate"),
        "Templates do not have barcodes.",
      ],
      [
        () => selectedResults.some((r) => !r.canRead),
        `You do not have permission to print ${
          selectedResults.length > 1 ? "these items" : "this item"
        }.`,
      ],
      [
        () => searchStore.activeResultIsBeingEdited,
        "Cannot print barcodes whilst a record is being edited.",
      ],
      [() => true, ""],
    ])();

    const handleOpen = () => {
      setShowPrintDialog(true);
      trackingStore.trackEvent("user:open:printDialog:inventoryContextMenu");
    };

    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={handleOpen}
            icon={<PrintIcon />}
            label="Print Barcode"
            disabledHelp={disabledHelp}
            as={as}
            ref={ref}
          >
            {showPrintDialog && (
              <PrintDialog
                showPrintDialog={showPrintDialog}
                onClose={() => setShowPrintDialog(false)}
                printType="contextMenu"
                itemsToPrint={selectedResults}
                closeMenu={closeMenu}
              />
            )}
          </ContextMenuAction>
        )}
      </Observer>
    );
  }
);

PrintBarcodeAction.displayName = "PrintBarcodeAction";
export default PrintBarcodeAction;
