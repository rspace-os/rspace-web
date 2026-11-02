import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import { type AvailabilityInterval, classifyCurrentDayAvailability } from "@/modules/booking/domain/availability";
import {
  assertBookingCataloguePage,
  type BookingCatalogueItem,
  BookingCatalogueRequestTooLargeError,
  type BookingCatalogueSearch,
  bookingCatalogueRequestFits,
  fetchAllBookingCataloguePages,
  fetchBookingCataloguePageWithinSafeBudget,
} from "@/modules/booking/domain/bookingCatalogue";
import { type AbsoluteDisplayInterval, currentWallClock, displayInterval } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/common/hooks/useAlignedMinute";
import { navigationProgressQueryMeta } from "@/modules/common/queries/navigationProgress";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { FilterExpression, FilterValue } from "@/modules/common/table-list/types";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { loadCalendarAvailability } from "../calendar/calendarAvailability";

type CurrentDayAvailability = ReturnType<typeof classifyCurrentDayAvailability>;
type BookingCataloguePage = Awaited<ReturnType<typeof fetchAllBookingCataloguePages>>;

export type AvailabilityQuickFilter = "available-now" | "free-later-today";
export type AllBookableItem = BookingConfiguration & {
  target: (NonNullable<BookingConfiguration["target"]> & { location?: BookingCatalogueItem["location"] }) | null;
  availability?: AvailabilityQuickFilter;
};

export function hasAvailabilityFilter(expression: FilterExpression<AllBookableItem> | null): boolean {
  return (
    expression !== null &&
    (expression.kind === "comparison"
      ? expression.field === "availability"
      : expression.children.some(hasAvailabilityFilter))
  );
}

/** Replaces only availability predicates; other rules and their grouping remain intact. */
export function withAvailability(
  expression: FilterExpression<AllBookableItem> | null,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  let remaining = expression;
  if (expression?.kind === "comparison") {
    if (expression.field === "availability") remaining = null;
  } else if (expression) {
    const children = expression.children.flatMap((child) => {
      const next = withAvailability(child, undefined);
      return next ? [next] : [];
    });
    remaining = children.length === 0 ? null : children.length === 1 ? children[0] : { ...expression, children };
  }
  if (!availability) return remaining;
  const rule: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "availability",
    operator: "equals",
    value: availability,
  };
  return remaining
    ? { kind: "and", children: remaining.kind === "and" ? [...remaining.children, rule] : [remaining, rule] }
    : rule;
}

/** Converts the derived availability field to ordinary server-side target predicates. */
export function resolveAvailabilityFilters(
  expression: FilterExpression<AllBookableItem> | null,
  index: ReadonlyMap<string, AvailabilityQuickIndexEntry> | undefined,
): FilterExpression<AllBookableItem> | null {
  if (!expression) return null;
  if (expression.kind !== "comparison")
    return {
      ...expression,
      children: expression.children.map((child) => resolveAvailabilityFilters(child, index) ?? child),
    };
  if (expression.field !== "availability") return expression;
  const ids = [...(index ?? [])].flatMap(([id, entry]) => (entry.category === expression.value ? [id] : []));
  return ids.length > 0
    ? { kind: "comparison", field: "target", operator: "in", value: ids }
    : { kind: "comparison", field: "id", operator: "equals", value: 0 };
}

export type AvailabilityQuickIndexEntry = {
  date: string;
  bounds: AbsoluteDisplayInterval;
  intervals: readonly AvailabilityInterval[];
  category: CurrentDayAvailability;
};

type SimplifiedAvailabilityFilter = boolean | FilterExpression<AllBookableItem>;
type AvailabilityComparison = Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>;

const CATALOGUE_PAGE_SIZE = 100;
const MAX_CATALOGUE_TARGETS_PER_BATCH = 500;
const MAX_FILTER_ARGUMENTS = 1000;
const MAX_FILTER_COMPARISONS = 50;
const MAX_FILTER_NESTING = 10;
const MAX_ENCODED_WHERE_BYTES = 32_768;
const MAX_AVAILABILITY_CANDIDATES = 1000;
const MATCHES_NOTHING: FilterExpression<AllBookableItem> = {
  kind: "comparison",
  field: "id",
  operator: "equals",
  value: 0,
};

class FilterRequestLimitError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FilterRequestLimitError";
  }
}

