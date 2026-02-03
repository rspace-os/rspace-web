import * as React from "react";
import { render, screen } from "@testing-library/react";
import QrCodeScanner from "../QrCodeScanner";
import { type BarcodeInput } from "../BarcodeScannerSkeleton";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { test, describe, expect, vi } from 'vitest';
vi.mock("qr-scanner");
describe("QrCodeScanner", () => {
  test("Should scan correctly.", async () => {
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

