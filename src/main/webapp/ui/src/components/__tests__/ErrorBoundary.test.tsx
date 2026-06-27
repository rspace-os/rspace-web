/*
 * @vitest-environment jsdom
 *
 * The global test setup runs i18n in cimode (keys render verbatim), so the
 * default `ERROR_MSG` `<Trans>` would render its key rather than the message.
 * This test provides a real i18n instance with the `errorBoundary.message`
 * resource so the rendered support email can be asserted (mirrors the pattern
 * in HistoricalVersionAlertLink.test.tsx).
 */

import { render } from "@testing-library/react";
import { createInstance, type i18n as I18nInstance } from "i18next";
import type React from "react";
import { I18nextProvider, initReactI18next } from "react-i18next";
import { describe, expect, test } from "vitest";
import "@testing-library/jest-dom/vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import ErrorBoundary from "../ErrorBoundary";

function AlwaysError(): React.ReactNode {
  throw new Error("foo");
}

async function createTestI18n(): Promise<I18nInstance> {
  const i18n = createInstance();
  await i18n.use(initReactI18next).init({
    lng: "en-US",
    fallbackLng: "en-US",
    resources: {
      "en-US": {
        common: {
          "errorBoundary.message":
            "Something went wrong! Please refresh the page. If this error persists, please contact <0>support@researchspace.com</0> with details of when the issue happens.",
        },
      },
    },
    defaultNS: "common",
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

describe("ErrorBoundary", () => {
  test("Reports the support email address.", async () => {
    /*
     * This is needed because the `render` function will report any errors
     * using console.error, even though the ErrorBoundary catches them, which
     * just pollutes the output of the vitest CLI runner
     */
    const restoreConsole = silenceConsole(["error"], [/./]);
    const errorHandler = (event: ErrorEvent) => {
      event.preventDefault();
    };
    window.addEventListener("error", errorHandler);
    try {
      const i18n = await createTestI18n();
      const { container } = render(
        <I18nextProvider i18n={i18n}>
          <ErrorBoundary>
            <AlwaysError />
          </ErrorBoundary>
        </I18nextProvider>,
      );
      expect(container).toHaveTextContent("support@researchspace.com");
    } finally {
      window.removeEventListener("error", errorHandler);
      restoreConsole();
    }
  });
});
