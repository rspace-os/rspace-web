import type { QueryClient } from "@tanstack/react-query";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  fetchBookingNotificationSubscription,
  replaceBookingNotificationSubscription,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { useBookingNoticeHost } from "@/modules/booking/feedback/BookingNotices";
import {
  invalidateCountBookingConfigurations,
  invalidateGetBookingCalendarSubscription,
  invalidateGetBookingConfiguration,
  invalidateGetBookingConfigurationAccess,
  invalidateGetMyBookingNotificationSubscription,
  invalidateListBookingConfigurationAuditEvents,
  invalidateListBookingConfigurations,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { invalidateListBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import type { TableListAlertsApi } from "@/modules/common/table-list/components/TableListAlerts";
import { rsqlSelectors } from "@/modules/common/table-list/rsql";
import { Button } from "@/modules/common/ui/button";
import { BookableItemsBulkActions } from "../bookable-items/BookableItemsBulkActions";
import {
  bookableItemBulkNotice,
  bookableItemLifecycleNotice,
  useBookableItemNoticeIds,
} from "../bookable-items/bookableItemFeedback";
import {
  type BookableItemsBulkAction,
  lifecycleErrorKey,
  requiredVersion,
} from "../bookable-items/bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "../bookable-items/bookableItemsAdministrationAccess";
import {
  type BookingConfigurationRow,
  invalidateBookingCatalogueQueries,
  restoreBookingConfiguration,
} from "../bookable-items/bookingConfiguration";
import { invalidateBookableItemRecords, mutateBookableItems } from "../bookable-items/mutateBookableItems";
import { DroppedFilterNotice } from "../DroppedFilterNotice";
import { AllBookableItemsTable } from "./AllBookableItemsTable";
import { useAllBookableItemsData } from "./useAllBookableItemsData";

type BookableItemsBulkMutation = { action: BookableItemsBulkAction; selectedRowIds: readonly string[] };
export function AllBookableItemsContent() {
  const { data: currentUser } = useCurrentUserQuery();
  return <AllBookableItemsContentForUser key={currentUser.id} />;
}

function AllBookableItemsContentForUser() {
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
  const table = useAllBookableItemsData({ subjectId: currentUser.id });
  const [droppedStatusFields, setDroppedStatusFields] = useState<readonly string[]>([]);
  const restoredIssue = table.tableProps.restoredViewIssue;
  const recoveredStatusView = useRef<string | null>(null);
  useEffect(() => {
    if (restoredIssue?.kind !== "invalid") {
      recoveredStatusView.current = null;
      return;
    }
    if (table.state.filters.expression !== null || recoveredStatusView.current === restoredIssue.encoded) return;
    const statusFields = rsqlSelectors(restoredIssue.encoded).filter(
      (field) => field === "state" || field === "enabled",
    );
    if (statusFields.length === 0) return;
    recoveredStatusView.current = restoredIssue.encoded;
    setDroppedStatusFields(statusFields);
    restoredIssue.remove();
  }, [restoredIssue, table.state.filters.expression]);
  const notificationEligible = useRef(new Set<string>());
  for (const row of table.tableProps.rows) {
    if (row.capabilities?.canManageNotificationSubscription) notificationEligible.current.add(String(row.id));
    else notificationEligible.current.delete(String(row.id));
  }
  const notificationIds = [...selectedRowIds].filter((id) => notificationEligible.current.has(id));
  const notificationMutation = useMutation({
    mutationFn: async (enabled: boolean) => {
      for (const id of notificationIds) {
        const subscription = await fetchBookingNotificationSubscription(Number(id));
        const etag = subscription.headers.get("ETag");
        if (!etag) throw new Error("Notification subscription validator is unavailable");
        await replaceBookingNotificationSubscription(Number(id), enabled, etag);
        await invalidateGetMyBookingNotificationSubscription(queryClient, Number(id));
      }
    },
    onSuccess: () => setSelectedRowIds(new Set()),
  });
  const itemNoticeIds = useBookableItemNoticeIds(table.tableProps.rows);
  const eligibleTargets = useEligibleBookingTargets().data;
  const canAdd = eligibleTargets === undefined || eligibleTargets.length > 0;
  const restoreMutation = useMutation({
    mutationFn: (configuration: BookingConfigurationRow) =>
      restoreBookingConfiguration(configuration.id, requiredVersion(configuration)),
    onSuccess: async (_data, configuration) => {
      await invalidateChangedConfigurations(queryClient, [configuration.id]);
      await invalidateListBookings(queryClient);
      await invalidateBookingCatalogueQueries(queryClient);
      alertsRef.current?.push(bookableItemLifecycleNotice("restored", configuration, t));
      await invalidateGetBookingCalendarSubscription(queryClient, configuration.id);
    },
  });
  const restore = restoreMutation.mutateAsync;
  const onRestore = useCallback((configuration: BookingConfigurationRow) => restore(configuration), [restore]);
  const directSysadmin = currentUser.hasSysAdminRole && !currentUser.session.operatedAs;
  const bulkMutation = useMutation({
    mutationFn: ({ action, selectedRowIds: mutationRowIds }: BookableItemsBulkMutation) =>
      mutateBookableItems(action, mutationRowIds),
    onSuccess: async (docs, variables) => {
      await invalidateBookableItemRecords(queryClient, variables.action, docs ?? []);
      if (variables.action !== "enable") {
        for (const id of docs?.map((doc) => doc.id) ?? variables.selectedRowIds.map(Number)) {
          alertsRef.current?.dismiss(itemNoticeIds.get(id) ?? `bookable-item-${id}`);
        }
      }
      alertsRef.current?.push(bookableItemBulkNotice(variables.action, docs?.length ?? null, t));
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

  return (
    <div>
      <DroppedFilterNotice fields={[...droppedStatusFields, ...table.droppedFilterFields]} />
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
      <AllBookableItemsTable
        filters={table.state.filters}
        hasNoBookableItems={
          table.tableProps.rows.length === 0 && !table.state.filters.search && !table.state.filters.expression
        }
        clearSelection={() => setSelectedRowIds(new Set())}
        directSysadmin={directSysadmin}
        resetView={() => {
          setSelectedRowIds(new Set());
          table.setFilters({ search: "", expression: null });
          table.setSorting(table.tableProps.config.defaultSort ?? []);
          table.setVisibleFields(table.tableProps.config.defaultColumns);
        }}
        setFilters={(filters) => {
          setSelectedRowIds(new Set());
          table.setFilters(filters);
        }}
        canAdd={canAdd}
        onLifecycleChanged={async (ids) => {
          await invalidateBookableItemRecords(
            queryClient,
            "archive",
            ids.map((id) => ({ id })),
            { cancelRefetch: false },
          );
          setSelectedRowIds(new Set());
        }}
        onRestore={onRestore}
        alertsRef={alertsRef}
        attachAlerts={attachAlerts}
        tableProps={{
          ...table.tableProps,
          selection: {
            value: selectedRowIds,
            onChange: onSelectionChange,
            disabled: bulkMutation.isPending || notificationMutation.isPending,
            maximumCount: 100,
            getRowLabel: (row) => row.target?.value.name ?? t("common:values.unknownItem"),
            renderActions: (selection) => (
              <>
                {notificationIds.length > 0 ? (
                  <>
                    <Button
                      disabled={bulkMutation.isPending || notificationMutation.isPending}
                      onClick={() => notificationMutation.mutate(true)}
                    >
                      {t("allBookableItems.notifications.subscribe", {})}
                    </Button>
                    <Button
                      disabled={bulkMutation.isPending || notificationMutation.isPending}
                      onClick={() => notificationMutation.mutate(false)}
                    >
                      {t("allBookableItems.notifications.unsubscribe", {
                        defaultValue: "Unsubscribe from notifications",
                      })}
                    </Button>
                  </>
                ) : null}
                {notificationMutation.isError ? (
                  <p role="alert">{t("notificationSubscriptions.item.saveError")}</p>
                ) : null}
                {directSysadmin ? (
                  <BookableItemsBulkActions
                    selection={selection}
                    disabled={bulkMutation.isPending || notificationMutation.isPending}
                    activeAction={bulkMutation.isPending ? (bulkMutation.variables?.action ?? null) : null}
                    failedAction={failedBulkAction}
                    onAction={onBulkAction}
                  />
                ) : null}
              </>
            ),
          },
        }}
      />
    </div>
  );
}

async function invalidateChangedConfigurations(queryClient: QueryClient, ids: readonly number[]) {
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
