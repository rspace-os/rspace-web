import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import materialTheme from "@/theme";
import BookingAction from "../BookingAction";

const globalId = "IN123";

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

function featureFlagPage(value: boolean) {
  return {
    ...collectionPage([
      {
        name: FEATURE_FLAGS.bookingEnabled,
        value,
        baselineValue: false,
        source: "USER_OVERRIDE",
        canOverride: true,
      },
    ]),
    limit: 100,
  };
}

function renderAction({ isOwner = true }: { isOwner?: boolean } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <BookingAction globalId={globalId} isOwner={isOwner} />
        </Suspense>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

function bookingHandlers({ enabled, configured }: { enabled: boolean; configured: boolean }) {
  return [
    http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
    http.get("/api/v2/feature-flags", () => HttpResponse.json(featureFlagPage(enabled))),
    http.get("/api/v2/booking-configurations", () =>
      HttpResponse.json(
        collectionPage(
          configured
            ? [
                {
                  id: 456,
                  target: { relationTo: "instruments", value: 123, globalId },
                },
              ]
            : [],
        ),
      ),
    ),
  ];
}

describe("Inventory instrument booking action", () => {
  test("links an owner to booking creation when booking is configured", async () => {
    server.use(...bookingHandlers({ enabled: true, configured: true }));
    const { container } = renderAction();

    const link = await screen.findByRole("link", { name: "inventory:instrument.booking.configured.action" });

    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(link).toHaveAttribute("href", "/booking/calendar/bookings/add?target=IN123");
    expect(screen.getByText("inventory:instrument.booking.configured.title")).toBeInTheDocument();
    await expectAccessible(container);
  });

  test("links an owner to booking setup when booking is not configured", async () => {
    server.use(...bookingHandlers({ enabled: true, configured: false }));
    const { container } = renderAction();

    const link = await screen.findByRole("link", { name: "inventory:instrument.booking.notConfigured.action" });

    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(link).toHaveAttribute("href", "/booking/config/bookable-items/add");
    expect(screen.getByText("inventory:instrument.booking.notConfigured.title")).toBeInTheDocument();
    await expectAccessible(container);
  });

  test("shows no booking UI when the booking feature flag is off", async () => {
    let featureFlagRequests = 0;
    let configurationRequests = 0;
    server.use(
      http.post("/api/v2/oauth/tokens", () => HttpResponse.json({ accessToken: "test-token" })),
      http.get("/api/v2/feature-flags", () => {
        featureFlagRequests += 1;
        return HttpResponse.json(featureFlagPage(false));
      }),
      http.get("/api/v2/booking-configurations", () => {
        configurationRequests += 1;
        return HttpResponse.json(collectionPage([]));
      }),
    );
    renderAction();

    await waitFor(() => expect(featureFlagRequests).toBe(1));
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(configurationRequests).toBe(0);
  });

  test("shows no booking UI when the viewer is not the owner", () => {
    renderAction({ isOwner: false });

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
