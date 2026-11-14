import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { inlineBookingSaveFixture } from "./__tests__/inlineBookingSaveFixture";
import { InlineBookingEditor } from "./InlineBookingEditor";

afterEach(() => cleanup());

test("closes before refreshing a saved booking without restarting or aborting overlap checks", async () => {
  const fixture = inlineBookingSaveFixture(InlineBookingEditor);
  worker.use(...fixture.handlers);
  const wrapper = await createRealI18nWrapper({
    resources: { booking: bookingEnglish, common: commonEnglish },
    defaultNS: "common",
  });
  render(<fixture.View />, { wrapper });
  const endTime = page.getByLabelText("End time");
  await endTime.fill("11:15");
  const save = page.getByRole("button", { name: "Save changes" });
  await expect.element(save).toBeEnabled();
  expect(fixture.requests.overlapChecks).toBeGreaterThan(0);
  await userEvent.click(save);
  await expect.element(page.getByText("Editor closed", { exact: true })).toBeVisible();
  await expect.element(page.getByLabelText("Booking version")).toHaveTextContent("8");
  expect(fixture.requests.listRefreshes).toBe(1);
  expect(fixture.requests.overlapQueryStartsAfterSave).toBe(0);
  expect(fixture.requests.overlapRequestsAfterSave).toBe(0);
  expect(fixture.requests.abortedAfterSave).toEqual([]);
});
