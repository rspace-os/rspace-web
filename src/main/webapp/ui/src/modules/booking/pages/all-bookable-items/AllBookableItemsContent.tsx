import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import {
  getSearchBookingCatalogueLocationsQueryKey,
  getSearchBookingCatalogueQueryKey,
} from "@/modules/common/api/v2/generated/booking-catalogue/booking-catalogue";
import {
  deleteBookingConfiguration,
  getCountBookingConfigurationsQueryKey,
  getGetBookingConfigurationAccessQueryKey,
  getGetBookingConfigurationQueryKey,
  getListBookingConfigurationAuditEventsQueryKey,
  getListBookingConfigurationsQueryKey,
  getSearchBookingConfigurationAccessGranteesQueryKey,
  invalidateListBookingConfigurations,
  listBookingConfigurations,
  updateBookingConfiguration as patchBookingConfiguration,
  useDeleteManyBookingConfigurations,
  useUpdateManyBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { getCountBookingsQueryKey, getListBookingsQueryKey } from "@/modules/common/api/v2/generated/bookings/bookings";
import { BookingConfigurationsReadSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import {
  TableList,
  type TableListFilterButtons,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import { type FilterExpression } from "@/modules/common/table-list/types";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "../bookable-items/ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "../bookable-items/BookableItemActionTriggers";
import { BookableItemsBulkActions } from "../bookable-items/BookableItemsBulkActions";
import {
  type BookableItemsBulkAction,
  lifecycleErrorKey,
  requiredVersion,
} from "../bookable-items/bookableItemLifecycleHelpers";
import { type BookingConfigurationRow, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { mapBookingConfiguration } from "../bookable-items/bookingConfigurationSchema";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";

export const bookableItemsProjection = {
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
    "ownerHealth",
    "state",
  ],
} as const;

export const maximumBookableItemsSelection = 1000;

export const maximumOwnerAttentionCandidates = 1000;

export const ownerAttentionParams = {
  limit: 100,
  totals: true,
  fields: { "booking-configurations": "id,ownerHealth" },
} as const;

export async function fetchOwnerAttentionPage(page: number, signal: AbortSignal) {
  const result = await listBookingConfigurations({ ...ownerAttentionParams, page }, { signal });
  if (result.totalDocs === undefined || result.totalIsLowerBound) throw new Error("Owner-health totals are missing");
  return { ...result, totalDocs: result.totalDocs, totalPages: Math.ceil(result.totalDocs / result.limit) };
}

export async function fetchOwnerAttentionIds(signal: AbortSignal): Promise<ReadonlySet<number>> {
  const first = await fetchOwnerAttentionPage(1, signal);
  if (first.totalDocs > maximumOwnerAttentionCandidates) {
    throw new Error(`Owner-health filtering supports at most ${maximumOwnerAttentionCandidates} bookable items`);
  }
  const documents = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    documents.push(...(await fetchOwnerAttentionPage(page, signal)).docs);
  }
  return new Set(documents.flatMap(({ id, ownerHealth }) => (ownerHealth?.hasEffectiveOwner === false ? [id] : [])));
}

export type BookableItemsBulkMutation = {
  action: BookableItemsBulkAction;
  selectedRowIds: readonly string[];
};

export function bulkParameters(selectedRowIds: readonly string[]) {
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

export async function archiveBookingConfiguration(id: number, version: number): Promise<void> {
  await deleteBookingConfiguration(id, { "If-Match": `"${version}"` });
}

export async function restoreBookingConfiguration(id: number, version: number): Promise<void> {
  await patchBookingConfiguration(id, { state: "ACTIVE" }, { "If-Match": `"${version}"` });
}

export async function permanentlyDeleteBookingConfiguration(id: number, version: number): Promise<void> {
  await deleteBookingConfiguration(id, { "If-Match": `"${version}"` }, { permanent: true });
}

export function AllBookableItemsContent() {
  const { t } = useTranslation(["booking", "common"]);

  const { data: currentUser } = useCurrentUserQuery();
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const [ownerAttentionOnly, setOwnerAttentionOnly] = useState(false);
  const ownerAttention = useQuery({
    queryKey: [...getListBookingConfigurationsQueryKey(ownerAttentionParams), "owner-attention"],
    queryFn: ({ signal }) => fetchOwnerAttentionIds(signal),
    enabled: ownerAttentionOnly,
    staleTime: 60_000,
  });
  const ownerAttentionFilter = useMemo<FilterExpression<BookingConfigurationRow> | undefined>(() => {
    if (!ownerAttentionOnly || !ownerAttention.data) return undefined;
    const ids = [...ownerAttention.data];
    return ids.length > 0
      ? { kind: "comparison", field: "id", operator: "in", value: ids }
      : { kind: "comparison", field: "id", operator: "equals", value: -1 };
  }, [ownerAttention.data, ownerAttentionOnly]);
  const request = useMemo(
    () => ({
      depth: 1,
      projection: bookableItemsProjection,
      ...(ownerAttentionFilter ? { baseFilter: ownerAttentionFilter } : {}),
    }),
    [ownerAttentionFilter],
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
  const onChanged = useCallback(() => void invalidateListBookingConfigurations(queryClient), [queryClient]);
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration)),
    onSuccess: onChanged,
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
  const ownerAttentionButtons: TableListFilterButtons = {
    legend: t("bookableItems.ownerHealth.filters"),
    buttons: [
      {
        id: "owner-attention",
        label: t("bookableItems.ownerHealth.filter"),
        pressed: ownerAttentionOnly,
        count: ownerAttention.data?.size,
        onClick: () => {
          setSelectedRowIds(new Set());
          table.setPage({ ...table.state.page, pageIndex: 0 });
          setOwnerAttentionOnly((current) => !current);
        },
      },
    ],
    onReset: () => {
      table.setPage({ ...table.state.page, pageIndex: 0 });
      setOwnerAttentionOnly(false);
    },
  };
  const displayedRows =
    ownerAttentionOnly && ownerAttention.data
      ? table.tableProps.rows.filter((row) => ownerAttention.data.has(row.id))
      : table.tableProps.rows;

  return (
    <main className="p-4 sm:p-8">
      {ownerAttentionOnly && ownerAttention.isPending && table.tableProps.status !== "error" ? (
        <p role="status" className="sr-only">
          {t("bookableItems.ownerHealth.loading")}
        </p>
      ) : null}
      {ownerAttentionOnly && ownerAttention.isError ? (
        <div role="alert" className="mb-3 flex items-center gap-3">
          <span>{t("bookableItems.ownerHealth.error")}</span>
          <Button type="button" variant="outline" onClick={() => void ownerAttention.refetch()}>
            {t("common:actions.retry")}
          </Button>
        </div>
      ) : null}
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
        status={
          table.tableProps.status === "error" || (ownerAttentionOnly && ownerAttention.isError)
            ? "error"
            : ownerAttentionOnly && ownerAttention.isPending
              ? "loading"
              : table.tableProps.status
        }
        rows={ownerAttentionOnly && (ownerAttention.isPending || ownerAttention.isError) ? [] : displayedRows}
        rowActions={rowActions}
        filterButtons={ownerAttentionButtons}
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

export async function invalidateBookableItemRecords(
  queryClient: QueryClient,
  action: BookableItemsBulkAction,
  docs: readonly { id?: number }[],
) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: getListBookingConfigurationsQueryKey() }, { cancelRefetch: false }),
    queryClient.invalidateQueries({ queryKey: getSearchBookingCatalogueLocationsQueryKey() }, { cancelRefetch: false }),
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
          ],
    ),
    ...(action === "archive"
      ? [
          queryClient.invalidateQueries({ queryKey: getListBookingsQueryKey() }),
          queryClient.invalidateQueries({ queryKey: getCountBookingsQueryKey() }),
        ]
      : []),
  ]);
}
