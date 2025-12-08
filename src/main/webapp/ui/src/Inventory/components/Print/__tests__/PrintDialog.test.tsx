/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { type MockStores, makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import type { StoreContainer } from "../../../../stores/stores/RootStore";
import { storesContext } from "../../../../stores/stores-context";
import materialTheme from "../../../../theme";
import PrintDialog from "../PrintDialog";
import { generatedBarcode, persistedBarcode1 } from "./mocking";
import "../../../../../__mocks__/createObjectURL";
import "../../../../../__mocks__/revokeObjectURL";

const mockRootStore = (mockedStores?: MockStores): StoreContainer => {
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
    const generatedBarcode1 = generatedBarcode(mockContainer.recordType, mockContainer.globalId);
    mockContainer.barcodes.push(generatedBarcode1);
}

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

            const basicOption = screen.getByLabelText("Basic");
            fireEvent.click(basicOption);
            expect(screen.queryByText(location)).not.toBeInTheDocument();
        });
    });
});
