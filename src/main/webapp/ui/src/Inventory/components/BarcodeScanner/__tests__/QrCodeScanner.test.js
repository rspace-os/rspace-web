/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/barcode-detection-api";
import QrCodeScanner from "../QrCodeScanner";
import { type BarcodeInput } from "../BarcodeScannerSkeleton";
import userEvent from "@testing-library/user-event";

jest.mock("qr-scanner");

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("QrCodeScanner", () => {
  test("Should scan correctly.", async () => {
    const user = userEvent.setup();
    const onScan = jest.fn<[BarcodeInput], void>();

    render(
      <QrCodeScanner onClose={() => {}} onScan={onScan} buttonPrefix="Scan" />
    );

    await user.click(screen.getByText("Scan"));

    expect(onScan).toHaveBeenCalledWith({
      rawValue: "foo",
      format: "qr_code",
    });
  });
});
