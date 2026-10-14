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
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import AddBookableItemPage from "@/modules/booking/pages/bookable-items/AddBookableItemPage";
import { addBookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { collectionPage } from "../mocks/bookableItemsMocks";

const confocal = { id: 123, name: "Confocal microscope", globalId: "IN123", deleted: false };
const settings = {
  slotGranularityMinutes: 5,
  openingHours: [
    { dayOfWeek: 1, start: "08:00", end: "17:00" },
    { dayOfWeek: 2, start: "10:00", end: "16:00" },
  ],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  availabilityWindowStart: "08:00",
  availabilityWindowEnd: "18:00",
  timezoneMode: "BROWSER",
  customTimezone: null,
  institutionTimezone: "UTC",
};

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

function renderPage(initialEntry = "/booking/bookable-items/add") {
  server.use(http.get("/api/v2/booking-settings", () => HttpResponse.json(settings)));
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
        createRoute({
          getParentRoute: () => bookingRoute,
          path: "/bookable-items/add/",
          validateSearch: addBookableItemSearch,
          component: AddBookableItemPage,
        }),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: [initialEntry] }),
  });

  const view = render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
  return { ...view, queryClient };
}

async function completeForm(user: ReturnType<typeof userEvent.setup>) {
  const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
  await user.type(search, "Conf");
  await user.click(
    await screen.findByRole("option", { name: "Confocal microscope common:relationshipPicker.openRecord" }),
  );
  await screen.findByRole("button", { name: "common:relationshipPicker.clear" });
}

