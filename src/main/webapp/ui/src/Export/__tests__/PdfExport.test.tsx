import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";

import PdfExport from "../PdfExport";

describe("PdfExport", () => {
  test("Toggling a switch should set a boolean value.", async () => {
    const user = userEvent.setup();
    const updateExportDetails: (key: string, exportDetail: unknown) => void = vi.fn();
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
    await user.click(screen.getByRole("checkbox", { name: "workspace:export.format.pdf.checkboxes.provenance" }));
    expect(updateExportDetails).toHaveBeenCalledWith("provenance", true);
  });
});
