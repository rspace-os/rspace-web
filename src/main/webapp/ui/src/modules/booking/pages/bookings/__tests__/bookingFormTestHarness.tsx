import { HttpResponse, http, type RequestHandler } from "msw";

export class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

export function bookingCatalogueResponse(items: readonly unknown[]) {
  return {
    items,
    page: 1,
    pageSize: 20,
    total: items.length,
    facets: { types: ["INSTRUMENT"] },
  };
}

export function bookingScheduleHandler(bookings: readonly unknown[] | null = []): RequestHandler {
  return http.get("/api/v2/bookings", () =>
    bookings === null
      ? new HttpResponse(null, { status: 503 })
      : HttpResponse.json({
          docs: bookings,
          page: 1,
          hasNextPage: false,
          limit: 100,
          pagingCounter: 1,
          hasPrevPage: false,
          prevPage: null,
          nextPage: null,
        }),
  );
}

export function bookingConfigurationResponse(document: unknown) {
  return {
    docs: [document],
    totalDocs: 1,
    limit: 2,
    page: 1,
    pagingCounter: 1,
    totalPages: 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}