class FilterNestingLimitError extends FilterRequestLimitError {
  constructor() {
    super("Booking catalogue filter exceeds the parser nesting limit");
    this.name = "FilterNestingLimitError";
  }
}

function throwIfAborted(signal: AbortSignal): void {
  if (signal.aborted) throw signal.reason ?? new DOMException("The operation was aborted", "AbortError");
}

function simplifyAvailabilityFilter(
  expression: FilterExpression<AllBookableItem> | null,
  availabilityMatches: (comparison: AvailabilityComparison) => boolean,
): SimplifiedAvailabilityFilter {
  if (!expression) return true;
  if (expression.kind === "comparison") {
    return expression.field === "availability" ? availabilityMatches(expression) : expression;
  }
  const children = expression.children.map((child) => simplifyAvailabilityFilter(child, availabilityMatches));
  if (expression.kind === "and") {
    if (children.includes(false)) return false;
    const remaining = children.filter((child): child is FilterExpression<AllBookableItem> => child !== true);
    if (remaining.length === 0) return true;
    if (remaining.length === 1) return remaining[0];
    return { ...expression, children: remaining };
  }
  if (children.includes(true)) return true;
  const remaining = children.filter((child): child is FilterExpression<AllBookableItem> => child !== false);
  if (remaining.length === 0) return false;
  if (remaining.length === 1) return remaining[0];
  return { ...expression, children: remaining };
}

function collectAvailabilityValues(expression: FilterExpression<AllBookableItem>, values: Set<FilterValue>): void {
  if (expression.kind === "comparison") {
    if (expression.field === "availability") values.add(expression.value);
    return;
  }
  for (const child of expression.children) collectAvailabilityValues(child, values);
}

function filterExpression(filter: SimplifiedAvailabilityFilter): FilterExpression<AllBookableItem> | undefined {
  if (filter === true) return undefined;
  return filter === false ? MATCHES_NOTHING : filter;
}

function logicalFilterNesting(expression: FilterExpression<AllBookableItem>): number {
  if (expression.kind === "comparison") return 0;
  return 1 + Math.max(0, ...expression.children.map(logicalFilterNesting));
}

function serializedParenthesisNesting(where: string): number {
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

function assertFilterNesting(expression: FilterExpression<AllBookableItem>, where: string): void {
  if (
    logicalFilterNesting(expression) > MAX_FILTER_NESTING ||
    serializedParenthesisNesting(where) > MAX_FILTER_NESTING
  ) {
    throw new FilterNestingLimitError();
  }
}

function assertFilterRequestLimits(expression: FilterExpression<AllBookableItem>, where: string): void {
  let comparisons = 0;
  let argumentsCount = 0;
  const count = (node: FilterExpression<AllBookableItem>): void => {
    if (node.kind !== "comparison") {
      for (const child of node.children) count(child);
      return;
    }
    comparisons += 1;
    argumentsCount +=
      node.operator === "in" || node.operator === "notIn" ? (Array.isArray(node.value) ? node.value.length : 1) : 1;
  };
  count(expression);
  if (comparisons > MAX_FILTER_COMPARISONS) {
    throw new FilterRequestLimitError("Booking catalogue filter exceeds the parser comparison limit");
  }
  if (argumentsCount > MAX_FILTER_ARGUMENTS) {
    throw new FilterRequestLimitError("Booking catalogue filter exceeds the parser argument limit");
  }
  assertFilterNesting(expression, where);
  const encodedWhere = new URLSearchParams({ where }).toString().slice("where=".length);
  if (new TextEncoder().encode(encodedWhere).length > MAX_ENCODED_WHERE_BYTES) {
    throw new FilterRequestLimitError("Booking catalogue filter exceeds the encoded where limit");
  }
}

function serializeAvailabilityFilter(filter: SimplifiedAvailabilityFilter): string | undefined {
  const expression = filterExpression(filter);
  if (!expression) return undefined;
  const where = serializeRsqlExpression(expression);
  assertFilterRequestLimits(expression, where);
  return where;
}

function restrictToTargets(
  filter: SimplifiedAvailabilityFilter,
  targetIds: readonly string[],
): FilterExpression<AllBookableItem> {
  const targetFilter: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "target",
    operator: "in",
    value: targetIds,
  };
  if (filter === true) return targetFilter;
  return {
    kind: "and",
    children: [filter === false ? MATCHES_NOTHING : filter, targetFilter],
  };
}

