import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import { getSearchBookingCatalogueQueryKey } from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { dropInvalidMeRules } from "@/modules/common/table-list/presets";
import type { FilterState } from "@/modules/common/table-list/types";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { availabilityMode, createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  deriveAvailabilityCandidateFilter,
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
  const { data: currentUser } = useCurrentUserQuery();
  const runtimeSelectors = useMemo(() => (where ? rsqlSelectors(where) : []), [where]);
  const runtimeFieldState = useApiV2RuntimeFields({
    resource: apiV2Resources["booking-configurations"],
    selectors: runtimeSelectors,
    request: { authScope: currentUser.id },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: ["availability", "targetOwner"],
      }),
    [runtimeFieldState.metadata, runtimeFieldState.runtimeFields, sourceConfig],
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
  const invalidFilter =
    !runtimeFieldState.pending && runtimeFieldState.error === null && Boolean(where && !parsedExpression);
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
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
    clock,
    candidateWhere,
    !runtimeFilterBlocked,
    currentUser.id,
    { q, types },
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
    dropped,
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
