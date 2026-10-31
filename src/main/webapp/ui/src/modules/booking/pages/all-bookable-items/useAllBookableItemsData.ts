import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { FilterState } from "@/modules/common/table-list/types";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { availabilityMode, createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  deriveAvailabilityCandidateFilter,
  fetchAvailabilityFilteredCatalogue,
  hasAvailabilityFilter,
  useAvailabilityQuickFilterIndex,
} from "./availabilityQuickFilters";

type AllBookableItemsDataOptions = {
  clock: () => Date;
  date?: string;
  routeAvailability?: AvailabilityQuickFilter;
  target?: string;
  mine: boolean;
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
  mine,
  where,
  q,
  types,
  page,
  pageSize,
}: AllBookableItemsDataOptions) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const sourceConfig = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
        (globalId) => commonT("tableList.filters.openRecord", { globalId }),
      ),
    [commonT, t],
  );
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const runtimeSelectors = useMemo(() => (where ? rsqlSelectors(where) : []), [where]);
  const runtimeFieldState = useApiV2RuntimeFields<AllBookableItem>({
    resourceName: "booking-configurations",
    selectors: runtimeSelectors,
    request: { token, authScope: currentUser.id },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: ["availability"],
        translate: (key) => String(t(key as never)),
      }),
    [runtimeFieldState.metadata, runtimeFieldState.runtimeFields, sourceConfig, t],
  );
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
  const invalidFilter =
    !runtimeFieldState.pending && runtimeFieldState.error === null && Boolean(where && !filters.expression);
  const runtimeFilterBlocked =
    runtimeFieldState.pending ||
    runtimeFieldState.error !== null ||
    runtimeFieldState.missing.length > 0 ||
    invalidFilter;
  const candidateFilter = deriveAvailabilityCandidateFilter(filters.expression);
  const candidateWhere = candidateFilter ? serializeRsqlExpression(candidateFilter) : undefined;
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
    candidateWhere,
    !runtimeFilterBlocked,
    currentUser.id,
    { q, types, mine },
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
          mine,
          availabilityIndexSignature,
          page,
          pageSize,
        ]
      : ["api-v2", "booking-catalogue", "all-items", token, q, types, mine, serverWhere, page, pageSize],
    queryFn: ({ signal }) => {
      if (usesAvailability) {
        if (!filters.expression || !quickIndex.data) {
          throw new Error("Availability filter search requires the quick-filter index");
        }
        return fetchAvailabilityFilteredCatalogue(
          filters.expression,
          quickIndex.data,
          { q, types, mine, page, pageSize },
          token,
          signal,
        );
      }
      return fetchBookingCatalogue({ q, types, mine, where: serverWhere, page, pageSize }, token, signal);
    },
    enabled: !runtimeFilterBlocked && (!usesAvailability || quickIndex.data !== undefined),
    staleTime: 30_000,
  });
  const rows = runtimeFilterBlocked ? [] : (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration);
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  const useQuickAvailability =
    selectedDate === userToday &&
    !quickIndex.isError &&
    (quickIndex.isPending || availabilityRows.every((row) => quickIndex.data?.has(row.globalId)));
  const availability = useCalendarAvailability(
    quickMode || useQuickAvailability ? [] : availabilityRows,
    bounds,
    token,
    currentUser.id,
  );

  return {
    config,
    currentUser,
    runtimeFieldState,
    preferences,
    userToday,
    selectedDate,
    filters,
    invalidFilter,
    runtimeFilterBlocked,
    quickMode,
    quickFilterLabels,
    bounds,
    quickIndex,
    quickFilterPending,
    quickFilterError,
    catalogue,
    rows,
    useQuickAvailability,
    availability,
  };
}
