import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { createAddBookableItemRoute } from "../routes";

const confocal = { id: 123, name: "Confocal microscope", globalId: "IN123", deleted: false };
const settings = {
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER",
  customTimezone: null,
  institutionTimezone: "Etc/UTC",
};

function collectionPage(docs: readonly unknown[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 20,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}

function targetsHandler(onRequest: (url: URL) => void = () => undefined, targets: readonly unknown[] = [confocal]) {
  return http.get("/api/v2/booking-configuration-targets", ({ request }) => {
    onRequest(new URL(request.url));
    return HttpResponse.json(targets);
  });
}

function availabilityHandler(
  configurations: () => readonly unknown[] = () => [],
  onRequest: (url: URL) => void = () => undefined,
) {
  return http.get("/api/v2/booking-configurations", ({ request }) => {
    onRequest(new URL(request.url));
    return HttpResponse.json(collectionPage(configurations()));
  });
}

function DestinationPage() {
  const { t } = useTranslation("booking");
  return <h1>{t("bookableItems.plural")}</h1>;
}

function ExistingConfigurationPage() {
  const { t } = useTranslation("booking");
  return <h1>{t("bookableItemDetails.title")}</h1>;
}

function renderPage(path = "/booking/bookable-items/add", defaults: object = settings) {
  server.use(http.get("/api/v2/booking-settings", () => HttpResponse.json(defaults)));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: Outlet });
  const bookingRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/booking",
    component: Outlet,
  });
  const destinationRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/bookable-items",
    component: DestinationPage,
  });
  const existingConfigurationRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId",
    component: ExistingConfigurationPage,
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([
        destinationRoute,
        existingConfigurationRoute,
        createAddBookableItemRoute(bookingRoute),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

async function completeForm(user: ReturnType<typeof userEvent.setup>) {
  const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
  await user.type(search, "Conf");
  await user.click(await screen.findByRole("option", { name: /Confocal microscope/ }));
  await screen.findByRole("button", { name: "common:relationshipPicker.clear" });
}

describe("AddBookableItemPage", () => {
  it("initializes the route target once and lets the user clear it", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
    );
    renderPage("/booking/bookable-items/add?target=IN123");

    expect(await screen.findByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeVisible();
    await user.click(await screen.findByRole("button", { name: "common:relationshipPicker.clear" }));
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "booking:bookableItems.targetSearch.label" })).toHaveValue("");
  });

  it("lists eligible instruments on open and only sends a query of at least two characters", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler((url) => requests.push(url)),
    );
    renderPage();

    const picker = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.openOptions" }));
    expect(await screen.findByRole("option", { name: /Confocal microscope/ })).toBeVisible();
    expect(screen.queryByText("common:relationshipPicker.enterSearchTerm")).not.toBeInTheDocument();
    expect(requests[0]?.searchParams.has("query")).toBe(false);
    expect(requests[0]?.searchParams.get("limit")).toBe("20");

    await user.type(picker, "C");
    // One character is not searched, so the list asks for another rather than reporting no matches.
    expect(await screen.findByText("common:relationshipPicker.searchTooShort")).toBeVisible();
    expect(screen.queryByText("common:relationshipPicker.empty")).not.toBeInTheDocument();
    await user.type(picker, "o");
    await waitFor(() => expect(requests.at(-1)?.searchParams.get("query")).toBe("Co"));
    expect(requests.some((url) => url.searchParams.get("query") === "C")).toBe(false);
  });

  it("explains how to make an instrument eligible when none can be configured", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(undefined, []),
    );
    renderPage();

    await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.openOptions" }));
    expect(await screen.findByText("booking:bookableItems.targetSearch.noEligible")).toBeVisible();
  });

  it("treats a blank relationship control as an empty selection", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(),
    );
    renderPage();

    await completeForm(user);
    await user.click(await screen.findByRole("button", { name: "common:relationshipPicker.clear" }));

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
  });

  it("submits the selected booking configuration and returns to the list", async () => {
    const user = userEvent.setup();
    let requestBody: unknown;
    let authorization: string | null = null;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        requestBody = await request.json();
        authorization = request.headers.get("Authorization");
        return HttpResponse.json({ id: 7 }, { status: 201 });
      }),
    );
    const { container } = renderPage();

    expect(await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" })).toBeVisible();
    expect(screen.queryByRole("combobox", { name: "booking:bookableItems.fields.timezone" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
    await completeForm(user);

    await expectAccessible(container);

    const timezone = screen.getByRole("combobox", { name: "booking:bookableItems.fields.timezone" });
    expect(timezone).toHaveValue("Etc/UTC");
    await user.clear(timezone);
    await user.type(timezone, "Europe/Berl");
    await user.click(await screen.findByRole("option", { name: "Europe/Berlin" }));

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(authorization).toBe("Bearer test-token");
    expect(requestBody).toEqual({
      target: { relationTo: "booking-instruments", value: 123 },
      enabled: true,
      timezone: "Europe/Berlin",
      slotGranularityMinutes: 5,
      openingStart: "00:00",
      openingEnd: "24:00",
      openDays: [1, 2, 3, 4, 5, 6, 7],
      openingExceptions: [],
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    });
  });

  it("copies default weekday exceptions and blocks submission while a day edit is pending", async () => {
    const user = userEvent.setup();
    const requestBodies: unknown[] = [];
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        requestBodies.push(await request.json());
        return HttpResponse.json({ id: 7 }, { status: 201 });
      }),
    );
    renderPage(undefined, {
      ...settings,
      openingStart: "09:00",
      openingEnd: "17:00",
      openingExceptions: [
        { dayOfWeek: 5, start: "09:00", end: "14:00" },
        { dayOfWeek: 6, start: "10:00", end: "16:00" },
      ],
    });
    await completeForm(user);

    const list = screen.getByRole("list", { name: "booking:settings.openingHours.hoursByDay" });
    const row = (day: string) =>
      within(list)
        .getAllByRole("listitem")
        .find((item) => within(item).queryByText(day, { exact: true })) as HTMLElement;
    expect(within(row("Saturday")).getByText("10:00\u201316:00")).toHaveClass("font-bold");
    const submit = screen.getByRole("button", { name: "booking:bookableItems.actions.submit" });

    await user.click(within(row("Monday")).getByRole("button", { name: "booking:settings.openingHours.editDay" }));
    expect(submit).toBeDisabled();
    await user.click(within(row("Monday")).getByRole("button", { name: "booking:settings.openingHours.discardDay" }));
    expect(submit).toBeEnabled();

    await user.click(screen.getByRole("checkbox", { name: "Friday" }));
    await user.click(submit);

    await waitFor(() => expect(requestBodies).toHaveLength(1));
    expect(requestBodies[0]).toMatchObject({
      openingStart: "09:00",
      openingEnd: "17:00",
      openDays: [1, 2, 3, 4, 6, 7],
      openingExceptions: [{ dayOfWeek: 6, start: "10:00", end: "16:00" }],
    });
  });

  it("keeps the completed form available when creation fails", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", () => new HttpResponse(null, { status: 500 })),
    );
    renderPage();
    await completeForm(user);

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.addError");
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeEnabled();
    expect(screen.getByRole("heading", { name: "booking:bookableItems.addTitle" })).toBeVisible();
  });

  it("searches only eligible instruments and selects a result", async () => {
    const user = userEvent.setup();
    const targetRequests: URL[] = [];
    const spare = { id: 456, name: "Spare confocal microscope", globalId: "IN456", deleted: false };
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler((url) => targetRequests.push(url), [spare]),
    );
    const { container } = renderPage();
    const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");

    expect(screen.queryByRole("option", { name: /Confocal microscope \(IN123\)/ })).not.toBeInTheDocument();
    await user.click(await screen.findByRole("option", { name: /Spare confocal microscope/ }));
    expect(await screen.findByRole("button", { name: "common:relationshipPicker.clear" })).toBeVisible();

    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeVisible();
    expect(targetRequests.some((request) => request.searchParams.get("query") === "Conf")).toBe(true);

    await expectAccessible(container);
  });

  it("shows the existing configuration link when creation loses a race", async () => {
    const user = userEvent.setup();
    let conflict = false;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(() =>
        conflict ? [{ id: 9, target: { relationTo: "booking-instruments", value: 123, globalId: "IN123" } }] : [],
      ),
      http.post("/api/v2/booking-configurations", () => {
        conflict = true;
        return HttpResponse.json(
          { status: 409, code: "errors.api.v2.bookingConfiguration.target.conflict" },
          { status: 409 },
        );
      }),
    );
    renderPage();
    await completeForm(user);

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("booking:bookableItems.availability.alreadyConfigured");
    expect(
      within(alert).getByRole("link", { name: "booking:bookableItems.availability.viewExisting" }),
    ).toHaveAttribute("href", "/booking/bookable-items/IN123");
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
  });

  it("shows an error when eligible instruments cannot be searched", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      http.get("/api/v2/booking-configuration-targets", () => new HttpResponse(null, { status: 500 })),
    );
    renderPage();
    const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");

    expect(await screen.findByText("common:relationshipPicker.failed")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
  });
});
