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
import { render, screen, within } from "@testing-library/react";
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

function renderPage() {
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
        createAddBookableItemRoute(bookingRoute),
      ]),
    ]),
    history: createMemoryHistory({ initialEntries: ["/booking/bookable-items/add"] }),
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
  const search = await screen.findByRole("textbox", { name: "booking:bookableItems.targetSearch.label" });
  await user.type(search, "Conf");
  await user.click(screen.getByRole("button", { name: "booking:bookableItems.targetSearch.search" }));
  await user.click(await screen.findByRole("button", { name: "Confocal microscope (IN123)" }));
}

describe("AddBookableItemPage", () => {
  it("treats a blank relationship control as an empty selection", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      targetsHandler(),
      availabilityHandler(),
    );
    renderPage();

    const search = await screen.findByRole("textbox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");
    await user.clear(search);

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
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

    expect(await screen.findByRole("textbox", { name: "booking:bookableItems.targetSearch.label" })).toBeVisible();
    expect(screen.queryByRole("combobox", { name: "booking:bookableItems.fields.timezone" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
    await completeForm(user);

    await expectAccessible(container);

    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" }));

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(authorization).toBe("Bearer test-token");
    expect(requestBody).toEqual({
      target: { relationTo: "booking-instruments", value: 123 },
      enabled: true,
      slotGranularityMinutes: 5,
      openingStart: "00:00",
      openingEnd: "24:00",
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
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
    const search = await screen.findByRole("textbox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.targetSearch.search" }));

    expect(screen.queryByRole("button", { name: "Confocal microscope (IN123)" })).not.toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Spare confocal microscope (IN456)" }));

    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.submit" })).toBeVisible();
    expect(targetRequests[0]?.searchParams.get("query")).toBe("Conf");

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
    const search = await screen.findByRole("textbox", { name: "booking:bookableItems.targetSearch.label" });
    await user.type(search, "Conf");
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.targetSearch.search" }));

    expect(await screen.findByText("booking:bookableItems.targetSearch.error")).toBeVisible();
    expect(screen.queryByRole("button", { name: "booking:bookableItems.actions.submit" })).not.toBeInTheDocument();
  });
});
