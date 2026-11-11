import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { getSearchBookingCatalogueQueryKey } from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseRsqlExpression, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { dropInvalidMeRules } from "@/modules/common/table-list/presets";
import type { FilterState } from "@/modules/common/table-list/types";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { availabilityMode, createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  fetchAvailabilityFilteredCatalogue,
  hasAvailabilityFilter,
  resolveAvailabilityFilters,
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
  const { data: currentUser } = useCurrentUserQuery();
  const config = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
      ),
    [t],
  );
  const preferences = useBookingDisplayPreferences();
  const userToday = todayInTimeZone(preferences.timeZone, clock());
  const selectedDate = date ?? userToday;
  const parsedExpression = useMemo(
    () => where ? parseRsqlExpression(where, config) : filterExpression(target, routeAvailability),
    [config, routeAvailability, target, where],
  );
  const { expression, dropped } = dropInvalidMeRules(parsedExpression, ["targetOwner"]);
  const filters: FilterState<AllBookableItem> = { search: q ?? "", expression };
  const invalidFilter = Boolean(where && !parsedExpression);
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
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
    clock,
  );
  const quickFilterPending = usesAvailability && quickIndex.isPending;
  const quickFilterError = usesAvailability && quickIndex.isError;
  const serverFilter = usesAvailability ? null : filters.expression;
  const serverWhere = serverFilter ? serializeRsqlExpression(serverFilter) : undefined;
  const _availabilityIndexSignature = usesAvailability
    ? quickIndex.data
      ? [...quickIndex.data]
          .map(([targetId, entry]) => [targetId, entry.category])
          .toSorted(([left], [right]) => left.localeCompare(right))
      : undefined
    : undefined;
  const catalogue = useQuery({
    queryKey: [
      ...getSearchBookingCatalogueQueryKey({
        q,
        type: types ? [...types] : undefined,
        where: filters.expression
          ? serializeRsqlExpression(
              resolveAvailabilityFilters(filters.expression, quickIndex.data) ?? filters.expression,
            )
          : undefined,
        page,
        limit: pageSize,
      }),
      "allItems",
      currentUser.id,
    ],
    queryFn: ({ signal }) => {
      if (usesAvailability) {
        if (!filters.expression || !quickIndex.data) {
          throw new Error("Availability filter search requires the quick-filter index");
        }
        return fetchAvailabilityFilteredCatalogue(
          filters.expression,
          quickIndex.data,
          { q, types, page, pageSize },
          signal,
        );
      }
      return fetchBookingCatalogue({ q, types, where: serverWhere, page, pageSize }, signal);
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
  const availability = useCalendarAvailability(quickMode ? [] : availabilityRows, bounds);

  return {
    config,
    preferences,
    userToday,
    selectedDate,
    filters,
    dropped,
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
