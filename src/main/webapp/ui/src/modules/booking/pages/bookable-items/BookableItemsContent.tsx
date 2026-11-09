import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { requireBookingValue } from "@/modules/booking/domain/bookingMapping";
import { useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import {
  getCountBookingCatalogueAvailabilityQueryKey,
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
  getSearchCalendarCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import {
  deleteManyBookingConfigurations,
  getCountBookingConfigurationsQueryKey,
  getGetBookingCalendarSubscriptionQueryKey,
  getGetBookingConfigurationAccessQueryKey,
  getGetBookingConfigurationQueryKey,
  getGetMyBookingNotificationSubscriptionQueryKey,
  getListBookingConfigurationAuditEventsQueryKey,
  getListBookingConfigurationsQueryKey,
  getSearchBookingConfigurationAccessGranteesQueryKey,
  invalidateGetBookingCalendarSubscription,
  invalidateListBookingConfigurations,
  updateManyBookingConfigurations,
  useDeleteManyBookingConfigurations,
  useUpdateManyBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import {
  getCountBookingsQueryKey,
  getListBookingsQueryKey,
  invalidateListBookings,
} from "@/modules/common/api/v2/generated/bookings/bookings";
import {
  getGetUserBookingCalendarSubscriptionQueryKey,
  getListMyBookableItemCalendarSubscriptionsQueryKey,
} from "@/modules/common/api/v2/generated/users/users";
import { BookingConfigurationsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "./ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "./BookableItemActionTriggers";
import { BookableItemsBulkActions } from "./BookableItemsBulkActions";
import { bookableItemBulkNotice, bookableItemLifecycleNotice, useBookableItemNoticeIds } from "./bookableItemFeedback";
import { type BookableItemsBulkAction, lifecycleErrorKey, requiredVersion } from "./bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "./bookableItemsAdministrationAccess";
import {
  archiveBookingConfiguration,
  type BookingConfigurationRow,
  bookingConfigurationConfig,
  permanentlyDeleteBookingConfiguration,
  restoreBookingConfiguration,
} from "./bookingConfiguration";
import { mapBookingConfiguration } from "./bookingConfigurationSchema";
import { PermanentDeleteBookableItemDialog } from "./PermanentDeleteBookableItemDialog";

const bookableItemsProjection = {
  fixed: [
    "id",
    "configurationVersion",
    "target",
    "enabled",
    "timezone",
    ...schedulingSettingsFieldNames,
    "updatedAt",
    "effectiveRole",
    "roleSources",
    "capabilities",
    "state",
  ],
} as const;

const maximumBookableItemsSelection = 1000;
type BookableItemsBulkMutation = {
  action: BookableItemsBulkAction;
  selectedRowIds: readonly string[];
};

function bulkParameters(selectedRowIds: readonly string[]) {
  if (selectedRowIds.length === 0) throw new Error("A bulk booking action requires at least one row ID");
  if (selectedRowIds.length > maximumBookableItemsSelection) {
    throw new Error(`A bulk booking action cannot contain more than ${maximumBookableItemsSelection} row IDs`);
  }

  const where = serializeRsqlExpression<BookingConfigurationRow>({
    kind: "comparison",
    field: "id",
    operator: "in",
    value: selectedRowIds,
  });
  return { where };
}

export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
): Promise<readonly { id: number }[] | null> {
  const params = bulkParameters(selectedRowIds);
  try {
    const response =
      action === "archive"
        ? await deleteManyBookingConfigurations(params)
        : await updateManyBookingConfigurations({ enabled: action === "enable" }, params);
    const docs = response?.docs;
    if (!docs || docs.some(({ id }) => id == null || !Number.isSafeInteger(id) || id < 1)) return null;
    return docs.map(({ id }) => ({ id: requireBookingValue(id, "id") }));
  } catch (error) {
    // A successful response with an unreadable receipt may already have committed.
    if (error instanceof v.ValiError || error instanceof SyntaxError) return null;
    throw error;
  }
}

export function BookableItemsContent() {
  const { t } = useTranslation(["booking", "common"]);

  const { data: currentUser } = useCurrentUserQuery();
  const queryClient = useQueryClient();
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
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const request = useMemo(
    () => ({
      authScope: currentUser.id,
      depth: 1,
      projection: bookableItemsProjection,
    }),
    [currentUser.id],
  );
  const table = useApiV2TableList({
    resource: apiV2Resources["booking-configurations"],
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationsReadSchema,
    mapDocument: mapBookingConfiguration,
    request,
    query: { keepPreviousData: true },
  });
  const itemNoticeIds = useBookableItemNoticeIds(table.tableProps.rows);
  const onArchive = useCallback((id: number, version: number) => archiveBookingConfiguration(id, version), []);
  const onPermanentDelete = useCallback(
    (id: number, version: number) => permanentlyDeleteBookingConfiguration(id, version),
    [],
  );
  const onChanged = useCallback(
    async (configurationId?: number) => {
      await Promise.all([
        invalidateListBookingConfigurations(queryClient),
        invalidateListBookings(queryClient),
        ...(configurationId === undefined
          ? []
          : [invalidateGetBookingCalendarSubscription(queryClient, configurationId)]),
      ]);
    },
    [queryClient],
  );
  const onCatalogueChanged = useCallback(
    async (configurationId: number) => {
      await Promise.all([
        onChanged(configurationId),
        queryClient.invalidateQueries({ queryKey: getSearchBookingCatalogueQueryKey() }),
      ]);
    },
    [onChanged, queryClient],
  );
  // Add stays hidden until the caller has an instrument to set up, so a direct URL offers no dead end.
  const canAdd = (useEligibleBookingTargets().data?.length ?? 0) > 0;
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration)),
    onSuccess: async (_data, configuration) => {
      alertsRef.current?.push(bookableItemLifecycleNotice("restored", configuration, t));
      await onCatalogueChanged(configuration.id);
    },
  });
  const restore = restoreMutation.mutateAsync;
  const onRestore = useCallback((configuration: BookingConfigurationRow) => restore(configuration), [restore]);
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkUpdateOperation = useUpdateManyBookingConfigurations();
  const bulkDeleteOperation = useDeleteManyBookingConfigurations();
  const bulkMutation = useMutation({
    mutationFn: async ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) => {
      const params = bulkParameters(mutationRowIds);
      try {
        const result =
          action === "archive"
            ? await bulkDeleteOperation.mutateAsync({ params })
            : await bulkUpdateOperation.mutateAsync({ data: { enabled: action === "enable" }, params });
        return result?.docs ?? null;
      } catch (error) {
        // A successful response with an unreadable receipt may already have committed.
        if (error instanceof v.ValiError || error instanceof SyntaxError) return null;
        throw error;
      }
    },
    onSuccess: async (docs, variables) => {
      if (variables.action !== "enable") {
        for (const id of docs?.map((doc) => doc.id) ?? variables.selectedRowIds.map(Number)) {
          alertsRef.current?.dismiss(itemNoticeIds.get(id) ?? `bookable-item-${id}`);
        }
      }
      alertsRef.current?.push(bookableItemBulkNotice(variables.action, docs?.length ?? null, t));

      await invalidateBookableItemRecords(queryClient, variables.action, docs ?? []);
      setSelectedRowIds(new Set());
      setFailedBulkAction(null);
    },
    onError: (_error, variables) => setFailedBulkAction(variables.action),
  });
  const onSelectionChange = (value: ReadonlySet<string>) => {
    setSelectedRowIds(value);
    setFailedBulkAction(null);
  };
  const mutateBulk = bulkMutation.mutateAsync;
  const onBulkAction = useCallback(
    async (action: BookableItemsBulkAction, actionRowIds: ReadonlySet<string>) => {
      setFailedBulkAction(null);
      await mutateBulk({ action, selectedRowIds: Array.from(actionRowIds) });
    },
    [mutateBulk],
  );
  const rowActions = useMemo<TableListRowActions<BookingConfigurationRow>>(
    () => ({
      id: "actions",
      label: t("bookableItems.fields.actions"),
      width: 88,
      minWidth: 80,
      renderCell: ({ row, activate }) => (
        <BookableItemActionTriggers
          configuration={row}
          activate={activate}
          directSysadmin={directSysadmin}
          onRestore={onRestore}
        />
      ),
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "archive" ? (
          <ArchiveBookableItemDialog
            configuration={row}
            close={close}
            onArchive={onArchive}
            onArchived={async (_id) => {
              alertsRef.current?.push(bookableItemLifecycleNotice("archived", row, t));
            }}
          />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={onPermanentDelete}
            onDeleted={async () => {
              alertsRef.current?.push(bookableItemLifecycleNotice("deleted", row, t));
            }}
          />
        ) : null,
    }),
    [directSysadmin, onArchive, onPermanentDelete, onRestore, t],
  );

  return (
    <main className="p-4 sm:p-8">
      {restoreMutation.isError ? (
        <p role="alert" className="mb-3 text-sm text-destructive">
          {t(
            lifecycleErrorKey(
              restoreMutation.error,
              "bookableItems.lifecycleErrors.restore",
              "bookableItems.lifecycleErrors.stale",
              "bookableItems.lifecycleErrors.stateChanged",
            ),
          )}
        </p>
      ) : null}
      <TableList
        {...table.tableProps}
        alertsRef={attachAlerts}
        headingClassName="text-2xl font-semibold"
        rowActions={rowActions}
        selection={
          directSysadmin
            ? {
                value: selectedRowIds,
                onChange: onSelectionChange,
                disabled: bulkMutation.isPending,
                maximumCount: maximumBookableItemsSelection,
                getRowLabel: (row) => row.target?.value.name ?? t("common:values.unknownItem"),
                renderActions: (selection) => (
                  <BookableItemsBulkActions
                    selection={selection}
                    disabled={bulkMutation.isPending}
                    activeAction={bulkMutation.isPending ? (bulkMutation.variables?.action ?? null) : null}
                    failedAction={failedBulkAction}
                    onAction={onBulkAction}
                  />
                ),
              }
            : undefined
        }
        createAction={
          canAdd ? (
            <Link to="/booking/bookable-items/add" className={cn(buttonVariants(), "rounded-sm")} data-slot="button">
              <PlusIcon aria-hidden="true" data-icon="inline-start" />
              {t("bookableItems.actions.add")}
            </Link>
          ) : undefined
        }
      />
    </main>
  );
}

