import type { QueryClient } from "@tanstack/react-query";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { bookingCatalogueQuery, catalogueItemAsConfiguration } from "@/modules/booking/domain/bookingCatalogue";
import { useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import {
  invalidateCountBookingConfigurations,
  invalidateGetBookingConfiguration,
  invalidateGetBookingConfigurationAccess,
  invalidateListBookingConfigurationAuditEvents,
  invalidateListBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { dropInvalidMeRules } from "@/modules/common/table-list/presets";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { FilterState } from "@/modules/common/table-list/types";
import { mutateBookableItems } from "../bookable-items/BookableItemsContent";
import { bookableItemBulkNotice, useBookableItemNoticeIds } from "../bookable-items/bookableItemFeedback";
import type { BookableItemsBulkAction } from "../bookable-items/bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "../bookable-items/bookableItemsAdministrationAccess";
import type { AllBookableItem } from "../calendar/availability/availabilityQuickFilters";
import { ownerPresetField } from "../ownerPreset";
import { createAllBookableItemsConfig, filterExpression } from "./allBookableItemsTableConfig";

type AllBookableItemsDataOptions = {
  target?: string;
  subjectId: number;
  where?: string;
  q?: string;
  types?: readonly string[];
  page: number;
  pageSize: number;
};

export function useAllBookableItemsData({
  target,
  subjectId,
  where,
  q,
  types,
  page,
  pageSize,
}: AllBookableItemsDataOptions) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  // Add disappears once the caller is known to have no instrument to set up, so it offers no dead
  // end; it stays while that answer loads so the toolbar does not shift for everyone else.
  const eligibleTargets = useEligibleBookingTargets().data;
  const canAdd = eligibleTargets === undefined || eligibleTargets.length > 0;
  const sourceConfig = useMemo(
    () => createAllBookableItemsConfig((globalId) => commonT("tableList.filters.openRecord", { globalId })),
    [commonT, t],
  );
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
  const clearSelection = useCallback(() => {
    setSelectedRowIds((current) => (current.size ? new Set() : current));
  }, []);
  const runtimeSelectors = where ? rsqlSelectors(where) : [];
  const runtimeFieldState = useApiV2RuntimeFields({
    resource: apiV2Resources["booking-configurations"],
    selectors: runtimeSelectors,
    request: { authScope: subjectId },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: [ownerPresetField.name],
      }),
    [runtimeFieldState.metadata, runtimeFieldState.runtimeFields, sourceConfig],
  );
  const recovered = useMemo(
    () => dropInvalidMeRules(where ? parseRsqlExpression(where, config) : filterExpression(target), ["targetOwner"]),
    [config, where, target],
  );
  const filters: FilterState<AllBookableItem> = { search: q ?? "", expression: recovered.expression };
  const droppedFilterFields = recovered.dropped;
  const invalidFilter =
    !runtimeFieldState.pending &&
    runtimeFieldState.error === null &&
    Boolean(where && !filters.expression && droppedFilterFields.length === 0);
  const runtimeFilterBlocked =
    runtimeFieldState.pending ||
    runtimeFieldState.error !== null ||
    runtimeFieldState.missing.length > 0 ||
    invalidFilter;
  const serverWhere = filters.expression ? serializeRsqlExpression(filters.expression) : undefined;
  // Read the table page from the catalogue.
  const scope = useQueryIdentityScope(subjectId);
  const catalogueOptions = bookingCatalogueQuery(scope, {
    q,
    totals: true,
    types,
    where: serverWhere,
    page,
    pageSize,
  });
  const catalogue = useQuery({
    ...catalogueOptions,
    enabled: !runtimeFilterBlocked,
  });
  const catalogueItems = catalogue.data?.items ?? [];
  const rows = runtimeFilterBlocked ? [] : catalogueItems.map(catalogueItemAsConfiguration);
  const itemNoticeIds = useBookableItemNoticeIds(rows);
  // An unfiltered empty catalogue means the user has no bookable items yet, not that a filter hid them.
  const hasNoBookableItems =
    catalogue.isSuccess && catalogue.data.total === 0 && !q && filters.expression === null && !target && !types?.length;
  // A disabled, archived or deleted item leaves this list, so it also leaves the selection.
  const onLifecycleChanged = useCallback(async (configurationIds: readonly number[]) => {
    setSelectedRowIds((current) => {
      const next = new Set(current);
      for (const id of configurationIds) next.delete(String(id));
      return next;
    });
  }, []);
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const bulkMutation = useMutation({
    mutationFn: ({ action, rowIds }: { action: BookableItemsBulkAction; rowIds: readonly string[] }) =>
      mutateBookableItems(action, rowIds),
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
  const selectionPending = bulkMutation.isPending;

  return {
    droppedFilterFields,
    config,
    runtimeFieldState,
    filters,
    invalidFilter,
    catalogue,
    rows,
    hasNoBookableItems,
    selectedRowIds,
    setSelectedRowIds,
    clearSelection,
    bulkMutation,
    canAdd,
    failedBulkAction,
    onLifecycleChanged,
    selectionPending,
    setFailedBulkAction,

    alertsRef,
    attachAlerts,
  };
}

async function _invalidateChangedConfigurations(queryClient: QueryClient, ids: readonly number[]) {
  await Promise.all([
    invalidateListBookingConfigurations(queryClient),
    invalidateCountBookingConfigurations(queryClient),
    ...ids.flatMap((id) => [
      invalidateGetBookingConfiguration(queryClient, id),
      invalidateGetBookingConfigurationAccess(queryClient, id),
      invalidateListBookingConfigurationAuditEvents(queryClient, id),
    ]),
  ]);
}
