/*
 * @vitest-environment jsdom
 *
 * Tests the real link rendering in HistoricalVersionAlert using a real i18n
 * instance (vi.unmock must be at the top level, so this lives in a separate file
 * from the key-mock tests in HistoricalVersionAlert.test.tsx).
 */

import { ThemeProvider } from "@mui/material/styles";
import { screen } from "@testing-library/react";
import { createInstance, type i18n as I18nInstance } from "i18next";
import { describe, expect, test, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import "@testing-library/jest-dom/vitest";
import materialTheme from "@/theme";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";

vi.unmock("react-i18next");

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

const { I18nextProvider, initReactI18next } = await import("react-i18next");
const { default: HistoricalVersionAlert } = await import("../HistoricalVersionAlert");

async function createTestI18n(): Promise<I18nInstance> {
  const i18n = createInstance();
  await i18n.use(initReactI18next).init({
    lng: "en-US",
    fallbackLng: "en-US",
    resources: {
      "en-US": {
        inventory: {
          "historicalVersion.title": "Version {version} of the {type}",
          "historicalVersion.readOnlyWithLink": "It is read-only. <a>View the latest version</a>",
          "historicalVersion.contentsNotShown":
            "Contents are not part of the historical snapshot, so they are not shown.",
        },
      },
    },
    defaultNS: "inventory",
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

describe("HistoricalVersionAlert link href", () => {
  test("link in read-only message points to the latest-version URL", async () => {
    const subsample = makeMockSubSample({
      version: 2,
      historicalVersion: true,
      globalId: "SS1v2",
    });

    const i18n = await createTestI18n();
    render(
      <ThemeProvider theme={materialTheme}>
        <I18nextProvider i18n={i18n}>
          <HistoricalVersionAlert record={subsample} />
        </I18nextProvider>
      </ThemeProvider>,
    );

    const link = screen.getByRole("link", { name: /view the latest version/i });
    expect(link).toHaveAttribute("href", "/inventory/subsample/1");
  });
});
