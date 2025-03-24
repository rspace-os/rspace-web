//@flow

import React, { type ComponentType, forwardRef, useState } from "react";
import useStores from "../../../stores/use-stores";
import { mkAlert } from "../../../stores/contexts/Alert";
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
    const { uiStore, searchStore } = useStores();

    const [previewImages, setPreviewImages] = useState<Array<string>>([]);
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

    const barcodes = selectedResults.map((r) => r.barcodes[0]);

    // check for b as barcodes[0] is undefined in public view
    const imgUrlsAvailable = barcodes.every((b) => b && b.imageUrl);

    const getImageUrls = async (): Promise<Array<string> | void> => {
      if (imgUrlsAvailable) {
        const images = await Promise.all(barcodes.map((b) => b.fetchImage()));
        const imageUrls = images.map((img) => URL.createObjectURL(img));
        return imageUrls;
      }
    };

    const handleOpen = () => {
      try {
        void getImageUrls().then((imageUrls) => {
          if (imageUrls && imageUrls.length > 0) {
            setPreviewImages(imageUrls);
          }
        });
      } catch (e) {
        uiStore.addAlert(
          mkAlert({
            title: "Unable to retrieve barcode images.",
            message: e.message || "",
            variant: "error",
            isInfinite: true,
          })
        );
      } finally {
        setShowPrintDialog(true);
      }
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
                imageLinks={previewImages}
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
