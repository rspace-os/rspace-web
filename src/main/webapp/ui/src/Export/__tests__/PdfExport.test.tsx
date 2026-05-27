import { test, describe, expect, vi } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PdfExport from "../PdfExport";
describe("PdfExport", () => {
  test("Toggling a switch should set a boolean value.", async () => {
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
      />,
    );
    await userEvent.click(
      screen.getByRole("checkbox", {
        name: "Include provenance information",
      }),
    );
    expect(updateExportDetails).toHaveBeenCalledWith("provenance", true);
  });
});
