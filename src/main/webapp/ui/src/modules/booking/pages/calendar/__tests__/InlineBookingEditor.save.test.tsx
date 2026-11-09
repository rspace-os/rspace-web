import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { inlineBookingSaveFixture } from "./inlineBookingSaveFixture";

it("closes before refreshing a saved booking without restarting or aborting overlap checks", async () => {
  const user = userEvent.setup();
  const fixture = inlineBookingSaveFixture();
  server.use(...fixture.handlers);
  render(<fixture.View />);
  const endTime = await screen.findByLabelText("booking:bookings.form.endTime");
  await user.clear(endTime);
  await user.type(endTime, "11:15");
  const save = screen.getByRole("button", { name: "booking:bookings.form.save" });
  await waitFor(() => expect(save).toBeEnabled());
  expect(fixture.requests.overlapChecks).toBeGreaterThan(0);
  await user.click(save);
  expect(await screen.findByText("Editor closed")).toBeVisible();
  await waitFor(() => expect(screen.getByLabelText("Booking version")).toHaveTextContent("8"));
  expect(fixture.requests.listRefreshes).toBe(1);
  expect(fixture.requests.overlapQueryStartsAfterSave).toBe(0);
  expect(fixture.requests.overlapRequestsAfterSave).toBe(0);
  expect(fixture.requests.abortedAfterSave).toEqual([]);
});
