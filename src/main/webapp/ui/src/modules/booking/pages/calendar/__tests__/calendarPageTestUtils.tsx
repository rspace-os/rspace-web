import "@/__tests__/__mocks__/matchMedia";
import { vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { bookableItemsHandlers } from "../../bookable-items/mocks/bookableItemsMocks";

export { renderCalendar } from "./calendarTestHarness";

const scrollToDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTo");

export function setupCalendarScrollTo() {
  Object.defineProperty(HTMLElement.prototype, "scrollTo", { configurable: true, value: vi.fn() });
}

export function setupCalendarBookableItems() {
  server.use(...bookableItemsHandlers(() => undefined));
}

export function restoreCalendarScrollTo() {
  if (scrollToDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollTo", scrollToDescriptor);
  else Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
}
