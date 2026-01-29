/*
 */
 

import { describe, it, expect, vi, beforeAll } from "vitest";
import "@testing-library/jest-dom/vitest";
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import materialTheme from "../../../../theme";
import { ThemeProvider } from "@mui/material/styles";
import { persistedBarcode1 } from "./mocking";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import {
  makeMockRootStore,
  type MockStores,
} from "../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../stores/stores-context";
import { type StoreContainer } from "../../../../stores/stores/RootStore";
import "../../../../../__mocks__/createObjectURL";
import "../../../../../__mocks__/revokeObjectURL";

vi.mock("mobx-react-lite", () => ({
  observer: (component: React.ComponentType<any>) => component,
  useObserver: (fn: () => React.ReactNode) => fn(),
  useLocalObservable: (initializer: () => unknown) => initializer(),
}));

let PrintDialog: typeof import("../PrintDialog").default;

beforeAll(async () => {
  ({ default: PrintDialog } = await import("../PrintDialog"));
});

const mockRootStore = (mockedStores?: MockStores): StoreContainer => {
  return makeMockRootStore({
    ...mockedStores,
    uiStore: {
      isSingleColumnLayout: false,
    },
  });
};

const mockContainer = {
  type: "CONTAINER",
  name: "Mock container",
  globalId: "IC1",
  identifiers: [{ doi: "10.1234/mock" }],
  barcodes: [persistedBarcode1],
} as unknown as InventoryRecord;

describe("Print Tests", () => {
  const openDialog = true; // if false, then dialog is null

  const modalRoot = document.createElement("div");
  modalRoot.setAttribute("id", "modal-root");

  const Dialog = () => {
    return (
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={mockRootStore()}>
          <PrintDialog
            showPrintDialog={openDialog}
            itemsToPrint={[mockContainer]}
            onClose={() => {}}
          />
        </storesContext.Provider>
      </ThemeProvider>
    );
  };

  describe("PrintDialog with items to print (barcodes)", () => {
    it("renders, has radio options for printerType, printMode, printSize (plus help text)", () => {
      render(<Dialog />);

      expect(screen.getAllByRole("radio")).toHaveLength(10);

      const standardOption = screen.getByLabelText("Standard Printer");
      expect(standardOption).toBeInTheDocument();
      expect(standardOption).toBeChecked();

      const labelOption = screen.getByLabelText("Label Printer");
      expect(labelOption).toBeInTheDocument();
      expect(labelOption).not.toBeChecked();

      const previewHeader = "Preview Barcode Label Layout";
      expect(screen.getByText(previewHeader)).toBeInTheDocument();
    });
  });

  describe("PrintDialog with items to print (barcodes)", () => {
    it("renders, content responds to clicked options", async () => {
      render(<Dialog />);

      const globalId = mockContainer.globalId;
      const location = "Location:";
      if (!globalId) throw new Error("Missing globalId");

      // on default "full" option elements are rendered, on "basic" they are not
      await waitFor(() => {
        expect(screen.getAllByText(globalId)[0]).toBeInTheDocument();
      });

      const basicOption = screen.getAllByLabelText("Basic")[0];
      fireEvent.click(basicOption);
      expect(basicOption).toBeChecked();
    });
  });
});