function serializeScopedFilter(filter: SimplifiedAvailabilityFilter, targetIds: readonly string[]): string {
  const expression = restrictToTargets(filter, targetIds);
  const where = serializeRsqlExpression(expression);
  assertFilterRequestLimits(expression, where);
  return where;
}

type CatalogueTargetScope = "where" | "parameter";

function catalogueSearchForTargets(
  search: Pick<BookingCatalogueSearch, "q" | "types">,
  filter: SimplifiedAvailabilityFilter,
  targetIds: readonly string[],
  scope: CatalogueTargetScope,
) {
  if (scope === "parameter") {
    if (targetIds.length !== 1) {
      throw new Error("Catalogue target parameter scope requires exactly one target");
    }
    const [targetId] = targetIds;
    if (targetId === undefined || targetId.trim() === "") {
      throw new Error("Catalogue target parameter scope requires a target ID");
    }
    return { ...catalogueSearch(search, serializeAvailabilityFilter(filter)), target: targetId };
  }
  return catalogueSearch(search, serializeScopedFilter(filter, targetIds));
}

function targetScopeForFilters(
  filters: readonly SimplifiedAvailabilityFilter[],
  targetId: string,
): CatalogueTargetScope {
  let nestingWouldExceedLimit = false;
  for (const filter of filters) {
    const expression = filterExpression(filter);
    if (!expression) continue;
    const where = serializeRsqlExpression(expression);
    assertFilterNesting(expression, where);
    try {
      serializeScopedFilter(filter, [targetId]);
    } catch (error) {
      if (error instanceof FilterNestingLimitError) {
        nestingWouldExceedLimit = true;
      } else if (!(error instanceof FilterRequestLimitError)) {
        throw error;
      }
    }
  }
  return nestingWouldExceedLimit ? "parameter" : "where";
}

function partitionCatalogueTargets(
  targetIds: readonly string[],
  fits: (targetIds: readonly string[]) => boolean,
): readonly (readonly string[])[] {
  const batches: string[][] = [];
  let current: string[] = [];
  for (const targetId of targetIds) {
    const candidate = [...current, targetId];
    if (current.length >= MAX_CATALOGUE_TARGETS_PER_BATCH || !fits(candidate)) {
      if (current.length === 0) {
        throw new Error("One target and its saved filter exceed the catalogue parser or request-size limits");
      }
      batches.push(current);
      current = [targetId];
      if (!fits(current)) {
        throw new Error("One target and its saved filter exceed the catalogue parser or request-size limits");
      }
    } else {
      current = candidate;
    }
  }
  if (current.length > 0) batches.push(current);
  return batches;
}

function availabilityTargetGroups(
  expression: FilterExpression<AllBookableItem>,
  index: ReadonlyMap<string, AvailabilityQuickIndexEntry>,
): readonly (readonly [CurrentDayAvailability, readonly string[]])[] {
  const values = new Set<FilterValue>();
  collectAvailabilityValues(expression, values);
  const groups = new Map<CurrentDayAvailability, string[]>();
  for (const [targetId, entry] of index) {
    if (!values.has(entry.category)) continue;
    const group = groups.get(entry.category) ?? [];
    group.push(targetId);
    groups.set(entry.category, group);
  }
  return [...groups.entries()]
    .map(([category, targetIds]) => [category, targetIds.toSorted()] as const)
    .toSorted(([left], [right]) => left.localeCompare(right));
}

function catalogueSearch(search: Pick<BookingCatalogueSearch, "q" | "types">, where: string | undefined) {
  return { ...search, where, page: 1, pageSize: CATALOGUE_PAGE_SIZE };
}

type ResidualChunk = {
  items: readonly BookingCatalogueItem[];
  facets?: BookingCataloguePage["facets"];
};

