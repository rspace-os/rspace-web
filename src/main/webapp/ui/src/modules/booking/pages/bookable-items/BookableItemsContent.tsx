import { useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon } from "lucide-react";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { bookingCatalogueKeys, bookingConfigurationKeys, bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2Mutation } from "@/modules/common/queries/apiV2Mutation";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { cn } from "@/modules/common/utils/cn";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { ArchiveBookableItemDialog } from "./ArchiveBookableItemDialog";
import { BookableItemActionTriggers } from "./BookableItemActionTriggers";
import { BookableItemsBulkActions } from "./BookableItemsBulkActions";
import { calendarSubscriptionQueryKey } from "./bookableItemCalendarSubscription";
import { bookableItemBulkNotice, bookableItemLifecycleNotice, useBookableItemNoticeIds } from "./bookableItemFeedback";
import { type BookableItemsBulkAction, lifecycleErrorKey, requiredVersion } from "./bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "./bookableItemsAdministrationAccess";
import {
  archiveBookingConfiguration,
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
  invalidateBookingCatalogueQueries,
  permanentlyDeleteBookingConfiguration,
  restoreBookingConfiguration,
} from "./bookingConfiguration";
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

export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
  token: string,
): Promise<readonly { id: number }[] | null> {
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
  const response = await fetchWithUiToken(`/api/v2/booking-configurations?${search}`, {
    method: isDelete ? "DELETE" : "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isDelete ? {} : { "Content-Type": "application/json" }),
      "X-Requested-With": "XMLHttpRequest",
    },
    ...(isDelete ? {} : { body: JSON.stringify({ enabled: action === "enable" }) }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  // The server has committed a successful response even if its receipt cannot be read.
  try {
    return parseOrThrow(
      v.object({ docs: v.array(v.object({ id: v.pipe(v.number(), v.integer(), v.minValue(1)) })) }),
      await response.json(),
    ).docs;
  } catch {
    return null;
  }
}

export function BookableItemsContent() {
  const { t } = useTranslation(["booking", "common"]);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
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
  const itemNoticeIds = useBookableItemNoticeIds(table.tableProps.rows);
  const onArchive = useCallback(
    (id: number, version: number) => archiveBookingConfiguration(id, version, token),
    [token],
  );
  const onPermanentDelete = useCallback(
    (id: number, version: number) => permanentlyDeleteBookingConfiguration(id, version, token),
    [token],
  );
  // Add stays hidden until the caller has an instrument to set up, so a direct URL offers no dead end.
  const canAdd = (useEligibleBookingTargets().data?.length ?? 0) > 0;
  const restoreMutation = useApiV2Mutation({
    meta: { invalidates: [bookingConfigurationKeys.all, bookingKeys.all, bookingCatalogueKeys.all] },
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration), token),
    onSuccess: async (_data, configuration) => {
      alertsRef.current?.push(bookableItemLifecycleNotice("restored", configuration, t));
      await queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) });
    },
  });
  const restore = restoreMutation.mutateAsync;
  const onRestore = useCallback((configuration: BookingConfigurationRow) => restore(configuration), [restore]);
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkMutation = useApiV2Mutation({
    meta: { invalidates: [bookingConfigurationKeys.all] },
    mutationFn: ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) =>
      mutateBookableItems(action, mutationRowIds, token),
    onSuccess: async (docs, variables) => {
      if (variables.action !== "enable") {
        for (const id of docs?.map((doc) => doc.id) ?? variables.selectedRowIds.map(Number)) {
          alertsRef.current?.dismiss(itemNoticeIds.get(id) ?? `bookable-item-${id}`);
        }
      }
      alertsRef.current?.push(bookableItemBulkNotice(variables.action, docs?.length ?? null, t));
      setSelectedRowIds(new Set());
      setFailedBulkAction(null);
      await Promise.all([
        variables.action === "archive"
          ? queryClient.invalidateQueries({ queryKey: bookingCatalogueKeys.all })
          : invalidateBookingCatalogueQueries(queryClient),
        ...(variables.action === "archive"
          ? [
              queryClient.invalidateQueries({ queryKey: bookingKeys.all }),
              ...variables.selectedRowIds.map((id) =>
                queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(Number(id)) }),
              ),
            ]
          : []),
      ]);
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
