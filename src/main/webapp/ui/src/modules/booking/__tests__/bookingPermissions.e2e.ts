import { Temporal } from "@js-temporal/polyfill";
import { type APIRequestContext, expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { BOOKING_READ_FIELDS } from "@/modules/booking/domain/booking";
import { type BookingConfiguration, createBookingConfiguration, type OpeningHoursEntry } from "./bookingE2eFixtures";

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

async function readBookingConfiguration(
  apiContext: APIRequestContext,
  apiKey: string,
  id: number,
): Promise<BookingConfiguration> {
  const response = await apiContext.get(`/api/v2/booking-configurations/${id}`, { headers: { apiKey } });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<BookingConfiguration>;
}

function futureDate(days: number): string {
  return Temporal.Now.zonedDateTimeISO("UTC").toPlainDate().add({ days }).toString();
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
      pageCreateBooking,
      pageMyBookings,
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
          pageBookableItem.accessPanel.getByRole("link", { name: "go to the inventory item's settings" }),
        ).toHaveAttribute("href", `/inventory/instrument/${instrument.id}`);
        const permissionChoices = pageBookableItem.accessPanel.getByRole("radio");
        await expect(permissionChoices).toHaveCount(3);
        for (const choice of await permissionChoices.all()) await expect(choice).toBeDisabled();
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
        const booking = await pageCreateBooking.create({
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
          `/api/v2/bookings/${bookingId}?depth=1&fields[bookings]=${BOOKING_READ_FIELDS}`,
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
          `/api/v2/bookings/${bookingId}?depth=1&fields[bookings]=${BOOKING_READ_FIELDS}`,
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
          `/api/v2/bookings?depth=1&limit=100&where=id==${bookingId}&fields[bookings]=${BOOKING_READ_FIELDS}`,
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

        await pageMyBookings.open();
        const bookingsTable = page.getByRole("table");
        await expect(bookingsTable.getByText("Unknown item", { exact: true })).toBeVisible();
        await expect(bookingsTable.getByText(purpose, { exact: true })).toBeVisible();
        await expect(bookingsTable.getByText("Read-only: you no longer have access to this item.")).toBeVisible();
        await expect(bookingsTable.getByText(instrument.name, { exact: true })).toHaveCount(0);
        await expect(page.getByRole("link", { name: instrument.globalId, exact: false })).toHaveCount(0);
        await expect(page.getByRole("link", { name: "View details" })).toHaveCount(1);
        await expect(page.getByRole("link", { name: "Edit" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Cancel booking" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: ".ics file", exact: false })).toHaveCount(0);

        await page.getByRole("link", { name: "View details" }).click();
        await expect(page).toHaveURL(new URL(`/booking/calendar/bookings/${bookingId}`, page.url()).href);
        await expect(page.getByText("Unknown item", { exact: true })).toBeVisible();
        await expect(page.getByText(purpose, { exact: true })).toBeVisible();
        await expect(page.getByRole("link", { name: "Edit" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: "Cancel booking" })).toHaveCount(0);
        await expect(page.getByRole("button", { name: ".ics file", exact: false })).toHaveCount(0);
        await expect(page.getByRole("link", { name: instrument.globalId, exact: false })).toHaveCount(0);
      });
    });
  });