async function fetchResidualChunk(
  targetIds: readonly string[],
  specializedFilter: SimplifiedAvailabilityFilter,
  baselineFilter: SimplifiedAvailabilityFilter,
  targetScope: CatalogueTargetScope,
  search: Pick<BookingCatalogueSearch, "q" | "types">,
  token: string,
  signal: AbortSignal,
): Promise<ResidualChunk> {
  throwIfAborted(signal);
  const specializedSearch = catalogueSearchForTargets(search, specializedFilter, targetIds, targetScope);
  const baselineSearch = catalogueSearchForTargets(search, baselineFilter, targetIds, targetScope);
  try {
    const specialized = await fetchAllBookingCataloguePages(specializedSearch, token, signal, targetIds.length);
    const baseline = await fetchAllBookingCataloguePages(baselineSearch, token, signal, targetIds.length);
    const baselineIds = new Set(baseline.items.map((item) => item.configurationId));
    const residualById = new Map<number, BookingCatalogueItem>();
    for (const item of specialized.items) {
      if (!baselineIds.has(item.configurationId)) residualById.set(item.configurationId, item);
    }
    const items = [...residualById.values()];
    return { items, facets: items.length > 0 ? specialized.facets : undefined };
  } catch (error) {
    if (!(error instanceof BookingCatalogueRequestTooLargeError)) throw error;
    if (targetIds.length <= 1) {
      throw new Error("A single-target booking catalogue request exceeds the safe request budget");
    }
    const midpoint = Math.ceil(targetIds.length / 2);
    const left = await fetchResidualChunk(
      targetIds.slice(0, midpoint),
      specializedFilter,
      baselineFilter,
      targetScope,
      search,
      token,
      signal,
    );
    const right = await fetchResidualChunk(
      targetIds.slice(midpoint),
      specializedFilter,
      baselineFilter,
      targetScope,
      search,
      token,
      signal,
    );
    return { items: [...left.items, ...right.items], facets: left.facets ?? right.facets };
  }
}

async function mergeBaselineAndResiduals(
  firstBaselinePage: BookingCataloguePage,
  baselineWhere: string | undefined,
  residuals: readonly BookingCatalogueItem[],
  search: Pick<BookingCatalogueSearch, "q" | "types">,
  page: number,
  pageSize: number,
  token: string,
  signal: AbortSignal,
): Promise<readonly BookingCatalogueItem[]> {
  const offset = (page - 1) * pageSize;
  if (!Number.isSafeInteger(offset)) throw new Error("Booking catalogue page offset is out of range");
  const requestedEnd = offset + pageSize;
  if (!Number.isSafeInteger(requestedEnd)) throw new Error("Booking catalogue page end is out of range");
  const baselinePageCount = Math.ceil(firstBaselinePage.total / CATALOGUE_PAGE_SIZE);
  let baselinePage = firstBaselinePage;
  let baselinePageNumber = 1;
  let baselineIndex = 0;
  let residualIndex = 0;
  let mergedRows = 0;
  const result: BookingCatalogueItem[] = [];
  while (mergedRows < requestedEnd) {
    if (baselineIndex >= baselinePage.items.length && baselinePageNumber < baselinePageCount) {
      baselinePageNumber += 1;
      baselinePage = await fetchBookingCataloguePageWithinSafeBudget(
        { ...catalogueSearch(search, baselineWhere), page: baselinePageNumber },
        token,
        signal,
      );
      assertBookingCataloguePage(baselinePage, baselinePageNumber, firstBaselinePage.total);
      baselineIndex = 0;
    }
    const baselineItem = baselinePage.items[baselineIndex];
    const residualItem = residuals[residualIndex];
    if (!baselineItem && !residualItem) break;
    let next: BookingCatalogueItem;
    if (baselineItem && residualItem) {
      if (baselineItem.configurationId <= residualItem.configurationId) {
        next = baselineItem;
        baselineIndex += 1;
        if (baselineItem.configurationId === residualItem.configurationId) residualIndex += 1;
      } else {
        next = residualItem;
        residualIndex += 1;
      }
    } else if (baselineItem) {
      next = baselineItem;
      baselineIndex += 1;
    } else if (residualItem) {
      next = residualItem;
      residualIndex += 1;
    } else {
      break;
    }
    if (mergedRows >= offset) result.push(next);
    mergedRows += 1;
  }
  return result;
}