async function invalidateBookableItemRecords(
  queryClient: QueryClient,
  action: BookableItemsBulkAction,
  docs: readonly { id?: number }[],
) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: getListBookingConfigurationsQueryKey() }, { cancelRefetch: false }),
    queryClient.invalidateQueries({ queryKey: getSearchBookingCatalogueLocationsQueryKey() }, { cancelRefetch: false }),
    queryClient.invalidateQueries({ queryKey: getSearchCalendarCatalogueQueryKey() }, { cancelRefetch: false }),
    queryClient.invalidateQueries(
      { queryKey: getCountBookingCatalogueAvailabilityQueryKey() },
      { cancelRefetch: false },
    ),
    queryClient.invalidateQueries({ queryKey: getCountBookingConfigurationsQueryKey() }, { cancelRefetch: false }),
    queryClient.invalidateQueries({ queryKey: getSearchBookingCatalogueQueryKey() }, { cancelRefetch: false }),
    ...docs.flatMap(({ id }) =>
      id === undefined
        ? []
        : [
            queryClient.invalidateQueries({ queryKey: getSearchBookingConfigurationAccessGranteesQueryKey(id) }),
            queryClient.invalidateQueries({ queryKey: getGetBookingConfigurationQueryKey(id) }),
            queryClient.invalidateQueries({ queryKey: getGetBookingConfigurationAccessQueryKey(id) }),
            queryClient.invalidateQueries({ queryKey: getListBookingConfigurationAuditEventsQueryKey(id) }),
            queryClient.invalidateQueries({ queryKey: getGetBookingCalendarSubscriptionQueryKey(id) }),
            queryClient.invalidateQueries({ queryKey: getGetMyBookingNotificationSubscriptionQueryKey(id) }),
          ],
    ),
    ...(action === "archive"
      ? [
          queryClient.invalidateQueries({ queryKey: getSearchCalendarEventsQueryKey() }, { cancelRefetch: false }),
          queryClient.invalidateQueries({ queryKey: getListBookingsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getCountBookingsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getListMyBookableItemCalendarSubscriptionsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getGetUserBookingCalendarSubscriptionQueryKey() }),
        ]
      : []),
  ]);
}