test.describe("Independent booking hours release acceptance", { tag: tags.INVENTORY }, () => {
  const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
  const initialHours = weekdays.map((_, index) => ({ dayOfWeek: index + 1, start: "08:00", end: "18:00" }));

  test("saves seven independent days, cancels a draft, and rejects bookings on a newly closed day", async ({
    apiContext,
    appUser,
    clientInventory,
    pageBookableItem,
  }) => {
    test.slow();
    const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-independent-hours") });
    const configuration = await createBookingConfiguration(apiContext, appUser.apiKey, instrument.id, initialHours);
    const savedHours: OpeningHoursEntry[] = [
      { dayOfWeek: 1, start: "08:00", end: "16:00" },
      { dayOfWeek: 2, start: "08:15", end: "16:15" },
      { dayOfWeek: 3, start: "08:30", end: "16:30" },
      { dayOfWeek: 4, start: "08:45", end: "16:45" },
      { dayOfWeek: 5, start: "09:00", end: "17:00" },
      { dayOfWeek: 6, start: "09:15", end: "17:15" },
      { dayOfWeek: 7, start: "09:30", end: "24:00" },
    ];

    await test.step("save all seven independent intervals through the configuration editor", async () => {
      await pageBookableItem.openDetails(instrument.globalId);
      await pageBookableItem.openEditor();
      await expect(pageBookableItem.hours.getByRole("listitem")).toHaveCount(7);
      await pageBookableItem.setHours("Monday", savedHours[0]);
      await expect(pageBookableItem.endTime("Tuesday")).toHaveValue("18:00");
      for (const [index, day] of weekdays.entries()) {
        await expect(pageBookableItem.openDay(day)).toBeChecked();
        if (index > 0) await pageBookableItem.setHours(day, savedHours[index]);
      }
      await pageBookableItem.save(configuration.id);
      const saved = await readBookingConfiguration(apiContext, appUser.apiKey, configuration.id);
      expect(saved.openingHours).toEqual(savedHours);
      expect(saved.configurationVersion).toBe(configuration.configurationVersion + 1);
    });

    await test.step("Cancel restores the complete saved week without changing its version", async () => {
      await pageBookableItem.openEditor();
      for (const day of weekdays) await pageBookableItem.setHours(day, { start: "10:00", end: "15:00" });
      await pageBookableItem.openDay("Monday").uncheck();
      await pageBookableItem.cancel();
      const retained = await readBookingConfiguration(apiContext, appUser.apiKey, configuration.id);
      expect(retained.openingHours).toEqual(savedHours);
      expect(retained.configurationVersion).toBe(configuration.configurationVersion + 1);
      await pageBookableItem.openEditor();
      for (const [index, day] of weekdays.entries()) {
        await expect(pageBookableItem.openDay(day)).toBeChecked();
        await expect(pageBookableItem.startTime(day)).toHaveValue(savedHours[index].start);
        await expect(pageBookableItem.endTime(day)).toHaveValue(
          savedHours[index].end === "24:00" ? "00:00" : savedHours[index].end,
        );
      }
      await pageBookableItem.cancel();
    });

    await test.step("closing a previously bookable weekday rejects a real booking", async () => {
      const date = Temporal.PlainDate.from(futureDate(14));
      const bookingInput = (day: Temporal.PlainDate) => ({
        target: { relationTo: "instruments", value: instrument.id },
        start: day.toZonedDateTime({ timeZone: configuration.timezone, plainTime: "10:00" }).toInstant().toString(),
        end: day.toZonedDateTime({ timeZone: configuration.timezone, plainTime: "11:00" }).toInstant().toString(),
        purpose: uniqueName("e2e-closed-weekday"),
      });
      const accepted = await apiContext.post("/api/v2/bookings", {
        headers: { apiKey: appUser.apiKey },
        data: bookingInput(date),
      });
      expect(accepted.status(), await accepted.text()).toBe(201);

      await pageBookableItem.openEditor();
      await pageBookableItem.openDay(weekdays[date.dayOfWeek - 1]).uncheck();
      await pageBookableItem.save(configuration.id);
      const closed = await readBookingConfiguration(apiContext, appUser.apiKey, configuration.id);
      expect(closed.openingHours).toEqual(savedHours.filter((entry) => entry.dayOfWeek !== date.dayOfWeek));

      const rejected = await apiContext.post("/api/v2/bookings", {
        headers: { apiKey: appUser.apiKey },
        data: bookingInput(date.add({ days: 7 })),
      });
      expect(rejected.status(), await rejected.text()).toBe(400);
      expect(await rejected.json()).toMatchObject({ code: "errors.api.v2.booking.openingHours" });
    });
  });

  test("collection PATCH rejects scalar rows and missing children with indexed errors and no version change", async ({
    apiContext,
    appUser,
    clientInventory,
  }) => {
    const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-array-rejections") });
    const configuration = await createBookingConfiguration(apiContext, appUser.apiKey, instrument.id, initialHours);
    for (const invalid of [
      { openingHours: [1], path: "openingHours[0]" },
      { openingHours: [{ dayOfWeek: 1, start: "09:00" }], path: "openingHours[0].end" },
    ]) {
      const rejected = await apiContext.patch(`/api/v2/booking-configurations/${configuration.id}`, {
        headers: { apiKey: appUser.apiKey, "If-Match": `"${configuration.configurationVersion}"` },
        data: { openingHours: invalid.openingHours },
      });
      expect(rejected.status(), await rejected.text()).toBe(400);
      expect(await rejected.json()).toMatchObject({
        invalidParams: expect.arrayContaining([{ name: invalid.path, reason: expect.any(String) }]),
      });
      const retained = await readBookingConfiguration(apiContext, appUser.apiKey, configuration.id);
      expect(retained.openingHours).toEqual(configuration.openingHours);
      expect(retained.configurationVersion).toBe(configuration.configurationVersion);
    }
  });
});
