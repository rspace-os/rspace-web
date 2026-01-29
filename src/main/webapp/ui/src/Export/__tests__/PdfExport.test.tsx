/*
 * @vitest-environment jsdom
 */
import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import PdfExport from "../PdfExport";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(cleanup);

describe("PdfExport", () => {
  test("Toggling a switch should set a boolean value.", () => {
    const updateExportDetails: (key: string, exportDetail: unknown) => void =
      vi.fn();

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


