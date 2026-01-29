/*
 */
import * as React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import "../../../../../__mocks__/barcode-detection-api";
import QrCodeScanner from "../QrCodeScanner";
import { type BarcodeInput } from "../BarcodeScannerSkeleton";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import { type Mock, describe, test, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("qr-scanner");

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

describe("QrCodeScanner", () => {
  test("Should scan correctly.", async () => {
    const user = userEvent.setup();
    const onScan = vi.fn() as Mock<void, [BarcodeInput]>;

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


