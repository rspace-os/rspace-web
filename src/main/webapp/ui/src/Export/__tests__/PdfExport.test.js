/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import PdfExport from "../PdfExport";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("PdfExport", () => {
  test("Toggling a switch should set a boolean value.", () => {
    const updateExportDetails: (string, mixed) => void = jest.fn();

    render(
      <PdfExport
        exportDetails={{
          exportFormat: "PDF",
          exportName: "",
          provenance: false,
          comments: false,
          annotations: false,
          restartPageNumberPerDoc: false,
          pageSize: "A4",
          defaultPageSize: "A4",
          dateType: "EXP",
          includeFooterAtEndOnly: false,
          setPageSizeAsDefault: false,
          includeFieldLastModifiedDate: false,
        }}
        updateExportDetails={updateExportDetails}
        validations={{
          submitAttempt: false,
          inputValidations: {
            exportName: false,
          },
        }}
      />
    );

    fireEvent.click(
      screen.getByRole("checkbox", { name: "Include provenance information" })
    );

    expect(updateExportDetails).toHaveBeenCalledWith("provenance", true);
  });
});
