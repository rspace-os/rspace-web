import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import { bookingDisplayPreferencesQueryKey } from "@/modules/booking/domain/bookingDisplayPreferences";
import { currentUser } from "@/modules/booking/pages/calendar/calendarFixtures";
import BookingPreferencesPage from "../BookingPreferencesPage";
import { customNewYorkBookingPreferences, inheritedBrowserBookingPreferences } from "../bookingPreferencesFixtures";

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <BookingPreferencesPage />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}

describe("BookingPreferencesPage", () => {
  beforeEach(() => {
    server.use(
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/users/me/booking-notification-preferences", () =>
        HttpResponse.json({
          autoSubscribeOwnedItems: false,
          notifyOnCreated: true,
          notifyOnCancelled: true,
          emailDelivery: false,
        }),
      ),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) =>
        HttpResponse.json({ ...((await request.json()) as object), emailDelivery: false }),
      ),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => HttpResponse.json({ updatedCount: 0 })),
      http.get("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
      ),
    );
  });

  it("keeps a local draft when preferences are refreshed in the background", async () => {
    const user = userEvent.setup();
    let document = inheritedBrowserBookingPreferences;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(document)),
    );
    const { queryClient } = renderPage();
    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    await user.clear(start);
    await user.type(start, "10:00");

    document = customNewYorkBookingPreferences;
    await queryClient.refetchQueries({ queryKey: bookingDisplayPreferencesQueryKey });

    expect(start).toHaveValue("10:00");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeEnabled();
  });

  it("saves one complete preference and replaces the shared query cache", async () => {
    let body: unknown;
    let writes = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        writes += 1;
        body = await request.json();
        return HttpResponse.json({
          ...inheritedBrowserBookingPreferences,
          ...(body as object),
          overridden: true,
        });
      }),
    );
    const user = userEvent.setup();
    const { queryClient, container } = renderPage();

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
    expect(
      screen.queryByRole("combobox", { name: "booking:preferences.timezone.customLabel" }),
    ).not.toBeInTheDocument();
    await user.clear(start);
    await user.type(start, "09:00");
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const saveButton = screen.getByRole("button", { name: "booking:preferences.actions.saved" });
    await waitFor(() => expect(saveButton).toHaveClass("bg-emerald-600"));
    expect(saveButton).toBeDisabled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    await user.click(saveButton);
    expect(writes).toBe(1);
    expect(body).toEqual({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "INSTITUTION",
      customTimezone: null,
      timeFormat: "AUTOMATIC",
    });
    await waitFor(() =>
      expect(queryClient.getQueryData(bookingDisplayPreferencesQueryKey)).toMatchObject({
        availabilityWindowStart: "09:00",
        timezoneMode: "INSTITUTION",
        overridden: true,
      }),
    );
    await expectAccessible(container);
  });

  it("saves an explicit time format, keeping the other preferences", async () => {
    let body: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(customNewYorkBookingPreferences)),
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...customNewYorkBookingPreferences, ...(body as object), overridden: true });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderPage();

    // A document without timeFormat, as older servers and saved overrides return, reads as Automatic.
    const automatic = await screen.findByRole("radio", { name: "booking:preferences.timeFormat.automatic" });
    expect(automatic).toBeChecked();
    expect(screen.getByRole("group", { name: "booking:preferences.timeFormat.legend" })).toHaveAccessibleDescription(
      "booking:preferences.timeFormat.description",
    );
    const twentyFourHour = screen.getByRole("radio", { name: "booking:preferences.timeFormat.twentyFourHour" });
    await user.click(twentyFourHour);
    expect(twentyFourHour).toBeChecked();
    expect(automatic).not.toBeChecked();
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    await waitFor(() =>
      expect(queryClient.getQueryData(bookingDisplayPreferencesQueryKey)).toMatchObject({ timeFormat: "H24" }),
    );
    expect(body).toEqual({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "17:00",
      timezoneMode: "CUSTOM",
      customTimezone: "America/New_York",
      timeFormat: "H24",
    });
    expect(screen.getByRole("radio", { name: "booking:preferences.timeFormat.twentyFourHour" })).toBeChecked();
  });

  it("shows the custom timezone field only in Custom mode and restores the last custom zone", async () => {
    const bodies: unknown[] = [];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(customNewYorkBookingPreferences)),
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        const body = await request.json();
        bodies.push(body);
        return HttpResponse.json({ ...customNewYorkBookingPreferences, ...(body as object), overridden: true });
      }),
    );
    const user = userEvent.setup();
    renderPage();
    const customName = { name: "booking:preferences.timezone.customLabel" };

    expect(await screen.findByRole("combobox", customName)).toHaveValue("America/New_York");
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" }));
    expect(screen.queryByRole("combobox", customName)).not.toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.custom" }));
    const custom = screen.getByRole("combobox", customName);
    expect(custom).toHaveValue("America/New_York");
    expect(custom).toBeEnabled();

    // An invalid custom zone blocks Save; leaving Custom mode clears the error with the hidden field.
    await user.clear(custom);
    await user.type(custom, "Not/A-Timezone");
    expect(custom).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toMatchObject({ timezoneMode: "INSTITUTION", customTimezone: null });
  });

  it("uses 00:00 for end of day while storing 24:00", async () => {
    let body: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () =>
        HttpResponse.json({ ...inheritedBrowserBookingPreferences, availabilityWindowEnd: "24:00" }),
      ),
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          ...inheritedBrowserBookingPreferences,
          ...(body as object),
          overridden: true,
        });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const end = await screen.findByLabelText("booking:preferences.availabilityWindow.end");
    expect(end).toHaveValue("00:00");
    expect(end).toHaveAccessibleDescription("booking:preferences.availabilityWindow.endOfDay");
    expect(
      screen.queryByRole("checkbox", { name: "booking:preferences.availabilityWindow.endOfDay" }),
    ).not.toBeInTheDocument();

    const start = screen.getByLabelText("booking:preferences.availabilityWindow.start");
    await user.clear(start);
    await user.type(start, "09:00");
    await user.clear(end);
    await user.type(end, "00:00");
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const saveButton = screen.getByRole("button", { name: "booking:preferences.actions.saved" });
    await waitFor(() => expect(saveButton).toHaveClass("bg-emerald-600"));
    expect(saveButton).toBeDisabled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(body).toMatchObject({ availabilityWindowEnd: "24:00" });
  });

  it("resets an override and immediately caches the current global document", async () => {
    let reads = 0;
    let deletes = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => {
        reads += 1;
        return HttpResponse.json(reads === 1 ? customNewYorkBookingPreferences : inheritedBrowserBookingPreferences);
      }),
      http.delete("/api/v2/users/me/booking-preferences", () => {
        deletes += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    const { queryClient } = renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.actions.reset" }));

    expect(await screen.findByRole("status")).toHaveTextContent("booking:preferences.resetComplete");
    expect(deletes).toBe(1);
    expect(reads).toBe(2);
    expect(queryClient.getQueryData(bookingDisplayPreferencesQueryKey)).toEqual({
      ...inheritedBrowserBookingPreferences,
      timeFormat: "AUTOMATIC",
    });
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
  });

  it("keeps invalid windows client-side and reports network failures", async () => {
    let writes = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-preferences", () => {
        writes += 1;
        return HttpResponse.json({ detail: "failure" }, { status: 503 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    await user.clear(start);
    await user.type(start, "19:00");
    expect(screen.getByRole("alert")).toHaveTextContent("booking:preferences.errors.invalid");
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    expect(writes).toBe(0);

    await user.clear(start);
    await user.type(start, "09:00");
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("booking:preferences.errors.save");
    expect(writes).toBe(1);
  });

  it("creates a user-wide calendar subscription from Booking preferences", async () => {
    let creates = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.post("/api/v2/users/me/booking-calendar-subscription", ({ request }) => {
        creates += 1;
        expect(request.headers.get("If-Match")).toBeNull();
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=user",
          },
          { status: 201, headers: { ETag: '"subscription-0"' } },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }));

    expect(creates).toBe(1);
    expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=user",
    );
    expect(screen.getByRole("link", { name: "booking:preferences.calendarSubscription.google" })).toBeVisible();
  });

  it("shows the existing link without an error when Create finds one already made elsewhere", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      // The page loaded before another tab created the link; the create returns that link unchanged.
      http.post("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=existing",
          },
          { status: 200, headers: { ETag: '"subscription-0"' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }));

    expect(await screen.findByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=existing",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("replaces the private link only after the user confirms", async () => {
    const rotations: (string | null)[] = [];
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.get("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=first",
          },
          { headers: { ETag: '"subscription-0"' } },
        ),
      ),
      http.post("/api/v2/users/me/booking-calendar-subscription/rotate", ({ request }) => {
        rotations.push(request.headers.get("If-Match"));
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T13:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=second",
          },
          { headers: { ETag: '"subscription-1"' } },
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.cancel" }),
    );
    expect(rotations).toEqual([]);
    expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
      "https://example.test/public/booking/calendars/feed.ics?token=first",
    );

    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );

    await waitFor(() =>
      expect(screen.getByLabelText("booking:preferences.calendarSubscription.copyPrompt")).toHaveValue(
        "https://example.test/public/booking/calendars/feed.ics?token=second",
      ),
    );
    expect(rotations).toEqual(['"subscription-0"']);
  });

  it("shows only the user-wide calendar link on preferences", async () => {
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
    );
    renderPage();

    expect(
      await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("region", { name: "booking:preferences.calendarSubscription.itemLinks.title" }),
    ).not.toBeInTheDocument();
  });

  it("keeps notification choices after a failed save and allows retrying", async () => {
    const user = userEvent.setup();
    let attempts = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json({}, { status: 503 })
          : HttpResponse.json({ ...((await request.json()) as object), emailDelivery: false });
      }),
    );
    renderPage();
    const on = await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" });
    await user.click(on);
    const save = screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" });
    await user.click(save);
    expect(await screen.findByText("booking:notificationSubscriptions.preferences.saveError")).toBeVisible();
    expect(on).toBeChecked();
    expect(save).toBeEnabled();
    await user.click(save);
    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(attempts).toBe(2);
  });

  it("saves the owner auto-subscribe default and unsubscribes from existing instruments", async () => {
    const user = userEvent.setup();
    let saved: unknown;
    let deleted = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ ...(saved as object), emailDelivery: false });
      }),
      http.delete("/api/v2/users/me/booking-notification-subscriptions", () => {
        deleted += 1;
        return HttpResponse.json({ updatedCount: 3 });
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));
    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveClass("bg-emerald-600");
    expect(savedButton).toBeDisabled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(saved).toEqual({ autoSubscribeOwnedItems: true, notifyOnCreated: true, notifyOnCancelled: true });

    await user.click(
      screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.unsubscribeAll" }),
    );
    expect(await screen.findByText("booking:notificationSubscriptions.preferences.unsubscribed")).toBeVisible();
    expect(deleted).toBe(1);
    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" })).toBeChecked();
    expect(
      screen.queryByRole("link", { name: "booking:notificationSubscriptions.preferences.manageSubscriptions" }),
    ).not.toBeInTheDocument();
  });
  it("saves the booking event toggles with the complete preference body and shows email delivery read-only", async () => {
    const user = userEvent.setup();
    let saved: unknown;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.put("/api/v2/users/me/booking-notification-preferences", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ ...(saved as object), emailDelivery: false });
      }),
    );
    const { container } = renderPage();

    const created = await screen.findByRole("checkbox", {
      name: "booking:notificationSubscriptions.preferences.events.created",
    });
    const cancelled = screen.getByRole("checkbox", {
      name: "booking:notificationSubscriptions.preferences.events.cancelled",
    });
    expect(created).toBeChecked();
    expect(cancelled).toBeChecked();
    expect(screen.getByText(/booking:notificationSubscriptions\.preferences\.emailDelivery\.off/)).toBeVisible();
    expect(
      screen.getByRole("link", { name: "booking:notificationSubscriptions.preferences.emailDelivery.change" }),
    ).toHaveAttribute("href", "/userform#prefContainer");
    expect(screen.queryByRole("checkbox", { name: /emailDelivery/ })).not.toBeInTheDocument();

    await user.click(cancelled);
    expect(cancelled).not.toBeChecked();
    await user.click(screen.getByRole("button", { name: "booking:notificationSubscriptions.preferences.save" }));

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeDisabled();
    expect(saved).toEqual({ autoSubscribeOwnedItems: false, notifyOnCreated: true, notifyOnCancelled: false });
    expect(cancelled).not.toBeChecked();
    await expectAccessible(container);
  });
});
