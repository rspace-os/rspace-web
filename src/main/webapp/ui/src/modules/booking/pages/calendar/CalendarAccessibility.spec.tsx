import "@/modules/common/styles/index.css";
import { createMemoryHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

registerHandlers();
afterEach(cleanup);

test("keeps the mobile editor and Save on screen and returns focus after Escape", async () => {
  await page.viewport(390, 844);
  render(
    <CalendarPageStory history={createMemoryHistory({ initialEntries: ["/booking/calendar?date=2026-08-17"] })} />,
  );
  const trigger = calendar.showEventDetails("Confocal microscope");
  await trigger.click();
  await calendar.editBooking.click();
  await expect.element(calendar.inlineSave).toBeVisible();
  await expect
    .poll(() => {
      const box = calendar.inlineSave.element().getBoundingClientRect();
      return box.bottom <= 844 && box.top >= 0 && box.left >= 0 && box.right <= 390;
    })
    .toBe(true);
  await expectNoAxeViolations();
  await calendar.closeWithEscape();
  await expect.element(trigger).toHaveFocus();
});
