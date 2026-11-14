import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import { getSearchCalendarEventsQueryKey } from "@/modules/common/api/v2/generated/booking-calendar/booking-calendar";
import {
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
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "../bookable-items/ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "../bookable-items/BookableItemActionTriggers";
import { BookableItemsBulkActions } from "../bookable-items/BookableItemsBulkActions";
import {
  type BookableItemsBulkAction,
  lifecycleErrorKey,
  requiredVersion,
} from "../bookable-items/bookableItemLifecycleHelpers";
import {
  archiveBookingConfiguration,
  type BookingConfigurationRow,
  bookingConfigurationConfig,
  permanentlyDeleteBookingConfiguration,
  restoreBookingConfiguration,
} from "../bookable-items/bookingConfiguration";
import { mapBookingConfiguration } from "../bookable-items/bookingConfigurationSchema";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";

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
): Promise<void> {
  const params = bulkParameters(selectedRowIds);
  if (action === "archive") await deleteManyBookingConfigurations(params);
  else await updateManyBookingConfigurations({ enabled: action === "enable" }, params);
}

export function AllBookableItemsContent() {
  const { t } = useTranslation(["booking", "common"]);

  const { data: currentUser } = useCurrentUserQuery();
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const request = useMemo(
    () => ({
      depth: 1,
      projection: bookableItemsProjection,
    }),
    [],
  );
  const table = useApiV2TableList({
    resource: apiV2Resources["booking-configurations"],
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationsReadSchema,
    mapDocument: mapBookingConfiguration,
    request,
    query: { keepPreviousData: true },
  });
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
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration)),
    onSuccess: (_data, configuration) => onChanged(configuration.id),
  });
  const onRestore = useCallback(
    (configuration: BookingConfigurationRow) => restoreMutation.mutateAsync(configuration),
    [restoreMutation],
  );
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkUpdateOperation = useUpdateManyBookingConfigurations();
  const bulkDeleteOperation = useDeleteManyBookingConfigurations();
  const bulkMutation = useMutation({
    mutationFn: async ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) => {
      const params = bulkParameters(mutationRowIds);
      const result =
        action === "archive"
          ? await bulkDeleteOperation.mutateAsync({ params })
          : await bulkUpdateOperation.mutateAsync({ data: { enabled: action === "enable" }, params });
      return result.docs;
    },
    onSuccess: async (docs, variables) => {
      await invalidateBookableItemRecords(queryClient, variables.action, docs ?? []);
      setSelectedRowIds(new Set());
      setFailedBulkAction(null);
    },
    onError: (_error, variables) => setFailedBulkAction(variables.action),
  });
  const onSelectionChange = useCallback((value: ReadonlySet<string>) => {
    setSelectedRowIds(value);
    setFailedBulkAction(null);
  }, []);
  const onBulkAction = useCallback(
    async (action: BookableItemsBulkAction, actionRowIds: ReadonlySet<string>) => {
      setFailedBulkAction(null);
      await bulkMutation.mutateAsync({ action, selectedRowIds: Array.from(actionRowIds) });
    },
    [bulkMutation],
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
          <ArchiveBookableItemDialog configuration={row} close={close} onArchive={onArchive} onArchived={onChanged} />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={onPermanentDelete}
            onDeleted={onChanged}
          />
        ) : null,
    }),
    [directSysadmin, onArchive, onChanged, onPermanentDelete, onRestore, t],
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
          <Link to="/booking/bookable-items/add" className={cn(buttonVariants(), "rounded-sm")} data-slot="button">
            <PlusIcon aria-hidden="true" data-icon="inline-start" />
            {t("bookableItems.actions.add")}
          </Link>
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
