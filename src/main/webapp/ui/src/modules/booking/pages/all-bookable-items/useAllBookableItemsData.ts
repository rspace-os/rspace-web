import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseRsqlExpression, serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import type { FilterState } from "@/modules/common/table-list/tableListState";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { availabilityMode, createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  fetchAvailabilityFilteredCatalogue,
  hasAvailabilityFilter,
  useAvailabilityQuickFilterIndex,
} from "./availabilityQuickFilters";

type AllBookableItemsDataOptions = {
  clock: () => Date;
  date?: string;
  routeAvailability?: AvailabilityQuickFilter;
  target?: string;
  where?: string;
  q?: string;
  types?: readonly string[];
  page: number;
  pageSize: number;
};

export function useAllBookableItemsData({
  clock,
  date,
  routeAvailability,
  target,
  where,
  q,
  types,
  page,
  pageSize,
}: AllBookableItemsDataOptions) {
  const { t } = useTranslation("booking");
  const config = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
      ),
    [t],
  );
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const userToday = todayInTimeZone(preferences.timeZone, clock());
  const selectedDate = date ?? userToday;
  const filters = useMemo<FilterState<AllBookableItem>>(
    () => ({
      search: q ?? "",
      expression: where ? parseRsqlExpression(where, config) : filterExpression(target, routeAvailability),
    }),
    [config, q, routeAvailability, target, where],
  );
  const invalidFilter = Boolean(where && !filters.expression);
  const usesAvailability = hasAvailabilityFilter(filters.expression);
  const quickMode = availabilityMode(filters.expression);
  const quickFilterLabels = {
    "available-now": t("allBookableItems.quickFilters.availableNow"),
    "free-later-today": t("allBookableItems.quickFilters.freeLaterToday"),
  } as const;
  const bounds = useMemo(
    () =>
      displayInterval(
        selectedDate,
        preferences.timeZone,
        preferences.availabilityWindow.start,
        preferences.availabilityWindow.end,
      ),
    [preferences.availabilityWindow.end, preferences.availabilityWindow.start, preferences.timeZone, selectedDate],
  );
  const quickIndex = useAvailabilityQuickFilterIndex(
    token,
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
    clock,
  );
  const quickFilterPending = usesAvailability && quickIndex.isPending;
  const quickFilterError = usesAvailability && quickIndex.isError;
  const serverFilter = usesAvailability ? null : filters.expression;
  const serverWhere = serverFilter ? serializeRsqlExpression(serverFilter) : undefined;
  const availabilityIndexSignature = usesAvailability
    ? quickIndex.data
      ? [...quickIndex.data]
          .map(([targetId, entry]) => [targetId, entry.category])
          .toSorted(([left], [right]) => left.localeCompare(right))
      : undefined
    : undefined;
  const catalogue = useQuery({
    queryKey: usesAvailability
      ? [
          "api-v2",
          "booking-catalogue",
          "all-items",
          token,
          filters.expression,
          q,
          types,
          availabilityIndexSignature,
          page,
          pageSize,
        ]
      : ["api-v2", "booking-catalogue", "all-items", token, q, types, serverWhere, page, pageSize],
    queryFn: ({ signal }) => {
      if (usesAvailability) {
        if (!filters.expression || !quickIndex.data) {
          throw new Error("Availability filter search requires the quick-filter index");
        }
        return fetchAvailabilityFilteredCatalogue(
          filters.expression,
          quickIndex.data,
          { q, types, page, pageSize },
          token,
          signal,
        );
      }
      return fetchBookingCatalogue({ q, types, where: serverWhere, page, pageSize }, token, signal);
    },
    enabled: !invalidFilter && (!usesAvailability || quickIndex.data !== undefined),
    staleTime: 30_000,
  });
  const rows = (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration);
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  const availability = useCalendarAvailability(quickMode ? [] : availabilityRows, bounds, token);

  return {
    config,
    preferences,
    userToday,
    selectedDate,
    filters,
    invalidFilter,
    quickMode,
    quickFilterLabels,
    bounds,
    quickIndex,
    quickFilterPending,
    quickFilterError,
    catalogue,
    rows,
    availability,
  };
}
