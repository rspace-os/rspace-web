/*
 * @vitest-environment jsdom
 *
 * Tests the real rich-text rendering in HistoricalVersionAlert using a production-like
 * i18n instance, because the title interpolation and latest-version link are behavior.
 */

import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import { afterAll, beforeAll, describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import "@testing-library/jest-dom/vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import appI18n from "@/modules/common/i18n";
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
  beforeAll(async () => {
    await appI18n.changeLanguage("en-US");
  });

  afterAll(async () => {
    await appI18n.changeLanguage("cimode");
  });

  test("renders the interpolated title and latest-version link", async () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });
    const i18n = await createTestI18n({ inventory: inventoryEn }, "inventory");

    render(
      <ThemeProvider theme={materialTheme}>
        <I18nextProvider i18n={i18n}>
          <HistoricalVersionAlert record={subsample} />
        </I18nextProvider>
      </ThemeProvider>,
    );

    expect(screen.getByText("This is version 2 of the subsample.")).toBeVisible();
    const link = screen.getByRole("link", { name: "View the latest version" });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1");
  });
});