export async function fetchAvailabilityFilteredCatalogue(
  expression: FilterExpression<AllBookableItem>,
  index: ReadonlyMap<string, AvailabilityQuickIndexEntry>,
  search: Pick<BookingCatalogueSearch, "q" | "types"> & { page: number; pageSize: number },
  token: string,
  signal: AbortSignal,
): Promise<BookingCataloguePage> {
  if (!hasAvailabilityFilter(expression)) {
    throw new Error("Availability catalogue search requires an availability filter");
  }
  if (
    !Number.isSafeInteger(search.page) ||
    search.page < 1 ||
    !Number.isSafeInteger(search.pageSize) ||
    search.pageSize < 1 ||
    search.pageSize > CATALOGUE_PAGE_SIZE
  ) {
    throw new Error("Booking catalogue page is out of range");
  }
  if (index.size > MAX_AVAILABILITY_CANDIDATES) {
    throw new Error("Availability quick filters support at most 1,000 bookable items");
  }
  throwIfAborted(signal);

  const originalWhere = serializeRsqlExpression(expression);
  assertFilterNesting(expression, originalWhere);
  const baselineFilter = simplifyAvailabilityFilter(expression, () => false);
  const baselineWhere = serializeAvailabilityFilter(baselineFilter);
  const firstBaselinePage = await fetchBookingCataloguePageWithinSafeBudget(
    { ...catalogueSearch(search, baselineWhere), page: 1 },
    token,
    signal,
  );
  assertBookingCataloguePage(firstBaselinePage, 1);

  const residualById = new Map<number, BookingCatalogueItem>();
  let residualFacets: BookingCataloguePage["facets"] | undefined;
  for (const [category, targetIds] of availabilityTargetGroups(expression, index)) {
    const specializedFilter = simplifyAvailabilityFilter(expression, (comparison) => comparison.value === category);
    if (specializedFilter === false) continue;
    const firstTargetId = targetIds[0];
    if (firstTargetId === undefined) throw new Error("Availability target group must not be empty");
    const targetScope = targetScopeForFilters([specializedFilter, baselineFilter], firstTargetId);
    const batches = partitionCatalogueTargets(targetIds, (batch) => {
      if (targetScope === "parameter" && batch.length !== 1) return false;
      try {
        const specializedSearch = catalogueSearchForTargets(search, specializedFilter, batch, targetScope);
        const scopedBaselineSearch = catalogueSearchForTargets(search, baselineFilter, batch, targetScope);
        return (
          bookingCatalogueRequestFits(specializedSearch, token) &&
          bookingCatalogueRequestFits(scopedBaselineSearch, token)
        );
      } catch (error) {
        if (error instanceof FilterRequestLimitError) return false;
        throw error;
      }
    });
    for (const batch of batches) {
      throwIfAborted(signal);
      const chunk = await fetchResidualChunk(
        batch,
        specializedFilter,
        baselineFilter,
        targetScope,
        search,
        token,
        signal,
      );
      if (chunk.items.length > 0 && !residualFacets) residualFacets = chunk.facets;
      for (const item of chunk.items) residualById.set(item.configurationId, item);
      if (residualById.size > MAX_AVAILABILITY_CANDIDATES) {
        throw new Error("Availability catalogue residual exceeded the 1,000-candidate bound");
      }
    }
  }

  const residuals = [...residualById.values()].toSorted((left, right) => left.configurationId - right.configurationId);
  const total = firstBaselinePage.total + residuals.length;
  if (!Number.isSafeInteger(total)) throw new Error("Booking catalogue total is out of range");
  const offset = (search.page - 1) * search.pageSize;
  if (!Number.isSafeInteger(offset)) throw new Error("Booking catalogue page offset is out of range");
  const items =
    offset >= total
      ? []
      : await mergeBaselineAndResiduals(
          firstBaselinePage,
          baselineWhere,
          residuals,
          search,
          search.page,
          search.pageSize,
          token,
          signal,
        );
  throwIfAborted(signal);
  return {
    ...firstBaselinePage,
    items: [...items],
    page: search.page,
    pageSize: search.pageSize,
    total,
    facets: firstBaselinePage.total > 0 ? firstBaselinePage.facets : (residualFacets ?? firstBaselinePage.facets),
  };
}

const AvailabilityCandidateSchema = v.object({
  target: v.nullable(v.object({ globalId: v.string() })),
  timezone: v.string(),
  ...schedulingSettingsEntries,
});

type AvailabilityCandidate = v.InferOutput<typeof AvailabilityCandidateSchema>;

