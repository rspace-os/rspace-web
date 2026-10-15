import { test as base, mergeTests } from "@playwright/test";
import { test as documentSessionTest } from "@/__tests__/e2e/fixtures/flows/documentSessions";
import { test as fileSystemsTest } from "@/__tests__/e2e/fixtures/flows/environment/fileSystems";
import { test as filestoreBackendsTest } from "@/__tests__/e2e/fixtures/flows/environment/filestoreBackends";
import { test as ipWhitelistTest } from "@/__tests__/e2e/fixtures/flows/environment/ipWhitelist";
import { test as publicSharingTest } from "@/__tests__/e2e/fixtures/flows/environment/publicSharing";
import { test as repositoryIntegrationTest } from "@/__tests__/e2e/fixtures/flows/environment/repositoryIntegrations";
import { test as rorRegistryTest } from "@/__tests__/e2e/fixtures/flows/environment/rorRegistry";
import { CreateBookingPage } from "@/modules/booking/__tests__/pageObjects/CreateBookingPage";
import { MyBookingsPage } from "@/modules/booking/__tests__/pageObjects/MyBookingsPage";

export type { SelfServicePiActor, UserSession } from "@/__tests__/e2e/fixtures/flows/sessions/userSessions";

type BookingPageFixtures = {
  pageCreateBooking: CreateBookingPage;
  pageMyBookings: MyBookingsPage;
};

const bookingPagesTest = base.extend<BookingPageFixtures>({
  pageCreateBooking: async ({ page }, use) => use(new CreateBookingPage(page)),
  pageMyBookings: async ({ page }, use) => use(new MyBookingsPage(page)),
});

export const test = mergeTests(
  bookingPagesTest,
  repositoryIntegrationTest,
  documentSessionTest,
  publicSharingTest,
  ipWhitelistTest,
  fileSystemsTest,
  filestoreBackendsTest,
  rorRegistryTest,
);
