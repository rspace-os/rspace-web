/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { act, cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/barcode-detection-api";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import materialTheme from "../../../../theme";
import { sleep } from "../../../../util/Util";
import AllBarcodeScanner from "../AllBarcodeScanner";
import type { BarcodeInput } from "../BarcodeScannerSkeleton";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("AllBarcodeScanner", () => {
    test("Should scan correctly.", async () => {
        const user = userEvent.setup();
        jest.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());

        const onScan = jest.fn() as jest.Mock<void, [BarcodeInput]>;

        render(
            <ThemeProvider theme={materialTheme}>
                <AllBarcodeScanner onClose={() => {}} onScan={onScan} buttonPrefix="Scan" />
            </ThemeProvider>,
        );

        /*
         * Wait a second because the barcode scanner checks for a barcode once per
         * second. The extra 100ms is just to ensure that this code doesn't execute
         * before the onScan call.
         */
        await act(async () => {
            await sleep(1100);
        });

        await user.click(screen.getByText("Scan"));

        /*
         * This mocked value comes from src/main/webapp/ui/__mocks__/barcode-detection-api.js
         */
        expect(onScan).toHaveBeenCalledWith({
            rawValue: "foo",
            format: "qr_code",
        });
    });
});
