import { type APIRequestContext, expect } from "@playwright/test";
import { createDynamicUser, dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { CreateBookingPage } from "./pageObjects/CreateBookingPage";
import { MyBookingsPage } from "./pageObjects/MyBookingsPage";

type BookingConfiguration = { id: number };
type Booking = {
  id: number;
  purpose: string | null;
  target: { globalId: string } | null;
  privacy: "full" | "busy";
  canEdit: boolean;
  canCancel: boolean;
  canViewConfiguration: boolean;
  timezone: string | null;
};
type BookingPage = { docs: Booking[] };
type AccessDocument = {
  inherited: boolean;
  assignments: Array<{ grantee: { detail: string | null }; role: string }>;
};

const bookingFields =
  "id,version,target,canViewConfiguration,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,canCancel,createdAt,updatedAt";

async function createBookingConfiguration(
  apiContext: APIRequestContext,
  apiKey: string,
  instrumentId: number,
): Promise<BookingConfiguration> {
  const response = await apiContext.post("/api/v2/booking-configurations", {
    headers: { apiKey },
    data: {
      enabled: true,
      target: { relationTo: "booking-instruments", value: instrumentId },
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<BookingConfiguration>;
}

function futureDate(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

test.describe
  .serial("Booking permission release journey", { tag: tags.INVENTORY }, () => {
    test("inherits Inventory permissions and retains a former owner's booking after transfer", async ({
      apiContext,
      appUser,
      clientInventory,
      clientSysadmin,
      page,
      pageBookableItem,
    }) => {
      test.slow();

      const incomingOwner = await createDynamicUser(clientSysadmin, "ROLE_PI", "BookingIncomingOwner");
      const purpose = uniqueName("e2e-booking-permission-transfer");
      const date = futureDate(14);
      const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-booking-permissions") });
      const configuration = await createBookingConfiguration(apiContext, appUser.apiKey, instrument.id);
      const accessPath = `/api/v2/booking-configurations/${configuration.id}/access`;

      await test.step("the booking access document is inherited and read-only", async () => {
        const accessResponse = await apiContext.get(accessPath, { headers: { apiKey: appUser.apiKey } });
        expect(accessResponse.ok(), await accessResponse.text()).toBe(true);
        const access = (await accessResponse.json()) as AccessDocument;
        expect(access).toMatchObject({ inherited: true, assignments: [] });

        await pageBookableItem.openRecord(instrument.globalId);
        await pageBookableItem.openAccess();
        await expect(
          page.getByText("Permissions are inherited from the Inventory item", { exact: true }),
        ).toBeVisible();
        await expect(page.getByRole("link", { name: /^View .* in Inventory$/ })).toBeVisible();
        await expect(page.getByRole("combobox", { name: "Add user or group" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Save changes", exact: true })).toHaveCount(0);

        const rejectedUpdate = await apiContext.put(accessPath, {
          headers: { apiKey: appUser.apiKey, "If-Match": accessResponse.headers().etag },
          data: { assignments: [] },
        });
        expect(rejectedUpdate.status()).toBe(403);
      });

      let bookingId: number;
      await test.step("the owner creates a booking through the live UI", async () => {
        const booking = await new CreateBookingPage(page).create({
          globalId: instrument.globalId,
          date,
          startTime: "10:00",
          endTime: "11:00",
          purpose,
        });
        bookingId = booking.id;
      });

      await test.step("transferring the Inventory item transfers effective booking access", async () => {
        const response = await apiContext.put(`/api/inventory/v1/instruments/${instrument.id}/actions/changeOwner`, {
          headers: { apiKey: appUser.apiKey },
          data: { owner: { username: incomingOwner.username } },
        });
        expect(response.ok(), await response.text()).toBe(true);

        const incomingOwnerBooking = await apiContext.get(
          `/api/v2/bookings/${bookingId}?depth=1&fields[bookings]=${bookingFields}`,
          { headers: { apiKey: incomingOwner.apiKey } },
        );
        expect(incomingOwnerBooking.ok(), await incomingOwnerBooking.text()).toBe(true);
        expect((await incomingOwnerBooking.json()) as Booking).toMatchObject({
          target: { globalId: instrument.globalId },
          canViewConfiguration: true,
        });
      });

      await test.step("the former owner keeps a read-only booking with an unknown item", async () => {
        const bookingRead = await apiContext.get(
          `/api/v2/bookings/${bookingId}?depth=1&fields[bookings]=${bookingFields}`,
          { headers: { apiKey: appUser.apiKey } },
        );
        expect(bookingRead.ok(), await bookingRead.text()).toBe(true);
        const retained = (await bookingRead.json()) as Booking;
        expect(retained).toMatchObject({
          id: bookingId,
          purpose,
          target: null,
          timezone: null,
          privacy: "full",
          canEdit: false,
          canCancel: false,
          canViewConfiguration: false,
        });

        const ownRowsResponse = await apiContext.get(
          `/api/v2/bookings?depth=1&limit=100&where=id==${bookingId}&fields[bookings]=${bookingFields}`,
          { headers: { apiKey: appUser.apiKey } },
        );
        expect(ownRowsResponse.ok(), await ownRowsResponse.text()).toBe(true);
        const ownRows = (await ownRowsResponse.json()) as BookingPage;
        expect(ownRows.docs).toHaveLength(1);
        expect(ownRows.docs[0]).toMatchObject({ id: bookingId, target: null, purpose });

        const configurationRead = await apiContext.get(`/api/v2/booking-configurations/${configuration.id}`, {
          headers: { apiKey: appUser.apiKey },
        });
        expect(configurationRead.status()).toBe(404);

        const bookingPage = new MyBookingsPage(page);
        await bookingPage.open();
        const bookingsTable = page.getByRole("table");
        await expect(bookingsTable.getByText("Unknown item", { exact: true })).toBeVisible();
        await expect(bookingsTable.getByText(purpose, { exact: true })).toBeVisible();
        await expect(bookingsTable.getByText("Read-only: you no longer have access to this item.")).toBeVisible();
        await expect(bookingsTable.getByText(instrument.name, { exact: true })).toHaveCount(0);
        await expect(page.getByRole("link", { name: new RegExp(instrument.globalId) })).toHaveCount(0);
        await expect(page.getByRole("link", { name: "View details" })).toHaveCount(1);
        await expect(page.getByRole("link", { name: "Edit" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Cancel booking" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: /^\.ics file/ })).toHaveCount(0);

        await page.getByRole("link", { name: "View details" }).click();
        await expect(page).toHaveURL(new RegExp(`/booking/calendar/bookings/${bookingId}$`));
        await expect(page.getByText("Unknown item", { exact: true })).toBeVisible();
        await expect(page.getByText(purpose, { exact: true })).toBeVisible();
        await expect(page.getByRole("link", { name: "Edit" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Cancel booking" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: /^\.ics file/ })).toHaveCount(0);
        await expect(page.getByRole("link", { name: new RegExp(instrument.globalId) })).toHaveCount(0);
      });
    });
  });
