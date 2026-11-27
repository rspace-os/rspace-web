import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import RecordsImport from "../RecordsImport";

vi.mock("../Fields/File", () => ({ default: () => null }));
vi.mock("../Fields/ColumnFieldMapping", () => ({ default: () => null }));
vi.mock("../Fields/TemplateDetails", () => ({ default: () => null }));

function renderImport(inventoryReadOnlyReason: string | null) {
  render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider
        value={makeMockRootStore({
          importStore: {
            importData: {
              recordType: "SAMPLES",
              isSamplesImport: true,
              importSubmittable: true,
              samplesSubmittable: true,
              byRecordType: () => null,
            },
            isCurrentImportState: () => false,
          },
          peopleStore: { inventoryReadOnlyReason },
        })}
      >
        <RecordsImport />
      </storesContext.Provider>
    </ThemeProvider>,
  );
  return screen.getByRole("button", { name: /import\.actions\.importSelected/ });
}

describe("RecordsImport", () => {
  test("the import button is enabled for a submittable import", () => {
    expect(renderImport(null)).toBeEnabled();
  });

  test("the import button is disabled while Inventory is read-only", () => {
    expect(renderImport("License expired")).toBeDisabled();
  });
});
