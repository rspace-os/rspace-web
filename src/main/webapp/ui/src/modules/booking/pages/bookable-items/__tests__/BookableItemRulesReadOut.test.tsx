import { screen, within } from "@testing-library/react";
import { expect, it } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import { BookableItemRulesReadOut } from "../BookableItemRulesReadOut";
import { bookableItemFixtures } from "../mocks/bookableItemsMocks";

it("names the gap without repeating the value's unit", async () => {
  await renderWithRealI18n(
    <BookableItemRulesReadOut
      configuration={{ ...bookableItemFixtures[0], roleSources: [], bufferBeforeMinutes: 15, bufferAfterMinutes: 15 }}
    />,
    { resources: { booking: bookingEnglish }, defaultNS: "booking" },
  );
  expect(screen.getByText("Gap before")).toBeVisible();
  const gap = screen.getByText("Gap before").parentElement;
  if (!gap) throw new Error("Expected a gap fact");
  expect(within(gap).getByText("15 minutes")).toBeVisible();
  expect(screen.queryByText("Gap before (minutes)")).not.toBeInTheDocument();
});
