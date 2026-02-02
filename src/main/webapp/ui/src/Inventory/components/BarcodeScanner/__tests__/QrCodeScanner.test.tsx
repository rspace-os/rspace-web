import * as React from "react";
import {
 render,
 screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import "../../../../../__mocks__/barcode-detection-api";
import QrCodeScanner from "../QrCodeScanner";
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

vi.mock("qr-scanner");

beforeEach(() => {
  vi.clearAllMocks();
});


describe("QrCodeScanner", () => {
  it("Should scan correctly.", async () => {
    const user = userEvent.setup();
    const onScan = vi.fn<(input: BarcodeInput) => void>();

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


