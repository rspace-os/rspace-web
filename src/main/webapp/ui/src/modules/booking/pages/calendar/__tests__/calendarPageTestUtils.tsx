import "@/__tests__/__mocks__/matchMedia";
import { screen, waitFor } from "@testing-library/react";
import type userEvent from "@testing-library/user-event";
import { expect, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookingPagesHandlers } from "../../mocks/bookingPagesMocks";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");
const scrollIntoViewDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollIntoView");
export const scrollIntoView = vi.fn();

export function setupCalendarScrollTo() {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", { configurable: true, value: vi.fn() });
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: scrollIntoView });
}

export function setupCalendarBookableItems() {
  // The calendar keeps its search in the document URL, so a search typed in one test must not seed the next.
  window.history.replaceState(null, "", "/");
  scrollIntoView.mockClear();
  server.use(...bookingPagesHandlers());
}

export function restoreCalendarScrollTo() {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
  if (scrollIntoViewDescriptor)
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", scrollIntoViewDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
}

/** Turns a quick filter on or off in the Filters menu, then closes it. */
export async function toggleQuickFilter(user: ReturnType<typeof userEvent.setup>, name: string) {
  await user.click(await screen.findByRole("button", { name: /^Filters(?:$|,)/ }));
  await user.click(await screen.findByRole("switch", { name }));
  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("switch", { name })).not.toBeInTheDocument());
}
