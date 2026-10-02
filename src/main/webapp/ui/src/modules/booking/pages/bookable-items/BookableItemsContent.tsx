import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import {
  TableList,
  type TableListFilterButtons,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "./ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "./BookableItemActionTriggers";
import { BookableItemsBulkActions } from "./BookableItemsBulkActions";
import { calendarSubscriptionQueryKey } from "./bookableItemCalendarSubscription";
import { type BookableItemsBulkAction, lifecycleErrorKey, requiredVersion } from "./bookableItemLifecycleHelpers";
import {
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "./bookingConfiguration";
import { PermanentDeleteBookableItemDialog } from "./PermanentDeleteBookableItemDialog";

export type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";

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
    "ownerHealth",
    "state",
  ],
} as const;

const maximumBookableItemsSelection = 1000;
const maximumOwnerAttentionCandidates = 1000;

const OwnerAttentionPageSchema = v.object({
  docs: v.array(
    v.object({
      id: v.number(),
      ownerHealth: v.optional(v.object({ hasEffectiveOwner: v.boolean() })),
    }),
  ),
  totalDocs: v.number(),
  totalPages: v.number(),
});

async function fetchOwnerAttentionPage(page: number, token: string, signal: AbortSignal) {
  const search = new URLSearchParams({
    page: String(page),
    limit: "100",
    "fields[booking-configurations]": "id,ownerHealth",
  });
  const response = await fetch(`/api/v2/booking-configurations?${search}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Owner-health request failed with status ${response.status}`);
  return v.parse(OwnerAttentionPageSchema, await response.json());
}

async function fetchOwnerAttentionIds(token: string, signal: AbortSignal): Promise<ReadonlySet<number>> {
  const first = await fetchOwnerAttentionPage(1, token, signal);
  if (first.totalDocs > maximumOwnerAttentionCandidates) {
    throw new Error(`Owner-health filtering supports at most ${maximumOwnerAttentionCandidates} bookable items`);
  }
  const documents = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    documents.push(...(await fetchOwnerAttentionPage(page, token, signal)).docs);
  }
  return new Set(documents.flatMap(({ id, ownerHealth }) => (ownerHealth?.hasEffectiveOwner === false ? [id] : [])));
}

type BookableItemsBulkMutation = {
  action: BookableItemsBulkAction;
  selectedRowIds: readonly string[];
};

export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
  token: string,
): Promise<void> {
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
  const search = new URLSearchParams({ where });
  const isDelete = action === "archive";
  const response = await fetch(`/api/v2/booking-configurations?${search}`, {
    method: isDelete ? "DELETE" : "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isDelete ? {} : { "Content-Type": "application/json" }),
      "X-Requested-With": "XMLHttpRequest",
    },
    ...(isDelete ? {} : { body: JSON.stringify({ enabled: action === "enable" }) }),
  });
  if (!response.ok) throw new Error(`Bulk booking ${action} failed with status ${response.status}`);
}

async function archiveBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

async function restoreBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify({ state: "ACTIVE" }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

async function permanentlyDeleteBookingConfiguration(id: number, version: number, token: string): Promise<void> {
  const response = await fetch(`/api/v2/booking-configurations/${id}?permanent=true`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      "If-Match": `"${version}"`,
      "X-Requested-With": "XMLHttpRequest",
    },
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

export function BookableItemsContent() {
  const { t } = useTranslation(["booking", "common"]);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const [ownerAttentionOnly, setOwnerAttentionOnly] = useState(false);
  const ownerAttention = useQuery({
    queryKey: ["api-v2", "booking-configurations", "owner-attention"],
    queryFn: ({ signal }) => fetchOwnerAttentionIds(token, signal),
    enabled: ownerAttentionOnly && token.length > 0,
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
      token,
      depth: 1,
      projection: bookableItemsProjection,
      ...(ownerAttentionFilter ? { baseFilter: ownerAttentionFilter } : {}),
    }),
    [ownerAttentionFilter, token],
  );
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: bookingConfigurationConfig,
    documentSchema: BookingConfigurationSchema,
    request,
    query: { keepPreviousData: true },
  });
  const onArchive = useCallback(
    (id: number, version: number) => archiveBookingConfiguration(id, version, token),
    [token],
  );
  const onPermanentDelete = useCallback(
    (id: number, version: number) => permanentlyDeleteBookingConfiguration(id, version, token),
    [token],
  );
  const onChanged = useCallback(
    async (configurationId?: number) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        ...(configurationId === undefined
          ? []
          : [queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configurationId) })]),
      ]);
    },
    [queryClient],
  );
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration), token),
    onSuccess: (_data, configuration) => onChanged(configuration.id),
  });
  const onRestore = useCallback(
    (configuration: BookingConfigurationRow) => restoreMutation.mutateAsync(configuration),
    [restoreMutation],
  );
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkMutation = useMutation({
    mutationFn: ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) =>
      mutateBookableItems(action, mutationRowIds, token),
    onSuccess: async (_data, variables) => {
      setSelectedRowIds(new Set());
      setFailedBulkAction(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        ...(variables.action === "archive"
          ? [
              queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
              ...variables.selectedRowIds.map((id) =>
                queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(Number(id)) }),
              ),
            ]
          : []),
      ]);
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
          setOwnerAttentionOnly((current) => !current);
        },
      },
    ],
    onReset: () => setOwnerAttentionOnly(false),
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
          {t(lifecycleErrorKey(restoreMutation.error, "bookableItems.lifecycleErrors.restore"))}
        </p>
      ) : null}
      <TableList
        {...table.tableProps}
        headingClassName="text-2xl font-semibold"
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
        selection={{
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
        }}
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
