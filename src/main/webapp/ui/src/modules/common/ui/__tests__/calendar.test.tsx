import { render, screen } from "@testing-library/react";
import { createInstance } from "i18next";
import ICU from "i18next-icu";
import { I18nextProvider } from "react-i18next";
import { describe, expect, it } from "vitest";
import { firstDayOfWeek } from "@/modules/common/i18n/localeFormat";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import { Calendar } from "../calendar";

async function i18nIn(language: string) {
  const i18n = createInstance();
  await i18n.use(ICU).init({
    lng: language,
    resources: { [language]: { common } },
    defaultNS: "common",
    ns: ["common"],
    keySeparator: ".",
    nsSeparator: ":",
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });
  return i18n;
}

describe("Calendar", () => {
  it.each([
    ["en-US", "March 2025", "Monday, March 3, 2025"],
    ["fr-FR", "mars 2025", "lundi 3 mars 2025"],
  ])("names months, weekdays and days in the %s UI language", async (language, caption, day) => {
    render(
      <I18nextProvider i18n={await i18nIn(language)}>
        <Calendar mode="single" month={new Date(2025, 2, 1)} />
      </I18nextProvider>,
    );
    // 2 March 2025 is a Sunday; the week starts on the browser region's first day. DayPicker hides the weekday row.
    const regionFirstDay = firstDayOfWeek(new Intl.DateTimeFormat().resolvedOptions().locale) % 7;
    const firstWeekday = new Intl.DateTimeFormat(language, { weekday: "long" }).format(
      new Date(2025, 2, 2 + regionFirstDay),
    );

    expect(screen.getByText(caption)).toBeVisible();
    expect(screen.getAllByRole("columnheader", { hidden: true })[0]).toHaveAccessibleName(firstWeekday);
    expect(screen.getByRole("button", { name: day })).toBeVisible();
  });
});
