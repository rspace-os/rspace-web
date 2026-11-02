import { notifyManager, QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense, useSyncExternalStore } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import {
  type BookingDisplayPreferencesDocument,
  bookingDisplayPreferencesQueryKey,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import BookingPreferencesPage from "../BookingPreferencesPage";
import { customNewYorkBookingPreferences, inheritedBrowserBookingPreferences } from "../bookingPreferencesFixtures";
import { renderPage, useDefaultBookingPreferencesHandlers } from "./bookingPreferencesPageTestUtils";

function BookingPreferencesQueryProbe() {
  const queryClient = useQueryClient();
  const document = useSyncExternalStore(
    (onStoreChange) => queryClient.getQueryCache().subscribe(notifyManager.batchCalls(onStoreChange)),
    () => queryClient.getQueryData<BookingDisplayPreferencesDocument>(bookingDisplayPreferencesQueryKey),
    () => queryClient.getQueryData<BookingDisplayPreferencesDocument>(bookingDisplayPreferencesQueryKey),
  );
  const end = document?.availabilityWindowEnd ?? "pending";
  return (
    <p role="status" aria-label={`Cached server end ${end}`}>
      {end}
    </p>
  );
}

describe("BookingPreferencesPage", () => {
  beforeEach(() => useDefaultBookingPreferencesHandlers());

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

  it("adopts an unchanged inherited document after resetting a dirty draft", async () => {
    let reads = 0;
    let deletes = 0;
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => {
        reads += 1;
        return HttpResponse.json(inheritedBrowserBookingPreferences);
      }),
      http.delete("/api/v2/users/me/booking-preferences", () => {
        deletes += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    await user.clear(start);
    await user.type(start, "09:00");
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.reset" }));

    expect(await screen.findByRole("status")).toHaveTextContent("booking:preferences.resetComplete");
    expect(deletes).toBe(1);
    expect(reads).toBe(2);
    expect(start).toHaveValue("08:00");
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "booking:preferences.actions.reset" })).toBeDisabled();
  });

  it("keeps the dirty draft and navigation guard through matching background data and adopts Save", async () => {
    let reads = 0;
    let submittedBody: unknown;
    const changedPreferences = { ...inheritedBrowserBookingPreferences, availabilityWindowEnd: "20:00" };
    const matchingPreferences = {
      ...inheritedBrowserBookingPreferences,
      availabilityWindowStart: "09:00",
      timezoneMode: "INSTITUTION",
      overridden: true,
    };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => {
        reads += 1;
        if (reads === 1) return HttpResponse.json(inheritedBrowserBookingPreferences);
        return HttpResponse.json(reads === 2 ? changedPreferences : matchingPreferences);
      }),
      http.put("/api/v2/users/me/booking-preferences", async ({ request }) => {
        submittedBody = await request.json();
        return HttpResponse.json(matchingPreferences);
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const destinationLinkLabel = "Go elsewhere";
    const destinationPageLabel = "Elsewhere page";
    const rootRoute = createRootRoute({
      component: () => (
        <>
          <Link to="/about" resetScroll={false}>
            {destinationLinkLabel}
          </Link>
          <Outlet />
        </>
      ),
    });
    const preferencesRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: "/",
      component: BookingPreferencesPage,
    });
    const destinationRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: "/about",
      component: () => <p>{destinationPageLabel}</p>,
    });
    const router = createRouter({
      routeTree: rootRoute.addChildren([preferencesRoute, destinationRoute]),
      history: createMemoryHistory({ initialEntries: ["/"] }),
    });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <BookingPreferencesQueryProbe />
      </QueryClientProvider>,
    );

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    const end = screen.getByLabelText("booking:preferences.availabilityWindow.end");
    await user.clear(start);
    await user.type(start, "09:00");
    await user.click(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" }));
    const save = screen.getByRole("button", { name: "booking:preferences.actions.save" });
    expect(save).toBeEnabled();

    await act(async () => {
      await queryClient.refetchQueries({ queryKey: bookingDisplayPreferencesQueryKey, type: "active" });
    });
    expect(reads).toBe(2);
    expect(await screen.findByRole("status", { name: "Cached server end 20:00" })).toHaveTextContent("20:00");
    expect(start).toHaveValue("09:00");
    expect(end).toHaveValue("18:00");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" })).toBeChecked();
    expect(save).toBeEnabled();

    await act(async () => {
      await queryClient.refetchQueries({ queryKey: bookingDisplayPreferencesQueryKey, type: "active" });
    });
    expect(reads).toBe(3);
    expect(await screen.findByRole("status", { name: "Cached server end 18:00" })).toHaveTextContent("18:00");
    expect(start).toHaveValue("09:00");
    expect(end).toHaveValue("18:00");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.institution" })).toBeChecked();
    expect(save).toBeEnabled();

    await user.click(screen.getByRole("link", { name: destinationLinkLabel }));
    expect(await screen.findByRole("alertdialog")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));
    expect(start).toBeInTheDocument();
    expect(save).toBeEnabled();

    await user.click(save);

    expect(await screen.findByRole("button", { name: "booking:preferences.actions.saved" })).toBeInTheDocument();
    expect(submittedBody).toEqual({
      availabilityWindowStart: "09:00",
      availabilityWindowEnd: "18:00",
      timezoneMode: "INSTITUTION",
      customTimezone: null,
      timeFormat: "AUTOMATIC",
    });
    expect(start).toHaveValue("09:00");
    expect(end).toHaveValue("18:00");
    expect(save).toBeDisabled();
    expect(queryClient.getQueryData(bookingDisplayPreferencesQueryKey)).toEqual({
      ...matchingPreferences,
      timeFormat: "AUTOMATIC",
    });

    await user.click(screen.getByRole("link", { name: destinationLinkLabel }));
    expect(await screen.findByText(destinationPageLabel)).toBeInTheDocument();
  });

  it("adopts a changed background response when the draft is clean", async () => {
    let reads = 0;
    const changedPreferences = { ...inheritedBrowserBookingPreferences, availabilityWindowEnd: "20:00" };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => {
        reads += 1;
        return HttpResponse.json(reads === 1 ? inheritedBrowserBookingPreferences : changedPreferences);
      }),
    );
    const { queryClient } = renderPage();

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    const end = screen.getByLabelText("booking:preferences.availabilityWindow.end");
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: bookingDisplayPreferencesQueryKey, type: "active" });
    });

    expect(reads).toBe(2);
    await waitFor(() => expect(end).toHaveValue("20:00"));
    expect(start).toHaveValue("08:00");
    expect(screen.getByRole("radio", { name: "booking:preferences.timezone.browser" })).toBeChecked();
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeDisabled();
  });

  it("adopts the latest background response when edits return to the original baseline", async () => {
    let reads = 0;
    const changedPreferences = { ...inheritedBrowserBookingPreferences, availabilityWindowEnd: "20:00" };
    server.use(
      oauthTokenHandler(true),
      http.get("/api/v2/users/me/booking-preferences", () => {
        reads += 1;
        return HttpResponse.json(reads === 1 ? inheritedBrowserBookingPreferences : changedPreferences);
      }),
    );
    const user = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <BookingPreferencesPage />
          <BookingPreferencesQueryProbe />
        </Suspense>
      </QueryClientProvider>,
    );

    const start = await screen.findByLabelText("booking:preferences.availabilityWindow.start");
    const end = screen.getByLabelText("booking:preferences.availabilityWindow.end");
    const save = screen.getByRole("button", { name: "booking:preferences.actions.save" });
    await user.clear(start);
    await user.type(start, "09:00");
    expect(save).toBeEnabled();

    await act(async () => {
      await queryClient.refetchQueries({ queryKey: bookingDisplayPreferencesQueryKey, type: "active" });
    });
    expect(reads).toBe(2);
    expect(await screen.findByRole("status", { name: "Cached server end 20:00" })).toHaveTextContent("20:00");
    expect(start).toHaveValue("09:00");
    expect(end).toHaveValue("18:00");
    expect(save).toBeEnabled();

    await user.clear(start);
    await user.type(start, "08:00");

    await waitFor(() => expect(end).toHaveValue("20:00"));
    expect(start).toHaveValue("08:00");
    expect(save).toBeDisabled();
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
    expect(start).toHaveValue("09:00");
    expect(screen.getByRole("button", { name: "booking:preferences.actions.save" })).toBeEnabled();
  });
});
