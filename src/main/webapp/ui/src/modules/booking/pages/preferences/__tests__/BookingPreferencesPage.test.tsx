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
      http.get("/api/v2/users/me/booking-calendar-subscription", () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
      ),
    );
  });

  it("saves one complete preference and replaces the shared query cache", async () => {
    let body: unknown;
    server.use(
      oauthTokenHandler(),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
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
    const { queryClient, container } = renderPage();

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
    expect(screen.getByRole("combobox", { name: "booking:preferences.timezone.customLabel" })).toBeDisabled();
    await user.clear(start);
    await user.type(start, "09:00");
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    expect(await screen.findByRole("status")).toHaveTextContent("booking:preferences.saved");
    expect(body).toEqual({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "INSTITUTION",
      customTimezone: null,
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

  it("resets an override and immediately caches the current global document", async () => {
    let reads = 0;
    let deletes = 0;
    server.use(
      oauthTokenHandler(),
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
    expect(queryClient.getQueryData(bookingDisplayPreferencesQueryKey)).toEqual(inheritedBrowserBookingPreferences);
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
  });

  it("keeps invalid windows client-side and reports network failures", async () => {
    let writes = 0;
    server.use(
      oauthTokenHandler(),
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
      oauthTokenHandler(),
      http.get("/api/v2/users/me/booking-preferences", () => HttpResponse.json(inheritedBrowserBookingPreferences)),
      http.post("/api/v2/users/me/booking-calendar-subscription", ({ request }) => {
        creates += 1;
        expect(request.headers.get("If-Match")).toBe('"inactive"');
        return HttpResponse.json(
          {
            active: true,
            updatedAt: "2026-08-30T12:00:00.000Z",
            subscriptionUrl: "https://example.test/public/booking/calendars/feed.ics?token=user",
          },
          { headers: { ETag: '"subscription-0"' } },
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
});
