import { parse } from "@rsql/parser";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import type { BookingCatalogueItem } from "@/modules/booking/domain/bookingCatalogue";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import {
  type AllBookableItem,
  AvailabilityCandidateLimitError,
  type AvailabilityQuickIndexEntry,
  deriveAvailabilityCandidateFilter,
  fetchAvailabilityCandidates,
  fetchAvailabilityFilteredCatalogue,
  loadAvailabilityQuickIndex,
  resolveAvailabilityFilters,
  useAvailabilityQuickFilterIndex,
  withAvailability,
} from "./availabilityQuickFilters";

const candidate = (id: number, globalId: string, timezone: string): BookingConfiguration => ({
  id,
  configurationVersion: 0,
  target: {
    relationTo: "booking-instruments",
    value: { id, name: globalId, deleted: false },
    globalId,
  },
  enabled: true,
  state: "ACTIVE",
  timezone,
  slotGranularityMinutes: 5,
  openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 0,
  allowDoubleBooking: false,
  effectiveRole: "Viewer",
  roleSources: [],
  capabilities: {
    canEditConfiguration: false,
    canViewAudit: false,
    canViewAccess: false,
    canManageAssignments: false,
    canManageOwners: false,
    canCreateBooking: false,
    canManageOwnBookings: false,
    canManageAllEvents: false,
    canCreateBlockout: false,
    canSubscribeCalendar: false,
    canLeaveConfiguration: false,
    canManageNotificationSubscription: false,
  },
});

const page = (docs: readonly BookingConfiguration[], pageNumber = 1, totalPages = 1, totalDocs = docs.length) => ({
  items: docs.map((doc) => ({
    ...doc,
    configurationId: doc.id,
    targetId: doc.target?.value.id,
    targetType: "INSTRUMENT",
    globalId: doc.target?.globalId,
    name: doc.target?.value.name,
    location: null,
  })),
  total: totalDocs,
  pageSize: totalPages > 1 && totalDocs < 100 ? 1 : 100,
  page: pageNumber,
  facets: { types: ["INSTRUMENT"] },
});

function catalogueItem(configurationId: number, globalId: string): BookingCatalogueItem {
  return {
    configurationId,
    configurationVersion: 0,
    targetType: "INSTRUMENT",
    targetId: configurationId,
    globalId,
    name: globalId,
    timezone: "UTC",
    slotGranularityMinutes: 5,
    openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
    bufferBeforeMinutes: 0,
    bufferAfterMinutes: 0,
    maxBookingDurationMinutes: 0,
    allowDoubleBooking: false,
    effectiveRole: null,
    capabilities: {
      canEditConfiguration: false,
      canViewAudit: false,
      canViewAccess: false,
      canManageAssignments: false,
      canManageOwners: false,
      canCreateBooking: false,
      canManageOwnBookings: false,
      canManageAllEvents: false,
      canCreateBlockout: false,
      canSubscribeCalendar: false,
      canLeaveConfiguration: false,
      canManageNotificationSubscription: false,
    },
    location: null,
  };
}

function quickIndexEntry(category: AvailabilityQuickIndexEntry["category"]): AvailabilityQuickIndexEntry {
  return {
    date: "2026-08-17",
    bounds: displayInterval("2026-08-17", "UTC", "00:00", "24:00"),
    intervals: [],
    category,
  };
}

function catalogueResponse(
  items: readonly BookingCatalogueItem[],
  pageNumber: number,
  pageSize: number,
  total: number,
  types: readonly string[] = ["INSTRUMENT"],
) {
  return HttpResponse.json({
    items,
    page: pageNumber,
    pageSize,
    total,
    facets: { types },
  });
}

function targetsInWhere(where: string): readonly string[] {
  const targetList = where.split("target=in=(")[1]?.split(")")[0] ?? "";
  return targetList ? targetList.split(",") : [];
}

const MAX_FILTER_NESTING = 10;
type ParsedRsqlNode = ReturnType<typeof parse>;
type FilterNesting = { logical: number; parentheses: number };

function logicalNesting(node: ParsedRsqlNode, parentOperator?: string): number {
  if (node.type === "COMPARISON") return 0;
  const depth = node.operator === parentOperator ? 0 : 1;
  return depth + Math.max(logicalNesting(node.left, node.operator), logicalNesting(node.right, node.operator));
}

