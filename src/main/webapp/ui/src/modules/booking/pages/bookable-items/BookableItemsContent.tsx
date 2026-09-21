import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
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
    "state",
  ],
} as const;

const maximumBookableItemsSelection = 1000;
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
  const request = useMemo(
    () => ({
      token,
      authScope: currentUser.id,
      depth: 1,
      projection: bookableItemsProjection,
    }),
    [currentUser.id, token],
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

  return (
    <main className="p-4 sm:p-8">
      {restoreMutation.isError ? (
        <p role="alert" className="mb-3 text-sm text-destructive">
          {t(lifecycleErrorKey(restoreMutation.error, "bookableItems.lifecycleErrors.restore"))}
        </p>
      ) : null}
      <TableList
        {...table.tableProps}
        headingClassName="text-2xl font-semibold"
        rowActions={rowActions}
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
