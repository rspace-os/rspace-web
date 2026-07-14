/*
 * @vitest-environment jsdom
 *
 * Tests the real rich-text rendering in HistoricalVersionAlert using a production-like
 * i18n instance, because the title interpolation and latest-version link are behavior.
 */

import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { renderWithRealI18n, setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import materialTheme from "@/theme";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import HistoricalVersionAlert from "../HistoricalVersionAlert";

vi.mock("../../../common/InvApiService", () => ({
  default: {},
}));
vi.mock("../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));

describe("HistoricalVersionAlert rich i18n", () => {
  // record.recordTypeLabel resolves via the global i18n singleton (MobX
  // getter), not the isolated instance renderWithRealI18n provides.
  setupRealAppI18n();

  test("renders the interpolated title and latest-version link", async () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });

    await renderWithRealI18n(
      <ThemeProvider theme={materialTheme}>
        <HistoricalVersionAlert record={subsample} />
      </ThemeProvider>,
      { resources: { inventory: inventoryEn }, defaultNS: "inventory" },
    );

    expect(screen.getByText("This is version 2.")).toBeVisible();
    const link = screen.getByRole("link", { name: "View the latest version" });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1");
  });
});
