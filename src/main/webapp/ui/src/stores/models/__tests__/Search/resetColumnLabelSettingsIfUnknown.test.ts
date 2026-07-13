import { describe, expect, test, vi } from "vitest";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import type { InventoryRecord } from "../../../definitions/InventoryRecord";
import type { CellContent } from "../../../definitions/Tables";
import Search from "../../Search";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle

const recordWithColumns = (...columns: Array<string>): InventoryRecord =>
  ({
    adjustableTableOptions: () =>
      new Map<string, () => CellContent>(
        columns.map((column): [string, () => CellContent] => [column, () => ({ renderOption: "node", data: column })]),
      ),
  }) as unknown as InventoryRecord;

describe("resetColumnLabelSettingsIfUnknown", () => {
  test("resets stale label-based defaults on construction.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        mainColumn: "Name",
        adjustableColumns: ["Global ID", "Owner", "Last Modified"],
      },
    });

    expect(search.uiConfig.mainColumn).toBe("name");
    expect(search.uiConfig.adjustableColumns).toEqual(["globalId", "owner", "lastModified"]);
  });

  test("resets adjustable columns when a configured key is not available in the result options.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        adjustableColumns: ["missingColumn", "owner", "lastModified"],
      },
    });
    search.fetcher.setResults([recordWithColumns("globalId", "owner", "lastModified")]);

    search.resetColumnLabelSettingsIfUnknown();

    expect(search.uiConfig.adjustableColumns).toEqual(["globalId", "owner", "lastModified"]);
  });

  test("resets stale label-based adjustable columns once result options are available.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        mainColumn: "name",
        adjustableColumns: ["Global ID", "Owner", "Last Modified"],
      },
    });
    search.fetcher.setResults([recordWithColumns("globalId", "owner", "lastModified")]);

    search.resetColumnLabelSettingsIfUnknown();

    expect(search.uiConfig.adjustableColumns).toEqual(["globalId", "owner", "lastModified"]);
  });

  test("keeps custom columns that are available in the result options.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        adjustableColumns: ["customField", "owner", "lastModified"],
      },
    });
    search.fetcher.setResults([recordWithColumns("customField", "owner", "lastModified")]);

    search.resetColumnLabelSettingsIfUnknown();

    expect(search.uiConfig.adjustableColumns).toEqual(["customField", "owner", "lastModified"]);
  });

  test("keeps built-in columns even when they are not available in the current result options.", () => {
    const search = new Search({
      factory: mockFactory(),
      uiConfig: {
        adjustableColumns: ["expiryDate", "owner", "lastModified"],
      },
    });
    search.fetcher.setResults([recordWithColumns("globalId", "owner", "lastModified")]);

    search.resetColumnLabelSettingsIfUnknown();

    expect(search.uiConfig.adjustableColumns).toEqual(["expiryDate", "owner", "lastModified"]);
  });
});
