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
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { createEditBookableItemRoute } from "../routes";

const configuration = {
  id: 7,
  target: {
    relationTo: "instruments",
    value: { id: 123, name: "Confocal microscope", globalId: "IN123", deleted: false },
    globalId: "IN123",
  },
  enabled: true,
  timezone: "Europe/Berlin",
  slotGranularityMinutes: 5,
  openingStart: "00:00",
  openingEnd: "24:00",
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  updatedAt: "2026-08-10T12:00:00Z",
};
const detailsDestinationTitle = "Details destination";

function DestinationPage() {
  const { t } = useTranslation("booking");
  return <h1>{t("bookableItems.plural")}</h1>;
}

function DetailsDestinationPage() {
  return <h1>{detailsDestinationTitle}</h1>;
}

function renderPage() {
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
  const detailsDestinationRoute = createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId",
    component: DetailsDestinationPage,
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([
      bookingRoute.addChildren([destinationRoute, detailsDestinationRoute, createEditBookableItemRoute(bookingRoute)]),
    ]),
    history: createMemoryHistory({ initialEntries: ["/booking/config/bookable-items/7/edit"] }),
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={null}>
        <RouterProvider router={router as never} />
      </Suspense>
    </QueryClientProvider>,
  );
}

describe("EditBookableItemPage", () => {
  it("loads and updates the selected booking configuration", async () => {
    const user = userEvent.setup();
    let requestUrl: URL | undefined;
    let requestBody: unknown;
    let authorization: string | null = null;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      http.get("/api/v2/booking-configurations/7", ({ request }) => {
        requestUrl = new URL(request.url);
        return HttpResponse.json(configuration);
      }),
      http.get("/api/v2/instruments/123", () =>
        HttpResponse.json({ id: 123, name: "Confocal microscope", globalId: "IN123" }),
      ),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        requestBody = await request.json();
        authorization = request.headers.get("Authorization");
        return HttpResponse.json({ ...configuration, enabled: false });
      }),
    );
    const { container } = renderPage();

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.editTitle" })).toBeVisible();
    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.queryByRole("combobox", { name: "booking:bookableItems.fields.target" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "booking:bookableItems.actions.viewInventory" })).toHaveAttribute(
      "href",
      "/globalId/IN123",
    );
    expect(screen.getByRole("checkbox", { name: "booking:bookableItems.fields.enabled" })).toBeChecked();
    expect(screen.getByRole("combobox", { name: "booking:bookableItems.fields.timezone" })).toHaveValue(
      "Europe/Berlin",
    );
    expect(requestUrl?.searchParams.get("depth")).toBe("1");
    expect(requestUrl?.searchParams.get("fields[booking-configurations]")).toBe(
      "id,target,enabled,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking,updatedAt",
    );
    await expectAccessible(container);

    await user.click(screen.getByRole("checkbox", { name: "booking:bookableItems.fields.enabled" }));
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));

    expect(await screen.findByRole("heading", { name: detailsDestinationTitle })).toBeVisible();
    expect(authorization).toBe("Bearer test-token");
    expect(requestBody).toEqual({
      enabled: false,
      timezone: "Europe/Berlin",
      slotGranularityMinutes: 5,
      openingStart: "00:00",
      openingEnd: "24:00",
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    });
  });

  it("keeps the form available when updating fails", async () => {
    const user = userEvent.setup();
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      http.get("/api/v2/booking-configurations/7", () => HttpResponse.json(configuration)),
      http.get("/api/v2/instruments/123", () =>
        HttpResponse.json({ id: 123, name: "Confocal microscope", globalId: "IN123" }),
      ),
      http.patch("/api/v2/booking-configurations/7", () => new HttpResponse(null, { status: 500 })),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "booking:bookableItems.actions.save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookableItems.editError");
    expect(screen.getByRole("button", { name: "booking:bookableItems.actions.save" })).toBeEnabled();
    expect(screen.getByRole("heading", { name: "booking:bookableItems.editTitle" })).toBeVisible();
  });

  it("updates visible fields without resubmitting an unreadable target", async () => {
    const user = userEvent.setup();
    let requestBody: unknown;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      http.get("/api/v2/booking-configurations/7", () => HttpResponse.json({ ...configuration, target: null })),
      http.patch("/api/v2/booking-configurations/7", async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json({ ...configuration, target: null, enabled: false });
      }),
    );
    renderPage();

    expect(await screen.findByText("common:values.unknownItem")).toBeVisible();
    expect(screen.queryByRole("combobox", { name: "booking:bookableItems.fields.target" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "booking:bookableItems.fields.enabled" }));
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.actions.save" }));

    expect(await screen.findByRole("heading", { name: "booking:bookableItems.plural" })).toBeVisible();
    expect(requestBody).toEqual({
      enabled: false,
      timezone: "Europe/Berlin",
      slotGranularityMinutes: 5,
      openingStart: "00:00",
      openingEnd: "24:00",
      bufferBeforeMinutes: 0,
      bufferAfterMinutes: 0,
      maxBookingDurationMinutes: 0,
      allowDoubleBooking: false,
    });
  });
});