const CandidatePageSchema = v.object({
  docs: v.array(AvailabilityCandidateSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
});

export class AvailabilityCandidateLimitError extends Error {
  constructor() {
    super("Availability quick filters support at most 1,000 bookable items");
    this.name = "AvailabilityCandidateLimitError";
  }
}

const now = () => new Date();
async function fetchCandidatePage(page: number, token: string, signal: AbortSignal) {
  const parameters = new URLSearchParams({
    where: "enabled==true;state==ACTIVE;target.deleted==false",
    page: String(page),
    limit: "100",
    totals: "true",
    depth: "1",
    "fields[booking-configurations]":
      "target,timezone,slotGranularityMinutes,openingHours,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking",
  });
  const response = await fetchWithUiToken(`/api/v2/booking-configurations?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Bookable item request failed (${response.status})`);
  return v.parse(CandidatePageSchema, await response.json());
}

export async function fetchAvailabilityCandidates(
  token: string,
  signal: AbortSignal,
): Promise<readonly AvailabilityCandidate[]> {
  const first = await fetchCandidatePage(1, token, signal);
  if (first.totalDocs > 1000) throw new AvailabilityCandidateLimitError();
  const candidates = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    candidates.push(...(await fetchCandidatePage(page, token, signal)).docs);
  }
  return candidates.filter((candidate) => candidate.target !== null);
}

export async function loadAvailabilityQuickIndex(
  candidates: readonly AvailabilityCandidate[],
  current: Date,
  displayTimeZone: string,
  availabilityWindowStart: string,
  availabilityWindowEnd: string,
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, AvailabilityQuickIndexEntry>> {
  const date = currentWallClock(current.toISOString(), displayTimeZone).date;
  const bounds = displayInterval(date, displayTimeZone, availabilityWindowStart, availabilityWindowEnd);
  const rows = candidates.flatMap((candidate) =>
    candidate.target
      ? [
          {
            globalId: candidate.target.globalId,
            timezone: candidate.timezone,
            openingHours: candidate.openingHours,
            bufferBeforeMinutes: candidate.bufferBeforeMinutes,
            bufferAfterMinutes: candidate.bufferAfterMinutes,
            maxBookingDurationMinutes: candidate.maxBookingDurationMinutes,
            allowDoubleBooking: candidate.allowDoubleBooking,
          },
        ]
      : [],
  );
  const availability = await loadCalendarAvailability(rows, bounds, token, signal);
  const index = new Map<string, AvailabilityQuickIndexEntry>();
  for (const row of rows) {
    const intervals = availability.get(row.globalId) ?? [];
    index.set(row.globalId, {
      date,
      bounds,
      intervals,
      category: classifyCurrentDayAvailability(intervals, new Date(bounds.start), new Date(bounds.end), current),
    });
  }
  return index;
}

export function useAvailabilityQuickFilterIndex(
  token: string,
  displayTimeZone = "UTC",
  availabilityWindowStart = "00:00",
  availabilityWindowEnd = "24:00",
  clock: () => Date = now,
) {
  const enabled = token.length > 0;
  const minute = useAlignedMinute(clock);
  const candidates = useQuery({
    queryKey: ["api-v2", "booking-configurations", "availability-candidates"],
    queryFn: ({ signal }) => fetchAvailabilityCandidates(token, signal),
    enabled,
    staleTime: 60_000,
    retry: (failureCount, error) => !(error instanceof AvailabilityCandidateLimitError) && failureCount < 3,
  });
  const signature = candidates.data
    ?.flatMap((candidate) =>
      candidate.target
        ? [
            [
              candidate.target.globalId,
              candidate.timezone,
              JSON.stringify(candidate.openingHours),
              candidate.bufferBeforeMinutes,
              candidate.bufferAfterMinutes,
              candidate.maxBookingDurationMinutes,
              candidate.allowDoubleBooking,
            ] as const,
          ]
        : [],
    )
    .toSorted(([left], [right]) => left.localeCompare(right));
  const index = useQuery({
    queryKey: [
      "api-v2",
      "bookings",
      "availability-quick-index",
      signature,
      minute,
      displayTimeZone,
      availabilityWindowStart,
      availabilityWindowEnd,
    ],
    queryFn: ({ signal }) =>
      loadAvailabilityQuickIndex(
        candidates.data ?? [],
        new Date(minute),
        displayTimeZone,
        availabilityWindowStart,
        availabilityWindowEnd,
        token,
        signal,
      ),
    enabled: enabled && candidates.isSuccess,
    meta: navigationProgressQueryMeta,
  });
  return {
    data: enabled ? index.data : undefined,
    now: new Date(minute),
    isPending: enabled && !candidates.isError && (candidates.isPending || index.isPending),
    isError: enabled && (candidates.isError || index.isError),
    error: candidates.error ?? index.error,
    refetch: candidates.isError ? candidates.refetch : index.refetch,
  };
}
