import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingCatalogueQuery, catalogueItemAsConfiguration } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingConfigurationKeys, bookingKeys, bookingSubscriptionKeys } from "@/modules/booking/domain/bookingKeys";
import { updateBookingNotificationSubscriptions } from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { displayInterval } from "@/modules/booking/domain/bookingTime";
import { useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useAlignedMinute } from "@/modules/common/hooks/useAlignedMinute";
import { useApiV2Mutation } from "@/modules/common/queries/apiV2Mutation";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { FilterState } from "@/modules/common/table-list/types";
import { mutateBookableItems } from "../bookable-items/BookableItemsContent";
import { bookableItemBulkNotice, useBookableItemNoticeIds } from "../bookable-items/bookableItemFeedback";
import type { BookableItemsBulkAction } from "../bookable-items/bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "../bookable-items/bookableItemsAdministrationAccess";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  keepAcrossMinutes,
  serverAvailabilityFilter,
  todayAvailabilityWindow,
  useAvailabilityCounts,
  withAvailability,
} from "./availabilityQuickFilters";

type AllBookableItemsDataOptions = {
  clock: () => Date;
  date?: string;
  routeAvailability?: AvailabilityQuickFilter;
  target?: string;
  mine: boolean;
  subjectId: number;
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
  subjectId,
  where,
  q,
  types,
  page,
  pageSize,
}: AllBookableItemsDataOptions) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  // Add disappears once the caller is known to have no instrument to set up, so it offers no dead
  // end; it stays while that answer loads so the toolbar does not shift for everyone else.
  const eligibleTargets = useEligibleBookingTargets().data;
  const canAdd = eligibleTargets === undefined || eligibleTargets.length > 0;
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
  const _queryClient = useQueryClient();
  const alertsRef = useRef<TableListAlertsApi | null>(null);
  const [alertsReady, setAlertsReady] = useState(false);
  const attachAlerts = useCallback((api: TableListAlertsApi | null) => {
    alertsRef.current = api;
    setAlertsReady(api !== null);
  }, []);
  const deliverNotice = useCallback((alert: Parameters<TableListAlertsApi["push"]>[0]) => {
    if (!alertsRef.current) return false;
    alertsRef.current.push(alert);
    return true;
  }, []);
  useBookingNoticeHost("bookable-items", deliverNotice, alertsReady);
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [notificationFeedback, setNotificationFeedback] = useState<{ enabled: boolean; count: number }>();
  const runtimeSelectors = where ? rsqlSelectors(where) : [];
  const runtimeFieldState = useApiV2RuntimeFields<AllBookableItem>({
    resourceName: "booking-configurations",
    selectors: runtimeSelectors,
    request: { token, authScope: subjectId },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: ["availability"],
        i18n,
        language: i18n.resolvedLanguage ?? i18n.language,
      }),
    [
      i18n,
      i18n.resolvedLanguage,
      i18n.language,
      runtimeFieldState.metadata,
      runtimeFieldState.runtimeFields,
      sourceConfig,
    ],
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
  // The server applies availability as one top-level rule; an availability rule nested in an OR
  // group, which only a hand-written saved view can hold, is reported like an unknown field.
  const serverFilter = serverAvailabilityFilter(filters.expression);
  const invalidFilter =
    !runtimeFieldState.pending &&
    runtimeFieldState.error === null &&
    (Boolean(where && !filters.expression) || !serverFilter.supported);
  const runtimeFilterBlocked =
    runtimeFieldState.pending ||
    runtimeFieldState.error !== null ||
    runtimeFieldState.missing.length > 0 ||
    invalidFilter;
  const quickMode = serverFilter.supported ? serverFilter.availability : undefined;
  const quickFilterText = {
    "available-now": {
      label: t("allBookableItems.quickFilters.availableNow"),
      description: t("allBookableItems.quickFilters.availableNowDescription"),
    },
    "free-later-today": {
      label: t("allBookableItems.quickFilters.freeLaterToday"),
      description: t("allBookableItems.quickFilters.freeLaterTodayDescription"),
    },
  } as const;
  const bounds = displayInterval(
    selectedDate,
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
  );
  const minute = useAlignedMinute(clock);
  const today = todayAvailabilityWindow(
    new Date(minute),
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
  );
  const serverWhere =
    serverFilter.supported && serverFilter.where ? serializeRsqlExpression(serverFilter.where) : undefined;
  const serverAvailability = serverFilter.supported ? serverFilter.availability : undefined;
  // Counts describe the item rules alone, so each chip shows what selecting it would find.
  const countsFilter = withAvailability(filters.expression, undefined);
  const counts = useAvailabilityCounts(
    token,
    subjectId,
    { q, totals: true, types, mine, where: countsFilter ? serializeRsqlExpression(countsFilter) : undefined },
    today,
    !runtimeFilterBlocked,
  );
  // The table reads its own page; the server applies an availability rule before paging.
  const scope = useQueryIdentityScope(subjectId);
  const catalogueOptions = bookingCatalogueQuery(
    scope,
    { q, totals: true, types, mine, where: serverWhere, page, pageSize, availability: serverAvailability, availabilityWindow: today },
    token,
  );
  const catalogueQueryKey = catalogueOptions.queryKey;
  const catalogue = useQuery({
    ...catalogueOptions,
    enabled: !runtimeFilterBlocked && token.length > 0,
    placeholderData: serverAvailability ? keepAcrossMinutes(catalogueQueryKey) : undefined,
  });
  const catalogueItems = catalogue.data?.items ?? [];
  const rows = runtimeFilterBlocked ? [] : catalogueItems.map(catalogueItemAsConfiguration);
  const itemNoticeIds = useBookableItemNoticeIds(rows);
  // An unfiltered empty catalogue means the user has no bookable items yet, not that a filter hid them.
  const hasNoBookableItems =
    catalogue.isSuccess &&
    catalogue.data.total === 0 &&
    !q &&
    !where &&
    !target &&
    !routeAvailability &&
    !mine &&
    !types?.length;
  const notificationSubscriptionMutation = useApiV2Mutation({
    meta: { invalidates: [bookingSubscriptionKeys.notifications(subjectId)] },
    mutationFn: ({ configurationIds, enabled }: { configurationIds: readonly number[]; enabled: boolean }) =>
      updateBookingNotificationSubscriptions(configurationIds, enabled, token),
    onMutate: () => setNotificationFeedback(undefined),
    onSuccess: async (subscriptions, variables) => {
      setSelectedRowIds(new Set());
      setNotificationFeedback({ enabled: variables.enabled, count: subscriptions.length });
    },
  });
  // A disabled, archived or deleted item leaves this list, so it also leaves the selection.
  const onLifecycleChanged = useCallback(async (configurationIds: readonly number[]) => {
    setSelectedRowIds((current) => {
      const next = new Set(current);
      for (const id of configurationIds) next.delete(String(id));
      return next;
    });
  }, []);
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const bulkMutation = useApiV2Mutation({
    meta: { invalidates: [bookingConfigurationKeys.all, bookingKeys.all] },
    mutationFn: ({ action, rowIds }: { action: BookableItemsBulkAction; rowIds: readonly string[] }) =>
      mutateBookableItems(action, rowIds, token),
    onMutate: () => setFailedBulkAction(null),
    onSuccess: async (docs, { action, rowIds }) => {
      if (action !== "enable") {
        for (const id of docs?.map((doc) => doc.id) ?? rowIds.map(Number)) {
          alertsRef.current?.dismiss(itemNoticeIds.get(id) ?? `bookable-item-${id}`);
        }
      }
      alertsRef.current?.push(bookableItemBulkNotice(action, docs?.length ?? null, t));
      await onLifecycleChanged(rowIds.map(Number));
    },
    onError: (_error, { action }) => setFailedBulkAction(action),
  });
  const selectionPending = notificationSubscriptionMutation.isPending || bulkMutation.isPending;
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  // A quick filter always describes today, so its rows show today's bars and book today.
  const rowsDate = quickMode ? today.date : selectedDate;
  const rowsBounds = quickMode ? today.bounds : bounds;
  const availability = useCalendarAvailability(availabilityRows, rowsBounds, token, subjectId);

  return {
    config,
    runtimeFieldState,
    preferences,
    userToday,
    selectedDate,
    filters,
    invalidFilter,
    quickMode,
    catalogue,
    rows,
    hasNoBookableItems,
    selectedRowIds,
    setSelectedRowIds,
    notificationFeedback,
    notificationSubscriptionMutation,
    availability,
    bulkMutation,
    canAdd,
    counts,
    failedBulkAction,
    minute,
    onLifecycleChanged,
    rowsBounds,
    rowsDate,
    selectionPending,
    setFailedBulkAction,
    token,
    quickFilterText,
    alertsRef,
    attachAlerts,
  };
}
