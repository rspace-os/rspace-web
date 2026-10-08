import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { currentUser } from "@/modules/booking/pages/calendar/calendarFixtures";
import { createBookingSettingsRoute } from "../routes";

const settings = {
  slotGranularityMinutes: 5,
  openingStart: "08:00",
  openingEnd: "18:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 3,
  bufferAfterMinutes: 7,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER" as const,
  customTimezone: null,
  institutionTimezone: "UTC",
  defaultSharedWith: "ALL_USERS" as const,
  selectedAccessGrantees: [],
  configurationVersion: 0,
  state: "ACTIVE",
};

let adminSettingsRequests = 0;

beforeEach(() => {
  adminSettingsRequests = 0;
  server.use(oauthTokenHandler(true));
});

function answerAdminSettings(respond: () => Response) {
  server.use(
    http.get("/api/v2/booking-settings/admin", () => {
      adminSettingsRequests += 1;
      return respond();
    }),
  );
}

// The application's QueryClient retries three times with backoff; a zero delay keeps retries observable but quick.
function renderSettingsRoute(hasSysAdminRole: boolean) {
  server.use(http.get("/api/v2/users/me", () => HttpResponse.json({ ...currentUser, hasSysAdminRole })));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
  const root = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({ getParentRoute: () => root, path: "/booking", component: Outlet });
  const router = createRouter({
    routeTree: root.addChildren([
      bookingRoute.addChildren([
        createBookingSettingsRoute(bookingRoute),
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/preferences",
          component: () => <main>{"Preferences destination"}</main>,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: ["/booking/config/settings"] }),
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("Booking settings route", () => {
  it("tells a non-sysadmin the page is restricted without asking the sysadmin-only API", async () => {
    const user = userEvent.setup();
    answerAdminSettings(() => new HttpResponse(null, { status: 403 }));
    const { container } = renderSettingsRoute(false);

    expect(await screen.findByText("booking:settings.notPermitted.title")).toBeVisible();
    expect(screen.getByText("booking:settings.notPermitted.description")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:settings.actions.save" })).not.toBeInTheDocument();
    expect(adminSettingsRequests).toBe(0);
    await expectAccessible(container);

    await user.click(screen.getByRole("link", { name: "booking:settings.notPermitted.action" }));
    expect(await screen.findByText("Preferences destination")).toBeVisible();
  });

  it("loads the institution settings for a sysadmin", async () => {
    answerAdminSettings(() => HttpResponse.json(settings));
    renderSettingsRoute(true);

    expect(await screen.findByRole("button", { name: "booking:settings.actions.save" })).toBeInTheDocument();
    expect(screen.queryByText("booking:settings.notPermitted.title")).not.toBeInTheDocument();
    expect(adminSettingsRequests).toBe(1);
  });

  it("shows the restricted state in place, without retrying, when the API still answers 403", async () => {
    answerAdminSettings(() => new HttpResponse(null, { status: 403 }));
    renderSettingsRoute(true);

    expect(await screen.findByText("booking:settings.notPermitted.title")).toBeVisible();
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(adminSettingsRequests).toBe(1);
  });

  it("retries a server error, then offers a working retry inside the page", async () => {
    const user = userEvent.setup();
    let failing = true;
    answerAdminSettings(() => (failing ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(settings)));
    renderSettingsRoute(true);

    expect(await screen.findByText("booking:settings.unavailable.title")).toBeVisible();
    expect(adminSettingsRequests).toBe(4);
    failing = false;
    await user.click(screen.getByRole("button", { name: "common:actions.retry" }));

    expect(await screen.findByRole("button", { name: "booking:settings.actions.save" })).toBeInTheDocument();
  });

  it("shows the default custom timezone field only in Custom mode", async () => {
    const user = userEvent.setup();
    answerAdminSettings(() => HttpResponse.json({ ...settings, timezoneMode: "CUSTOM", customTimezone: "Asia/Tokyo" }));
    renderSettingsRoute(true);
    const customName = { name: "booking:preferences.timezone.customLabel" };

    expect(await screen.findByRole("combobox", customName)).toHaveValue("Asia/Tokyo");
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    await waitFor(() => expect(screen.queryByRole("combobox", customName)).not.toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.custom" }));
    expect(screen.getByRole("combobox", customName)).toHaveValue("Asia/Tokyo");
  });
});
