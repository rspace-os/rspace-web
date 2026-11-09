import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { bookingPagesHandlers, resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { AllBookableItemsStory } from "./AllBookableItemsPage.story";
import { AllBookableItemsPage } from "./pageObjects/AllBookableItemsPage";

const pageObj = new AllBookableItemsPage();

function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

let history: RouterHistory;
let browserUrl: string;

beforeEach(() => {
  history = createMemoryHistory({ initialEntries: ["/booking/all-items"] });
  browserUrl = window.location.href;
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  const actualUrl = window.location.href;
  window.history.replaceState({}, "", "/");
  expect(actualUrl).toBe(browserUrl);
});

describe("the All Bookable Items page", () => {
  test("navigates through the instrument name to the bookable item details page", async () => {
    render(<AllBookableItemsStory history={history} />);

    await expect.element(pageObj.detailsButton).toBeVisible();
    await pageObj.instrumentName.click();

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => history.location.pathname).toBe("/booking/bookable-items/IN123");
  });
});

test("names desktop icon actions on hover and keyboard focus", async () => {
  await page.viewport(1376, 795);
  render(<AllBookableItemsStory containerWidth={1100} />);
  for (const control of [pageObj.detailsButton, pageObj.electronBookButton]) {
    await control.hover();
    await expect.element(page.getByRole("tooltip")).toBeVisible();
    await pageObj.heading.hover();
    control.element().focus();
    await userEvent.keyboard("{Tab}{Shift>}{Tab}{/Shift}");
    await expect.element(page.getByRole("tooltip")).toBeVisible();
  }
});
