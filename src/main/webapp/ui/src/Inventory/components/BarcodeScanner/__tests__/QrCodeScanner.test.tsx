/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/barcode-detection-api";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import materialTheme from "../../../../theme";
import type { BarcodeInput } from "../BarcodeScannerSkeleton";
import QrCodeScanner from "../QrCodeScanner";

jest.mock("qr-scanner");

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("QrCodeScanner", () => {
    test("Should scan correctly.", async () => {
        const user = userEvent.setup();
        const onScan = jest.fn() as jest.Mock<void, [BarcodeInput]>;

        render(
            <ThemeProvider theme={materialTheme}>
                <QrCodeScanner onClose={() => {}} onScan={onScan} buttonPrefix="Scan" />
            </ThemeProvider>,
        );

        await user.click(screen.getByText("Scan"));

        expect(onScan).toHaveBeenCalledWith({
            rawValue: "foo",
            format: "qr_code",
        });
    });
});
