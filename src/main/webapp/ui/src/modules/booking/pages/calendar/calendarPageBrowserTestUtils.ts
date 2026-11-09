import { worker } from "@/__tests__/browserMocks";
import { bookingPagesHandlers } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { CalendarPage as CalendarPageObject } from "./pageObjects/CalendarPage";

export const calendar = new CalendarPageObject();

export function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}
