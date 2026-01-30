/*
 */
import React from "react";
import {
 render,
 act,
 screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import "../../../../../__mocks__/barcode-detection-api";
import AllBarcodeScanner from "../AllBarcodeScanner";
import { sleep } from "../../../../util/Util";
import { type BarcodeInput } from "../BarcodeScannerSkeleton";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import {
  type Mock,
 describe,
 expect,
 beforeEach,
 it,
 vi,
} from "vitest";

beforeEach(() => {
  vi.clearAllMocks();
});


describe("AllBarcodeScanner", () => {
  it("Should scan correctly.", async () => {
    const user = userEvent.setup();
    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() =>
      Promise.resolve()
    );

    const onScan = vi.fn() as Mock<void, [BarcodeInput]>;

    render(
      <ThemeProvider theme={materialTheme}>
        <AllBarcodeScanner
          onClose={() => {}}
          onScan={onScan}
          buttonPrefix="Scan"
        />
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


