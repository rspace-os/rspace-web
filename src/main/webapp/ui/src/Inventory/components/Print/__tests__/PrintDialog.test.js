/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
/* eslint-disable no-undefined */

import "@testing-library/jest-dom";
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import PrintDialog, { type PrintType } from "../PrintDialog";
import { generatedBarcode, persistedBarcode1 } from "./mocking";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import {
  makeMockRootStore,
  type MockStores,
} from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { type StoreContainer } from "../../../../stores/stores/RootStore";

const mockRootStore = (mockedStores: ?MockStores): StoreContainer => {
  return makeMockRootStore({
    ...mockedStores,
    uiStore: {
      isSingleColumnLayout: false,
    },
  });
};

const mockContainer = makeMockContainer();
mockContainer.barcodes = [persistedBarcode1];

if (mockContainer.globalId) {
  const generatedBarcode1 = generatedBarcode(
    mockContainer.recordType,
    mockContainer.globalId
  );
  mockContainer.barcodes.push(generatedBarcode1);
}

describe("Print Tests", () => {
  let openDialog = true; // if false, then dialog is null

  const modalRoot = document.createElement("div");
  modalRoot.setAttribute("id", "modal-root");

  const Dialog = ({ printType }: { printType: PrintType }) => {
    return (
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={mockRootStore()}>
          <PrintDialog
            showPrintDialog={openDialog}
            imageLinks={[]}
            printType={printType}
            itemsToPrint={[
              mockContainer
            ]}
            onClose={() => {}}
          />
        </storesContext.Provider>
      </ThemeProvider>
    );
  };

  describe("PrintDialog with items to print (barcodes)", () => {
    it("renders, has radio options for printerType, printMode, printSize (plus help text)", () => {
      render(<Dialog printType="contextMenu" />);

      expect(screen.getAllByRole("radio")).toHaveLength(10);

      const standardOption = screen.getByLabelText("Standard Printer");
      expect(standardOption).toBeInTheDocument();
      expect(standardOption).toBeChecked();

      const labelOption = screen.getByLabelText("Label Printer");
      expect(labelOption).toBeInTheDocument();
      expect(labelOption).not.toBeChecked();

      /* assert help text for default mode option, and preview header */
      const defaultModeHint = "Barcode and record details.";
      expect(screen.getByText(defaultModeHint)).toBeInTheDocument();
      const previewHeader = "Preview: Barcode Label Layout";
      expect(screen.getByText(previewHeader)).toBeInTheDocument();
    });
  });

  describe("PrintDialog with items to print (barcodes)", () => {
    it("renders, content responds to clicked options", () => {
      render(<Dialog printType="barcodeLabel" />);

      const globalId = mockContainer.globalId;
      const location = "Location:";
      if (!globalId) throw new Error("Missing globalId");

      // on default "full" option elements are rendered, on "basic" they are not
      expect(screen.getAllByText(globalId)[0]).toBeInTheDocument();

      const basicOption = screen.getByLabelText("Basic");
      fireEvent.click(basicOption);
      expect(screen.queryByText(location)).not.toBeInTheDocument();
    });
  });
});