function parenthesisNesting(where: string): number {
  let depth = 0;
  let maximum = 0;
  let quote: "'" | '"' | undefined;
  let escaped = false;
  for (const character of where) {
    if (escaped) {
      escaped = false;
    } else if (character === "\\") {
      escaped = true;
    } else if (quote) {
      if (character === quote) quote = undefined;
    } else if (character === "'" || character === '"') {
      quote = character;
    } else if (character === "(") {
      depth += 1;
      maximum = Math.max(maximum, depth);
    } else if (character === ")") {
      depth -= 1;
    }
  }
  return maximum;
}

function filterNesting(where: string): FilterNesting {
  return {
    logical: where ? logicalNesting(parse(where)) : 0,
    parentheses: parenthesisNesting(where),
  };
}

function assertServerFilterNesting(where: string): FilterNesting {
  const nesting = filterNesting(where);
  if (nesting.logical > MAX_FILTER_NESTING || nesting.parentheses > MAX_FILTER_NESTING) {
    throw new Error("COMPLEXITY");
  }
  return nesting;
}

function boundaryFilter(extraDepth = 0) {
  const depth = 9 + extraDepth;
  const idIsNotTwo: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "id",
    operator: "notEquals",
    value: 2,
  };
  let branch: FilterExpression<AllBookableItem>;
  let lastKind: "and" | "or";
  if (depth % 2 === 1) {
    branch = {
      kind: "and",
      children: [idIsNotTwo, { kind: "comparison", field: "id", operator: "greaterThanOrEqual", value: 0 }],
    };
    lastKind = "and";
  } else {
    branch = {
      kind: "or",
      children: [idIsNotTwo, { kind: "comparison", field: "target", operator: "equals", value: "NEVER" }],
    };
    lastKind = "or";
  }
  for (let level = 1; level < depth; level += 1) {
    const kind: "and" | "or" = lastKind === "and" ? "or" : "and";
    const neutral: FilterExpression<AllBookableItem> =
      kind === "and"
        ? { kind: "comparison", field: "id", operator: "greaterThanOrEqual", value: 0 }
        : { kind: "comparison", field: "target", operator: "equals", value: "NEVER" };
    branch = { kind, children: [neutral, branch] };
    lastKind = kind;
  }
  const positive: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "id",
    operator: "greaterThanOrEqual",
    value: 0,
  };
  const quotedParenthesisValue: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "target",
    operator: "equals",
    value: "NEVER(1)",
  };
  const specialized: FilterExpression<AllBookableItem> = {
    kind: "or",
    children: [positive, branch, quotedParenthesisValue],
  };
  const baseline: FilterExpression<AllBookableItem> = {
    kind: "or",
    children: [branch, quotedParenthesisValue],
  };
  return {
    expression: {
      kind: "or",
      children: [
        {
          kind: "and",
          children: [
            { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
            positive,
          ],
        },
        branch,
        quotedParenthesisValue,
      ],
    } satisfies FilterExpression<AllBookableItem>,
    baseline,
    specialized,
  };
}

