import { type APIRequestContext, expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { createDynamicUser, loginInNewContext, dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";
import { BookingPermissionsPage } from "./pageObjects/BookingPermissionsPage";
import { CreateBookingPage } from "./pageObjects/CreateBookingPage";
import { MyBookingsPage } from "./pageObjects/MyBookingsPage";

type BookingConfiguration = { id: number };
type Booking = {
  id: number;
  version: number;
  purpose: string | null;
  privacy: "full" | "busy";
  canEdit: boolean;
  canCancel: boolean;
  canViewConfiguration: boolean;
};
type BookingPage = { docs: Booking[] };
type AccessDocument = {
  assignments: Array<{ grantee: { detail: string | null }; role: string }>;
};

const bookingFields =
  "id,version,target,canViewConfiguration,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,canCancel,createdAt,updatedAt";

/** Narrows the permanent audience row without removing it. */
async function disableAllUsersAccess(
  apiContext: APIRequestContext,
  apiKey: string,
  configurationId: number,
): Promise<void> {
  const path = `/api/v2/booking-configurations/${configurationId}/access`;
  const current = await apiContext.get(path, { headers: { apiKey } });
  expect(current.ok(), await current.text()).toBe(true);
  const etag = current.headers().etag;
  const document = (await current.json()) as {
    assignments: Array<{ grantee: { key: string }; role: string }>;
  };
  const response = await apiContext.put(path, {
    headers: { apiKey, "If-Match": etag },
    data: {
      assignments: document.assignments.map(({ grantee, role }) => ({
        granteeKey: grantee.key,
        role: grantee.key === "audience:all-users" ? "NO_ACCESS" : role,
      })),
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
}

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

async function createBooking(
  apiContext: APIRequestContext,
  apiKey: string,
  instrumentId: number,
  start: string,
  end: string,
  purpose: string,
): Promise<Booking> {
  const response = await apiContext.post("/api/v2/bookings", {
    headers: { apiKey },
    data: {
      target: { relationTo: "booking-instruments", value: instrumentId },
      start,
      end,
      purpose,
    },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<Booking>;
}

async function getAccess(
  apiContext: APIRequestContext,
  apiKey: string,
  configurationId: number,
): Promise<AccessDocument> {
  const response = await apiContext.get(`/api/v2/booking-configurations/${configurationId}/access`, {
    headers: { apiKey },
  });
  expect(response.ok(), await response.text()).toBe(true);
  return response.json() as Promise<AccessDocument>;
}

function futureDate(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

test.describe
  .serial("Booking permission release journey", { tag: tags.INVENTORY }, () => {
    test("keeps assignment, role loss, repair, and coordinated transfer coherent", async ({
      apiContext,
      appUser,
      browser,
      browserContextOptions,
      clientInventory,
      clientSysadmin,
      pageBookableItem,
    }) => {
      test.slow();
      const booker = await createDynamicUser(clientSysadmin, "ROLE_USER", "BookingBooker");
      const viewer = await createDynamicUser(clientSysadmin, "ROLE_USER", "BookingViewer");
      const incomingOwner = await createDynamicUser(clientSysadmin, "ROLE_PI", "BookingIncomingOwner");
      const repairOwner = await createDynamicUser(clientSysadmin, "ROLE_PI", "BookingUnavailableOwner");
      const repairRecipient = await createDynamicUser(clientSysadmin, "ROLE_PI", "BookingRepairOwner");
      await clientSysadmin.createGroup({
        displayName: uniqueName("e2e-booking-permissions-group"),
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: booker.username, roleInGroup: "DEFAULT" },
          { username: viewer.username, roleInGroup: "DEFAULT" },
          { username: incomingOwner.username, roleInGroup: "DEFAULT" },
          { username: repairOwner.username, roleInGroup: "DEFAULT" },
          { username: repairRecipient.username, roleInGroup: "DEFAULT" },
        ],
      });
      const bookerPurpose = uniqueName("e2e-booker-purpose");
      const ownerPurpose = uniqueName("e2e-owner-purpose");
      const date = futureDate(14);
      const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-booking-permissions") });
      const configuration = await createBookingConfiguration(apiContext, appUser.apiKey, instrument.id);

      await test.step("the owner narrows default access and grants Booker and Viewer", async () => {
        await disableAllUsersAccess(apiContext, appUser.apiKey, configuration.id);
        await pageBookableItem.openRecord(instrument.globalId);
        await pageBookableItem.openAccess();
        await expect(pageBookableItem.assignment("All users")).toContainText("No access");
        await pageBookableItem.addUser(booker.username, "E2E BookingBooker", "BOOKER");
        await pageBookableItem.addUser(viewer.username, "E2E BookingViewer", "VIEWER");
      });

      let bookerBooking: { id: number };
      await test.step("the Booker creates a booking through the live UI", async () => {
        const session = await loginInNewContext(browser, browserContextOptions, booker);
        try {
          bookerBooking = await new CreateBookingPage(session.page).create({
            globalId: instrument.globalId,
            date,
            startTime: "10:00",
            endTime: "11:00",
            purpose: bookerPurpose,
          });
        } finally {
          await session.close();
        }
      });

      const ownerBooking = await createBooking(
        apiContext,
        appUser.apiKey,
        instrument.id,
        `${date}T14:00:00Z`,
        `${date}T15:00:00Z`,
        ownerPurpose,
      );

      await test.step("the Viewer reads full event details but cannot create", async () => {
        const viewerRead = await apiContext.get(
          `/api/v2/bookings/${bookerBooking.id}?depth=1&fields[bookings]=${bookingFields}`,
          { headers: { apiKey: viewer.apiKey } },
        );
        expect(viewerRead.ok(), await viewerRead.text()).toBe(true);
        const viewed = (await viewerRead.json()) as Booking;
        expect(viewed).toMatchObject({ purpose: bookerPurpose, privacy: "full", canEdit: false });

        const rejectedCreate = await apiContext.post("/api/v2/bookings", {
          headers: { apiKey: viewer.apiKey },
          data: {
            target: { relationTo: "booking-instruments", value: instrument.id },
            start: `${date}T16:00:00Z`,
            end: `${date}T17:00:00Z`,
            purpose: "Viewer must not create this",
          },
        });
        expect(rejectedCreate.status()).toBe(403);
      });

      await test.step("role loss retains only the requester's own read-only non-navigable booking", async () => {
        await pageBookableItem.openRecord(instrument.globalId);
        await pageBookableItem.openAccess();
        await pageBookableItem.removeAssignment(booker.username, "E2E BookingBooker");

        const where = encodeURIComponent(`target==${instrument.globalId}`);
        const ownRowsResponse = await apiContext.get(
          `/api/v2/bookings?depth=1&limit=100&where=${where}&fields[bookings]=${bookingFields}`,
          { headers: { apiKey: booker.apiKey } },
        );
        expect(ownRowsResponse.ok(), await ownRowsResponse.text()).toBe(true);
        const ownRows = (await ownRowsResponse.json()) as BookingPage;
        expect(ownRows.docs.map(({ id }) => id)).toEqual([bookerBooking.id]);
        expect(ownRows.docs[0]).toMatchObject({
          purpose: bookerPurpose,
          privacy: "full",
          canEdit: false,
          canCancel: false,
          canViewConfiguration: false,
        });
        expect(ownRows.docs.some(({ id }) => id === ownerBooking.id)).toBe(false);

        const configurationRead = await apiContext.get(`/api/v2/booking-configurations/${configuration.id}`, {
          headers: { apiKey: booker.apiKey },
        });
        expect(configurationRead.status()).toBe(404);
        const subscriptionRead = await apiContext.get(
          `/api/v2/booking-configurations/${configuration.id}/calendar-subscription`,
          { headers: { apiKey: booker.apiKey } },
        );
        expect(subscriptionRead.status()).toBe(404);

        const session = await loginInNewContext(browser, browserContextOptions, booker);
        try {
          const bookingPage = new MyBookingsPage(session.page);
          await bookingPage.open();
          await expect(session.page.getByText(bookerPurpose, { exact: true })).toBeVisible();
          await expect(session.page.getByText("Read-only: you no longer have access to this item.")).toBeVisible();
          await expect(session.page.getByRole("link", { name: "View details" })).toHaveCount(0);
          await expect(session.page.getByRole("link", { name: new RegExp(instrument.globalId) })).toHaveCount(0);
        } finally {
          await session.close();
        }
      });

      await test.step("a sysadmin repairs a configuration with no effective owner", async () => {
        const repairInventory = new InventoryClient(apiContext, repairOwner.apiKey);
        const repairInstrument = await repairInventory.createInstrument({ name: uniqueName("e2e-owner-repair") });
        const repairConfiguration = await createBookingConfiguration(
          apiContext,
          repairOwner.apiKey,
          repairInstrument.id,
        );
        await clientSysadmin.disableUser(repairOwner.id);

        const session = await loginInNewContext(browser, browserContextOptions, SYSADMIN);
        try {
          const repairPage = new BookingPermissionsPage(session.page);
          await repairPage.openRecord(repairInstrument.globalId);
          await repairPage.openAccess();
          await repairPage.addUser(repairRecipient.username, "E2E BookingRepairOwner", "OWNER");
        } finally {
          await session.close();
        }

        const repaired = await getAccess(apiContext, repairRecipient.apiKey, repairConfiguration.id);
        expect(repaired.assignments).toContainEqual(
          expect.objectContaining({
            grantee: expect.objectContaining({ detail: repairRecipient.username }),
            role: "OWNER",
          }),
        );
      });

      await test.step("the optional Inventory transfer changes both ownership models", async () => {
        const transfer = await apiContext.post("/api/inventory/v1/bulk", {
          headers: { apiKey: appUser.apiKey },
          data: {
            operationType: "CHANGE_OWNER",
            rollbackOnError: true,
            transferBookingConfigurationOwnership: true,
            records: [
              {
                type: "INSTRUMENT",
                id: instrument.id,
                owner: { username: incomingOwner.username },
              },
            ],
          },
        });
        expect(transfer.ok(), await transfer.text()).toBe(true);
        expect(await transfer.json()).toMatchObject({ status: "COMPLETED", successCount: 1, errorCount: 0 });

        const transferredInstrument = await apiContext.get(`/api/inventory/v1/instruments/${instrument.id}`, {
          headers: { apiKey: incomingOwner.apiKey },
        });
        expect(transferredInstrument.ok(), await transferredInstrument.text()).toBe(true);
        expect(await transferredInstrument.json()).toMatchObject({
          owner: { username: incomingOwner.username },
        });
        const transferredAccess = await getAccess(apiContext, incomingOwner.apiKey, configuration.id);
        expect(transferredAccess.assignments).toContainEqual(
          expect.objectContaining({
            grantee: expect.objectContaining({ detail: incomingOwner.username }),
            role: "OWNER",
          }),
        );
        expect(
          transferredAccess.assignments.some(
            (assignment) => assignment.grantee.detail === appUser.username && assignment.role === "OWNER",
          ),
        ).toBe(false);
      });
    });
  });
