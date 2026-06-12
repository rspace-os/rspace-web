import { ThemeProvider } from "@mui/material/styles";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import { beforeAll, describe, expect, test, vi } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { type MockStores, makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
// biome-ignore lint/style/useImportType: initial biome migration
import { type StoreContainer } from "../../../../stores/stores/RootStore";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import { persistedBarcode1 } from "./mocking";

vi.mock("mobx-react-lite", () => ({
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
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
          <PrintDialog showPrintDialog={openDialog} itemsToPrint={[mockContainer]} onClose={() => {}} />
        </storesContext.Provider>
      </ThemeProvider>
    );
  };

  const renderDialog = async () => {
    render(<Dialog />);
    await waitFor(() => {
      expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
    });
  };
  describe("PrintDialog with items to print (barcodes)", () => {
    test("renders, has radio options for printerType, printMode, printSize (plus help text)", async () => {
      await renderDialog();

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
    test("renders, content responds to clicked options", async () => {
      await renderDialog();
      const globalId = mockContainer.globalId;
      // biome-ignore lint/correctness/noUnusedVariables: initial biome migration
      const location = "Location:";

      if (!globalId) throw new Error("Missing globalId");
      // on default "full" option elements are rendered, on "basic" they are not
      expect(screen.getAllByText(globalId)[0]).toBeInTheDocument();
      const basicOption = screen.getAllByLabelText("Basic")[0];
      await act(async () => {
        fireEvent.click(basicOption);
        await Promise.resolve();
      });

      expect(basicOption).toBeChecked();
    });
  });
});