describe("AddBookableItemPage", () => {
  it("keeps manual edits while retrying a failed initial target lookup", async () => {
    const user = userEvent.setup();
    const spare = { id: 456, name: "Spare microscope", globalId: "IN456", deleted: false };
    let saved: unknown;
    server.use(
      http.get("/api/v2/booking-configuration-targets", ({ request }) =>
        new URL(request.url).searchParams.get("query") === "IN123"
          ? new HttpResponse(null, { status: 503 })
          : HttpResponse.json([spare]),
      ),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ id: 8 }, { status: 201 });
      }),
    );
    const { queryClient } = renderPage("/booking/bookable-items/add?target=IN123");
    await user.type(await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" }), "Spare");
    await user.click(
      await screen.findByRole("option", { name: "Spare microscope common:relationshipPicker.openRecord" }),
    );
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");
    const requested = Promise.withResolvers<void>();
    const response = Promise.withResolvers<void>();
    server.use(
      http.get("/api/v2/booking-configuration-targets", async ({ request }) => {
        if (new URL(request.url).searchParams.get("query") !== "IN123") return HttpResponse.json([spare]);
        requested.resolve();
        await response.promise;
        return new HttpResponse(null, { status: 503 });
      }),
    );
    let refresh: Promise<void> | undefined;
    try {
      await act(async () => {
        refresh = queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configuration-targets", "route-target", "IN123"],
        });
        await requested.promise;
      });
      expect(
        within(screen.getByRole("group", { name: "Monday" })).getByLabelText("booking:settings.fields.openingStart"),
      ).toHaveValue("09:00");
      await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));
      await waitFor(() =>
        expect(saved).toMatchObject({
          target: { relationTo: "booking-instruments", value: 456 },
          openingHours: expect.arrayContaining([{ dayOfWeek: 1, start: "09:00", end: "17:00" }]),
        }),
      );
      response.resolve();
      await refresh;
      expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    } finally {
      response.resolve();
      await refresh;
    }
  });

  it("uses the route target only as an initial selection", async () => {
    const user = userEvent.setup();
    const spare = { id: 456, name: "Spare microscope", globalId: "IN456", deleted: false };
    let saved: unknown;
    server.use(
      targetsHandler(() => undefined, [confocal, spare]),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json({ id: 8 }, { status: 201 });
      }),
    );
    renderPage("/booking/bookable-items/add?target=IN123");
    expect(await screen.findByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeVisible();

    await user.click(await screen.findByRole("button", { name: "common:relationshipPicker.clear" }));

    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "booking:bookableItems.targetSearch.label" })).toHaveValue("");
    await user.type(screen.getByRole("combobox", { name: "booking:bookableItems.targetSearch.label" }), "Spare");
    await user.click(
      await screen.findByRole("option", { name: "Spare microscope common:relationshipPicker.openRecord" }),
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));
    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(saved).toMatchObject({ target: { relationTo: "booking-instruments", value: 456 } });
  });

  it("treats a blank relationship control as an empty selection", async () => {
    const user = userEvent.setup();
    server.use(targetsHandler(), availabilityHandler());
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

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(authorization).toBe(`Bearer ${OAUTH_TOKEN}`);
    expect(requestBody).toEqual({
      target: { relationTo: "booking-instruments", value: 123 },
      enabled: true,
      slotGranularityMinutes: 5,
      openingHours: settings.openingHours,
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    });
  });

  it("keeps the completed form available when creation fails", async () => {
    const user = userEvent.setup();
    let requestBody: unknown;
    const spare = { id: 456, name: "Spare confocal microscope", globalId: "IN456", deleted: false };
    server.use(
      targetsHandler(() => undefined, [confocal, spare]),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        requestBody = await request.json();
        return new HttpResponse(null, { status: 500 });
      }),
    );
    renderPage();
    await completeForm(user);
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.addError");
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeEnabled();
    expect(screen.getByRole("heading", { name: "booking:bookableItems.addTitle" })).toBeVisible();
    expect(mondayStart).toHaveValue("09:00");
    expect(
      within(screen.getByRole("group", { name: "Tuesday" })).getByLabelText("booking:settings.fields.openingStart"),
    ).toHaveValue("10:00");
    expect(requestBody).toMatchObject({
      openingHours: [{ ...settings.openingHours[0], start: "09:00" }, settings.openingHours[1]],
    });
    expect(settings.openingHours[0].start).toBe("08:00");

    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.clear" }));
    await user.type(screen.getByRole("combobox", { name: "booking:bookableItems.targetSearch.label" }), "Spare");
    await user.click(
      await screen.findByRole("option", { name: "Spare confocal microscope common:relationshipPicker.openRecord" }),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows a retryable error when creation fails due to a network error", async () => {
    const user = userEvent.setup();
    let attempts = 0;
    const requestBodies: unknown[] = [];
    server.use(
      targetsHandler(),
      availabilityHandler(),
      http.post("/api/v2/booking-configurations", async ({ request }) => {
        requestBodies.push(await request.json());
        if (attempts++ === 0) return HttpResponse.error();
        return HttpResponse.json({ id: 7 }, { status: 201 });
      }),
    );
    renderPage();
    await completeForm(user);
    const mondayStart = within(screen.getByRole("group", { name: "Monday" })).getByLabelText(
      "booking:settings.fields.openingStart",
    );
    await user.clear(mondayStart);
    await user.type(mondayStart, "09:00");

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.addError");
    expect(screen.getByRole("button", { name: "common:relationshipPicker.clear" })).toBeVisible();
    expect(mondayStart).toHaveValue("09:00");
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(attempts).toBe(2);
    expect(requestBodies[1]).toMatchObject({
      target: { relationTo: "booking-instruments", value: 123 },
      openingHours: [{ ...settings.openingHours[0], start: "09:00" }, settings.openingHours[1]],
    });
  });

  it("searches only eligible instruments and selects a result", async () => {
    const user = userEvent.setup();
    const targetRequests: URL[] = [];
    const spare = { id: 456, name: "Spare confocal microscope", globalId: "IN456", deleted: false };
    server.use(targetsHandler((url) => targetRequests.push(url), [spare]));
    const { container } = renderPage();
    const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");

    expect(
      screen.queryByRole("option", { name: "Confocal microscope common:relationshipPicker.openRecord" }),
    ).not.toBeInTheDocument();
    await user.click(
      await screen.findByRole("option", { name: "Spare confocal microscope common:relationshipPicker.openRecord" }),
    );
    expect(await screen.findByRole("button", { name: "common:relationshipPicker.clear" })).toBeVisible();

    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeVisible();
    expect(targetRequests.some((request) => request.searchParams.get("query") === "Conf")).toBe(true);

    await expectAccessible(container);
  });

  it("shows the existing configuration link when creation loses a race", async () => {
    const user = userEvent.setup();
    let conflict = false;
    server.use(
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
    server.use(http.get("/api/v2/booking-configuration-targets", () => new HttpResponse(null, { status: 500 })));
    renderPage();
    const search = await screen.findByRole("combobox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");

    expect(await screen.findByText("common:relationshipPicker.failed")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
  });
});
