import { act, render, screen } from "@testing-library/react";
import "@/__tests__/__mocks__/barcode-detection-api";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import { delay } from "es-toolkit";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import AllBarcodeScanner from "../AllBarcodeScanner";
import type { BarcodeInput } from "../BarcodeScannerSkeleton";

describe("AllBarcodeScanner", () => {
  test("Should scan correctly.", async () => {
    const user = userEvent.setup();

    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
    const onScan = vi.fn<(input: BarcodeInput) => void>();
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
      await delay(1100);
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
