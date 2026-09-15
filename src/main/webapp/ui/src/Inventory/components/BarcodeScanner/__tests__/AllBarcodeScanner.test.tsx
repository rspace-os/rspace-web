import { act, render } from "@testing-library/react";
import "@/__tests__/__mocks__/barcode-detection-api";
import { ThemeProvider } from "@mui/material/styles";
import { delay } from "es-toolkit";
import { describe, expect, test, vi } from "vitest";
import materialTheme from "../../../../theme";
import AllBarcodeScanner from "../AllBarcodeScanner";
import type { BarcodeInput } from "../BarcodeScannerSkeleton";

describe("AllBarcodeScanner", () => {
  test("Should submit the first detected barcode automatically.", async () => {
    vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
    const onScan = vi.fn<(input: BarcodeInput) => void>();
    const onClose = vi.fn<() => void>();
    render(
      <ThemeProvider theme={materialTheme}>
        <AllBarcodeScanner onClose={onClose} onScan={onScan} />
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

    /*
     * This mocked value comes from src/__tests__/__mocks__/barcode-detection-api.ts
     */
    expect(onScan).toHaveBeenCalledWith({
      rawValue: "foo",
      format: "qr_code",
    });
    expect(onClose).toHaveBeenCalled();
  });
});