describe("availability quick filters", () => {
  it("derives a safe candidate predicate while preserving item-only grouping", () => {
    const itemA: FilterExpression<AllBookableItem> = {
      kind: "comparison",
      field: "target",
      operator: "equals",
      value: "IN1",
    };
    const itemB: FilterExpression<AllBookableItem> = {
      kind: "comparison",
      field: "target",
      operator: "equals",
      value: "IN2",
    };
    const availability: FilterExpression<AllBookableItem> = {
      kind: "comparison",
      field: "availability",
      operator: "equals",
      value: "available-now",
    };

    expect(deriveAvailabilityCandidateFilter({ kind: "and", children: [itemA, availability] })).toEqual(itemA);
    expect(deriveAvailabilityCandidateFilter({ kind: "or", children: [itemA, itemB] })).toEqual({
      kind: "or",
      children: [itemA, itemB],
    });
    expect(deriveAvailabilityCandidateFilter({ kind: "or", children: [itemA, availability] })).toBeNull();
    expect(
      deriveAvailabilityCandidateFilter({
        kind: "and",
        children: [{ kind: "or", children: [availability, itemA] }, itemB],
      }),
    ).toEqual(itemB);
  });

  it("retains duplicate item and ID rules when a quick filter is changed or removed", () => {
    const original: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "id", operator: "greaterThan", value: 5 },
        { kind: "comparison", field: "target", operator: "equals", value: "IN1" },
        { kind: "comparison", field: "target", operator: "equals", value: "IN2" },
      ],
    };
    const added = withAvailability(original, "available-now");
    expect(withAvailability(added, undefined)).toEqual(original);
    expect(withAvailability(withAvailability(added, "free-later-today"), undefined)).toEqual(original);
    expect(resolveAvailabilityFilters(added, new Map())).toEqual({
      kind: "and",
      children: [...original.children, { kind: "comparison", field: "id", operator: "equals", value: 0 }],
    });
  });

  it("merges numeric availability residuals with outside-OR baseline pages without double counting", async () => {
    const outsideMatch = catalogueItem(1, "OUTSIDE-1");
    const overlap = catalogueItem(1010, "CANDIDATE-OVERLAP");
    const baselineRows = [
      outsideMatch,
      ...Array.from({ length: 101 }, (_, index) =>
        index === 10 ? overlap : catalogueItem(1000 + index, `OUTSIDE-${1000 + index}`),
      ),
    ];
    const candidateRows = [
      catalogueItem(2, "CANDIDATE-2"),
      catalogueItem(20, "CANDIDATE-20"),
      catalogueItem(100, "CANDIDATE-100"),
      overlap,
    ];
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = targetsInWhere(where);
        const rows =
          targetIds.length > 0
            ? where.includes("id=ge=1000")
              ? baselineRows.filter((item) => targetIds.includes(item.globalId))
              : candidateRows.filter((item) => targetIds.includes(item.globalId))
            : baselineRows;
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        const pageItems = rows.slice((pageNumber - 1) * pageSize, pageNumber * pageSize);
        return catalogueResponse(pageItems, pageNumber, pageSize, rows.length);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        {
          kind: "or",
          children: [
            { kind: "comparison", field: "target", operator: "equals", value: "OUTSIDE-1" },
            { kind: "comparison", field: "id", operator: "greaterThanOrEqual", value: 1000 },
          ],
        },
      ],
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>();
    for (const item of candidateRows) index.set(item.globalId, quickIndexEntry("available-now"));
    const signal = new AbortController().signal;

    const firstPage = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { page: 1, pageSize: 4 },
      "token",
      signal,
    );
    const secondPage = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { page: 2, pageSize: 100 },
      "token",
      signal,
    );

    expect(firstPage.items.map((item) => item.configurationId)).toEqual([1, 2, 20, 100]);
    expect(firstPage.items[0]?.globalId).toBe("OUTSIDE-1");
    expect(secondPage.items.map((item) => item.configurationId)).toEqual([1096, 1097, 1098, 1099, 1100]);
    expect(firstPage.total).toBe(105);
    expect(secondPage.total).toBe(105);
    expect(secondPage.page).toBe(2);
    expect(secondPage.pageSize).toBe(100);
    expect(
      requests.some(
        (url) => !targetsInWhere(url.searchParams.get("where") ?? "").length && url.searchParams.get("page") === "2",
      ),
    ).toBe(true);
  });

  it("specializes nested availability leaves together while preserving nonavailability operators", async () => {
    const outside = catalogueItem(30, "OUTSIDE-1");
    const availableNow = catalogueItem(10, "IN1");
    const freeLater = catalogueItem(20, "IN4");
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = targetsInWhere(where);
        let items: readonly BookingCatalogueItem[] = [];
        if (targetIds.length === 0 && where.includes("target=out=(OUT1,OUT2)")) {
          items = [outside];
        } else if (targetIds.includes("IN1") && where.includes("id!=500")) {
          items = [availableNow];
        } else if (targetIds.includes("IN4") && where.includes("target=out=(IN2,IN3)")) {
          items = [freeLater];
        }
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse(items, pageNumber, pageSize, items.length);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "or",
      children: [
        {
          kind: "and",
          children: [
            { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
            { kind: "comparison", field: "target", operator: "equals", value: "IN1" },
            { kind: "comparison", field: "id", operator: "notEquals", value: 500 },
          ],
        },
        {
          kind: "and",
          children: [
            { kind: "comparison", field: "availability", operator: "notEquals", value: "free-later-today" },
            { kind: "comparison", field: "target", operator: "notIn", value: ["IN2", "IN3"] },
          ],
        },
        {
          kind: "and",
          children: [
            { kind: "comparison", field: "target", operator: "notIn", value: ["OUT1", "OUT2"] },
            { kind: "comparison", field: "id", operator: "notEquals", value: 42 },
          ],
        },
      ],
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>([
      ["IN1", quickIndexEntry("available-now")],
      ["IN4", quickIndexEntry("free-later-today")],
    ]);

    const result = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { page: 1, pageSize: 20 },
      "token",
      new AbortController().signal,
    );

    expect(result.items.map((item) => item.configurationId)).toEqual([10, 20, 30]);
    expect(result.total).toBe(3);
    expect(requests.some((url) => url.searchParams.get("where")?.includes("target=out=(IN2,IN3)"))).toBe(true);
    expect(requests.some((url) => url.searchParams.get("where")?.includes("id!=500"))).toBe(true);
    expect(requests.some((url) => url.searchParams.get("where")?.includes("id!=42"))).toBe(true);
    expect(requests.every((url) => !url.searchParams.get("where")?.includes("availability"))).toBe(true);
  });

  it("returns the nonavailability baseline when the availability index is empty", async () => {
    const outside = catalogueItem(7, "OUTSIDE-1");
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse(pageNumber === 1 ? [outside] : [], pageNumber, pageSize, 1);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        { kind: "comparison", field: "target", operator: "equals", value: "OUTSIDE-1" },
      ],
    };

    const result = await fetchAvailabilityFilteredCatalogue(
      expression,
      new Map(),
      { page: 1, pageSize: 20 },
      "token",
      new AbortController().signal,
    );

    expect(result.items.map((item) => item.configurationId)).toEqual([7]);
    expect(result.total).toBe(1);
    expect(requests).toHaveLength(1);
    expect(requests[0]?.searchParams.get("where")).toBe("target==OUTSIDE-1");
  });

  it("keeps depth-ten saved OR filters valid by scoping boundary requests with target", async () => {
    const { expression, baseline, specialized } = boundaryFilter();
    const originalWhere = serializeRsqlExpression(expression);
    const originalNesting = assertServerFilterNesting(originalWhere);
    expect(originalNesting).toEqual({ logical: 10, parentheses: 9 });
    expect(originalWhere).toContain('"NEVER(1)"');
    expect(parenthesisNesting('target=="NEVER(1)"')).toBe(0);

    const unscopedWhereFilter: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [specialized, { kind: "comparison", field: "target", operator: "in", value: ["IN1", "IN2"] }],
    };
    const depthElevenWhere = serializeRsqlExpression(unscopedWhereFilter);
    expect(filterNesting(depthElevenWhere)).toEqual({ logical: 11, parentheses: 11 });
    expect(() => assertServerFilterNesting(depthElevenWhere)).toThrow("COMPLEXITY");

    const outside = catalogueItem(1, "OUTSIDE(1)");
    const overlap = catalogueItem(3, "IN1");
    const availabilityOnly = catalogueItem(2, "IN2");
    const baselineWhere = serializeRsqlExpression(baseline);
    const specializedWhere = serializeRsqlExpression(specialized);
    const requests: { url: URL; nesting: FilterNesting }[] = [];
    const rejectedNesting: FilterNesting[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where") ?? "";
        const nesting = filterNesting(where);
        requests.push({ url, nesting });
        if (nesting.logical > MAX_FILTER_NESTING || nesting.parentheses > MAX_FILTER_NESTING) {
          rejectedNesting.push(nesting);
          return HttpResponse.json({ reason: "COMPLEXITY" }, { status: 400 });
        }
        if (where !== baselineWhere && where !== specializedWhere) {
          return HttpResponse.json({ reason: "Unexpected filter" }, { status: 500 });
        }
        const scopedTarget = url.searchParams.get("target");
        const rows = [outside, overlap, availabilityOnly].filter(
          (item) =>
            (!scopedTarget || item.globalId === scopedTarget) &&
            (where === specializedWhere || item.configurationId !== 2),
        );
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        const items = rows.slice((pageNumber - 1) * pageSize, pageNumber * pageSize);
        return catalogueResponse(items, pageNumber, pageSize, rows.length);
      }),
    );
    const index = new Map<string, AvailabilityQuickIndexEntry>([
      ["IN1", quickIndexEntry("available-now")],
      ["IN2", quickIndexEntry("available-now")],
    ]);

    const result = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { page: 1, pageSize: 20 },
      "token",
      new AbortController().signal,
    );

    expect(result.items.map((item) => item.configurationId)).toEqual([1, 2, 3]);
    expect(result.items[0]?.globalId).toBe("OUTSIDE(1)");
    expect(result.total).toBe(3);
    expect(rejectedNesting).toEqual([]);
    expect(Math.max(...requests.map(({ nesting }) => nesting.logical))).toBe(10);
    expect(Math.max(...requests.map(({ nesting }) => nesting.parentheses))).toBeLessThanOrEqual(MAX_FILTER_NESTING);
    const scopedRequests = requests.filter(({ url }) => url.searchParams.has("target"));
    expect(scopedRequests).toHaveLength(4);
    expect(scopedRequests.filter(({ url }) => url.searchParams.get("where") === specializedWhere)).toHaveLength(2);
    expect(scopedRequests.filter(({ url }) => url.searchParams.get("where") === baselineWhere)).toHaveLength(2);
    expect(new Set(scopedRequests.map(({ url }) => url.searchParams.get("target")))).toEqual(new Set(["IN1", "IN2"]));
  });

  it("rejects an already-overdepth saved filter before sending catalogue requests", async () => {
    const { expression } = boundaryFilter(1);
    expect(filterNesting(serializeRsqlExpression(expression)).logical).toBe(11);
    let requests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requests += 1;
        const url = new URL(request.url);
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse([], pageNumber, pageSize, 0);
      }),
    );

    await expect(
      fetchAvailabilityFilteredCatalogue(
        expression,
        new Map([["IN1", quickIndexEntry("available-now")]]),
        { page: 1, pageSize: 20 },
        "token",
        new AbortController().signal,
      ),
    ).rejects.toThrow("nesting limit");
    expect(requests).toBe(0);
  });

  it("shrinks 1,000 long target IDs against serialized filters, q, and authorization headers", async () => {
    const longQuery = "large catalogue query ".repeat(10);
    const token = "long-token".repeat(110);
    const blockedTargets = Array.from({ length: 24 }, (_, index) => `BLOCK-${index}-${"f".repeat(42)}`);
    const expression: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        { kind: "comparison", field: "target", operator: "notIn", value: blockedTargets },
        { kind: "comparison", field: "id", operator: "greaterThanOrEqual", value: 1 },
      ],
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>();
    for (let target = 0; target < 1000; target += 1) {
      const globalId = `TARGET-${String(target).padStart(4, "0")}-${"x".repeat(48)}`;
      index.set(globalId, quickIndexEntry("available-now"));
    }
    const requests: { url: URL; authorization: string; requestedWith: string }[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push({
          url,
          authorization: request.headers.get("authorization") ?? "",
          requestedWith: request.headers.get("x-requested-with") ?? "",
        });
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse([], pageNumber, pageSize, 0);
      }),
    );

    const result = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { q: longQuery, page: 1, pageSize: 20 },
      token,
      new AbortController().signal,
    );

    const encoder = new TextEncoder();
    const targetBatchSizes = requests
      .map(({ url }) => targetsInWhere(url.searchParams.get("where") ?? "").length)
      .filter((targetCount) => targetCount > 0);
    expect(result.items).toEqual([]);
    expect(result.total).toBe(0);
    expect(targetBatchSizes.length).toBeGreaterThan(2);
    expect(Math.max(...targetBatchSizes)).toBeLessThan(500);
    for (const { url, authorization, requestedWith } of requests) {
      const requestLine = `GET ${url.pathname}${url.search} HTTP/1.1\r\n`;
      const headers = `authorization: ${authorization}\r\nx-requested-with: ${requestedWith}\r\n\r\n`;
      expect(authorization.length).toBeGreaterThan(token.length);
      expect(url.searchParams.get("q")).toBe(longQuery.trim());
      expect(encoder.encode(`${requestLine}${headers}`).length).toBeLessThanOrEqual(6000);
    }
  });

  it("rejects a single-target scoped filter that exceeds the parser argument limit", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse([], pageNumber, pageSize, 0);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "and",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        {
          kind: "comparison",
          field: "target",
          operator: "notIn",
          value: Array.from({ length: 1000 }, (_, i) => `B${i}`),
        },
      ],
    };
    const index = new Map([["IN1", quickIndexEntry("available-now")]]);

    await expect(
      fetchAvailabilityFilteredCatalogue(
        expression,
        index,
        { page: 1, pageSize: 20 },
        "token",
        new AbortController().signal,
      ),
    ).rejects.toThrow("One target and its saved filter exceed the catalogue parser or request-size limits");
  });

  it.each([414, 431])("splits a catalogue target batch after HTTP %s", async (status) => {
    const candidateRows = [catalogueItem(2, "IN1"), catalogueItem(20, "IN2")];
    let oversizedAttempts = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = targetsInWhere(where);
        if (targetIds.length === 2 && !where.includes("id==0")) {
          oversizedAttempts += 1;
          return new HttpResponse(null, { status });
        }
        const items =
          targetIds.length > 0 && !where.includes("id==0")
            ? candidateRows.filter((item) => targetIds.includes(item.globalId))
            : [];
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse(items, pageNumber, pageSize, items.length);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "comparison",
      field: "availability",
      operator: "equals",
      value: "available-now",
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>([
      ["IN1", quickIndexEntry("available-now")],
      ["IN2", quickIndexEntry("available-now")],
    ]);

    const result = await fetchAvailabilityFilteredCatalogue(
      expression,
      index,
      { page: 1, pageSize: 20 },
      "token",
      new AbortController().signal,
    );

    expect(oversizedAttempts).toBe(1);
    expect(result.items.map((item) => item.configurationId)).toEqual([2, 20]);
    expect(result.total).toBe(2);
  });

  it("rejects the whole catalogue result when a later availability group fails", async () => {
    const firstCandidate = catalogueItem(2, "IN1");
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = targetsInWhere(where);
        if (targetIds.includes("IN2") && !where.includes("id==0")) {
          return HttpResponse.json({ message: "later batch failed" }, { status: 500 });
        }
        const items = targetIds.includes("IN1") && !where.includes("id==0") ? [firstCandidate] : [];
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse(items, pageNumber, pageSize, items.length);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        { kind: "comparison", field: "availability", operator: "equals", value: "free-later-today" },
      ],
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>([
      ["IN1", quickIndexEntry("available-now")],
      ["IN2", quickIndexEntry("free-later-today")],
    ]);

    await expect(
      fetchAvailabilityFilteredCatalogue(
        expression,
        index,
        { page: 1, pageSize: 20 },
        "token",
        new AbortController().signal,
      ),
    ).rejects.toThrow("500");
  });

  it("rejects instead of returning earlier groups when a later catalogue request is aborted", async () => {
    const controller = new AbortController();
    const firstCandidate = catalogueItem(2, "IN1");
    let laterGroupStarted = false;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        const where = url.searchParams.get("where") ?? "";
        const targetIds = targetsInWhere(where);
        if (targetIds.includes("IN2") && !where.includes("id==0")) {
          laterGroupStarted = true;
          controller.abort();
        }
        const items = targetIds.includes("IN1") && !where.includes("id==0") ? [firstCandidate] : [];
        const pageNumber = Number(url.searchParams.get("page"));
        const pageSize = Number(url.searchParams.get("limit"));
        return catalogueResponse(items, pageNumber, pageSize, items.length);
      }),
    );
    const expression: FilterExpression<AllBookableItem> = {
      kind: "or",
      children: [
        { kind: "comparison", field: "availability", operator: "equals", value: "available-now" },
        { kind: "comparison", field: "availability", operator: "equals", value: "free-later-today" },
      ],
    };
    const index = new Map<string, AvailabilityQuickIndexEntry>([
      ["IN1", quickIndexEntry("available-now")],
      ["IN2", quickIndexEntry("free-later-today")],
    ]);

    await expect(
      fetchAvailabilityFilteredCatalogue(expression, index, { page: 1, pageSize: 20 }, "token", controller.signal),
    ).rejects.toThrow();
    expect(laterGroupStarted).toBe(true);
  });

  it("treats a display window collapsed by DST as unavailable without requesting bookings", async () => {
    const result = await loadAvailabilityQuickIndex(
      [candidate(1, "IN1", "Europe/Berlin")],
      new Date("2026-03-29T00:00:00Z"),
      "Europe/Berlin",
      "02:30",
      "03:00",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe("unavailable-today");
    expect(result.get("IN1")?.bounds.elapsedMinutes).toBe(0);
  });

  it("fetches every catalogue candidate page", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const url = new URL(request.url);
        requests.push(url);
        return HttpResponse.json(
          url.searchParams.get("page") === "1"
            ? page([candidate(1, "IN1", "UTC")], 1, 2, 2)
            : page([candidate(2, "IN2", "Europe/Berlin")], 2, 2, 2),
        );
      }),
    );

    const result = await fetchAvailabilityCandidates("token", new AbortController().signal);
    expect(result).toHaveLength(2);
    expect(requests.map((request) => request.searchParams.get("page"))).toEqual(["1", "2"]);
    expect(requests[0].searchParams.get("where")).toBeNull();
    expect(requests[0].searchParams.get("limit")).toBe("100");
  });

  it("pushes the safe candidate predicate down before applying the candidate limit", async () => {
    const requests: URL[] = [];
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        requests.push(new URL(request.url));
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
    );

    await fetchAvailabilityCandidates("token", new AbortController().signal, "target.customFields.SF152==BSL-2");

    expect(requests[0].searchParams.get("where")).toBe("target.customFields.SF152==BSL-2");
  });

  it("preserves OR candidates before the filtered capacity limit", async () => {
    const rule = "target==IN1,target==IN2";
    const expected = rule;
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) =>
        HttpResponse.json(
          new URL(request.url).searchParams.get("where") === expected
            ? page([candidate(1, "IN1", "UTC"), candidate(2, "IN2", "UTC")])
            : page([], 1, 11, 1001),
        ),
      ),
    );
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal, rule)).resolves.toHaveLength(2);
  });

  it("rejects candidate collections above the relationship-filter ceiling", async () => {
    server.use(http.get("/api/v2/booking-catalogue", () => HttpResponse.json(page([], 1, 11, 1001))));
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal)).rejects.toBeInstanceOf(
      AvailabilityCandidateLimitError,
    );
  });

  it("applies catalogue search and types before the candidate capacity limit", async () => {
    server.use(
      http.get("/api/v2/booking-catalogue", ({ request }) => {
        const parameters = new URL(request.url).searchParams;
        return HttpResponse.json(
          parameters.get("q") === "microscope" && parameters.get("type") === "INSTRUMENT"
            ? page([candidate(1, "IN1", "UTC")])
            : page([], 1, 11, 1001),
        );
      }),
    );
    await expect(fetchAvailabilityCandidates("token", new AbortController().signal)).rejects.toBeInstanceOf(
      AvailabilityCandidateLimitError,
    );
    await expect(
      fetchAvailabilityCandidates("token", new AbortController().signal, undefined, {
        q: "microscope",
        types: ["INSTRUMENT"],
      }),
    ).resolves.toHaveLength(1);
  });

  it("classifies candidates using each configured time zone", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({
          docs: [
            {
              id: 1,
              version: 0,
              target: candidate(1, "IN1", "UTC").target,
              timezone: "UTC",
              start: "2026-08-17T08:00:00Z",
              end: "2026-08-17T10:00:00Z",
              state: "CONFIRMED",
              privacy: "full",
              purpose: null,
              bookedBy: null,
              canEdit: false,
              canCancel: false,
              createdAt: "2026-08-17T08:00:00Z",
              updatedAt: "2026-08-17T08:00:00Z",
            },
          ],
          totalDocs: 1,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        }),
      ),
    );
    const result = await loadAvailabilityQuickIndex(
      [candidate(1, "IN1", "UTC"), candidate(2, "IN2", "America/Los_Angeles")],
      new Date("2026-08-17T09:00:00Z"),
      "UTC",
      "00:00",
      "24:00",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe("free-later-today");
    expect(result.get("IN1")?.date).toBe("2026-08-17");
    expect(result.get("IN2")?.category).toBe("available-now");
    expect(result.get("IN2")?.date).toBe("2026-08-17");
  });

  it("distinguishes before opening, open now, and after closing", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false }),
      ),
    );
    const restricted = {
      ...candidate(1, "IN1", "UTC"),
      openingHours: candidate(1, "IN1", "UTC")
        .openingHours.filter((entry) => [1, 2, 3, 4, 5, 6, 7].includes(entry.dayOfWeek))
        .map((entry) => ({ ...entry, start: "08:00", end: "18:00" })),
    };

    for (const [time, expected] of [
      ["2026-08-17T07:00:00Z", "free-later-today"],
      ["2026-08-17T09:00:00Z", "available-now"],
      ["2026-08-17T19:00:00Z", "unavailable-today"],
    ] as const) {
      const result = await loadAvailabilityQuickIndex(
        [restricted],
        new Date(time),
        "UTC",
        "00:00",
        "24:00",
        "token",
        new AbortController().signal,
      );
      expect(result.get("IN1")?.category).toBe(expected);
    }
  });

  it("treats an item closed on the instrument's weekday as unavailable and follows its exception", async () => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false }),
      ),
    );
    // 2026-08-17 is a Monday.
    const closedMonday = {
      ...candidate(1, "IN1", "UTC"),
      openingHours: candidate(1, "IN1", "UTC").openingHours.filter((entry) =>
        [2, 3, 4, 5, 6, 7].includes(entry.dayOfWeek),
      ),
    };
    const lateMonday = {
      ...candidate(2, "IN2", "UTC"),
      openingHours: candidate(2, "IN2", "UTC").openingHours.map(
        (entry) =>
          [{ dayOfWeek: 1, start: "12:00", end: "18:00" }].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
      ),
    };

    const result = await loadAvailabilityQuickIndex(
      [closedMonday, lateMonday],
      new Date("2026-08-17T09:00:00Z"),
      "UTC",
      "00:00",
      "24:00",
      "token",
      new AbortController().signal,
    );

    expect(result.get("IN1")?.category).toBe("unavailable-today");
    expect(result.get("IN2")?.category).toBe("free-later-today");
  });

  it("does not request data without a token and loads counts once authenticated", async () => {
    let candidateRequests = 0;
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => {
        candidateRequests += 1;
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const { result, rerender } = renderHook(
      ({ token }: { token: string }) =>
        useAvailabilityQuickFilterIndex(token, "UTC", "00:00", "24:00", () => new Date("2026-08-17T09:00:00Z")),
      {
        wrapper,
        initialProps: {
          token: "",
        },
      },
    );
    expect(result.current.isPending).toBe(false);
    expect(candidateRequests).toBe(0);
    rerender({ token: "token" });
    await waitFor(() => expect(result.current.data).toBeDefined());
    rerender({ token: "token" });
    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(candidateRequests).toBe(1);
    expect(bookingRequests).toBe(1);
  });

  it("refreshes bookings, but not fresh candidates, at the next minute", async () => {
    vi.useFakeTimers();
    vi.setSystemTime("2026-08-17T09:00:30Z");
    let candidateRequests = 0;
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => {
        candidateRequests += 1;
        return HttpResponse.json(page([candidate(1, "IN1", "UTC")]));
      }),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const { result } = renderHook(() => useAvailabilityQuickFilterIndex("token"), { wrapper });
    await act(async () => vi.waitFor(() => expect(result.current.data).toBeDefined()));
    await act(() => vi.advanceTimersByTimeAsync(30_000));
    await act(async () => vi.waitFor(() => expect(bookingRequests).toBe(2)));
    expect(candidateRequests).toBe(1);
    vi.useRealTimers();
  });
  it.each([
    ["2026-08-17T12:00:00Z", "available-now"],
    ["2026-08-18T12:00:00Z", "free-later-today"],
    ["2026-08-19T12:00:00Z", "unavailable-today"],
  ])("classifies the independent hours at %s", async (time, category) => {
    server.use(
      http.get("/api/v2/bookings", () =>
        HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false }),
      ),
    );
    const result = await loadAvailabilityQuickIndex(
      [
        {
          ...candidate(1, "IN1", "UTC"),
          openingHours: [
            { dayOfWeek: 1, start: "08:00", end: "17:00" },
            { dayOfWeek: 2, start: "14:00", end: "16:00" },
          ],
        },
      ],
      new Date(time),
      "UTC",
      "00:00",
      "24:00",
      "token",
      new AbortController().signal,
    );
    expect(result.get("IN1")?.category).toBe(category);
  });

  it("recomputes availability when the weekday schedule changes within the same minute", async () => {
    let configuration = {
      ...candidate(1, "IN1", "UTC"),
      openingHours: [{ dayOfWeek: 1, start: "08:00", end: "17:00" }],
    };
    let bookingRequests = 0;
    server.use(
      http.get("/api/v2/booking-catalogue", () => HttpResponse.json(page([configuration]))),
      http.get("/api/v2/bookings", () => {
        bookingRequests += 1;
        return HttpResponse.json({ docs: [], totalDocs: 0, totalPages: 0, page: 1, hasNextPage: false });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children);
    const clock = () => new Date("2026-08-17T09:00:00Z");
    const { result } = renderHook(() => useAvailabilityQuickFilterIndex("token", "UTC", "00:00", "24:00", clock), {
      wrapper,
    });
    await waitFor(() => expect(result.current.data?.get("IN1")?.category).toBe("available-now"));

    configuration = { ...configuration, openingHours: [{ dayOfWeek: 1, start: "12:00", end: "18:00" }] };
    await act(() =>
      queryClient.refetchQueries({ queryKey: ["api-v2", "booking-catalogue", "availability-candidates"] }),
    );

    await waitFor(() => expect(result.current.data?.get("IN1")?.category).toBe("free-later-today"));
    expect(bookingRequests).toBe(2);
  });
});
