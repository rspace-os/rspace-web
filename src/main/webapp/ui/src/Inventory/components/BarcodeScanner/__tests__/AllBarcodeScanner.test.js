/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, act, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/barcode-detection-api";
import AllBarcodeScanner from "../AllBarcodeScanner";
import { sleep } from "../../../../util/Util";
import { type BarcodeInput } from "../BarcodeScannerSkeleton";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("AllBarcodeScanner", () => {
  test("Should scan correctly.", async () => {
    jest.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => {});

    const onScan = jest.fn<[BarcodeInput], void>();

    render(
      <AllBarcodeScanner
        onClose={() => {}}
        onScan={onScan}
        buttonPrefix="Scan"
      />
    );

    /*
     * Wait a second because the barcode scanner checks for a barcode once per
     * second. The extra 100ms is just to ensure that this code doesn't execute
     * before the onScan call.
     */
    await act(async () => {
      await sleep(1100);
    });

    screen.getByText("Scan").click();

    /*
     * This mocked value comes from src/main/webapp/ui/__mocks__/barcode-detection-api.js
     */
    expect(onScan).toHaveBeenCalledWith({
      rawValue: "foo",
      format: "qr_code",
    });
  });
});
