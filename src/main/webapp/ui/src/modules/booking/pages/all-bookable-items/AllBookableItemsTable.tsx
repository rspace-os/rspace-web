import { Link, useNavigate } from "@tanstack/react-router";
import {
  CalendarClockIcon,
  CalendarPlusIcon,
  Clock3Icon,
  EyeIcon,
  PackageCheckIcon,
  PlusIcon,
  SettingsIcon,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { addCalendarDays } from "@/modules/booking/domain/bookingTime";
import {
  TableList,
  type TableListFilterButtons,
  type TableListProps,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/types";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { TooltipIconLink } from "@/modules/common/ui/tooltip-icon-link";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "../bookable-items/ArchiveBookableItemDialog";
import { BookingConfigurationActionsMenu } from "../bookable-items/BookingConfigurationActionsMenu";
import { bookableItemLifecycleNotice } from "../bookable-items/bookableItemFeedback";
import {
  archiveBookingConfiguration,
  permanentlyDeleteBookingConfiguration,
} from "../bookable-items/bookingConfiguration";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";
import { type AllBookableItem, withAvailability } from "./availabilityQuickFilters";
import type { useAllBookableItemsData } from "./useAllBookableItemsData";

type AllBookableItemsTableProps = Omit<
  ReturnType<typeof useAllBookableItemsData>,
  | "config"
  | "invalidFilter"
  | "catalogue"
  | "runtimeFieldState"
  | "bulkMutation"
  | "failedBulkAction"
  | "notificationSubscriptionMutation"
  | "rows"
  | "selectedRowIds"
  | "selectionPending"
  | "setFailedBulkAction"
> & {
  types?: readonly string[];
  mine: boolean;
  directSysadmin: boolean;
  tableProps: TableListProps<AllBookableItem>;
  setDate: (nextDate: string) => void;
  resetView: () => void;
  setFilters: (next: FilterState<AllBookableItem>) => void;
};

export function AllBookableItemsTable({
  preferences,
  userToday,
  selectedDate,
  filters,
  quickMode,
  hasNoBookableItems,
  setSelectedRowIds,
  notificationFeedback,
  availability,
  types,
  mine,
  directSysadmin,
  tableProps,
  setDate,
  resetView,
  setFilters,
  canAdd,
  counts,
  minute,
  onLifecycleChanged,
  rowsBounds,
  rowsDate,
  token,
  quickFilterText,
  alertsRef,
  attachAlerts,
}: AllBookableItemsTableProps) {
  const { t } = useTranslation("booking");
  const navigate = useNavigate({ from: "/booking/all-items/" });
  const rowActions = useMemo<TableListRowActions<AllBookableItem>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 224,
      minWidth: 176,
      renderCell: ({ row, activate }) => {
        if (!row.target) return null;
        const detailsLabel = t("allBookableItems.actions.viewDetails");
        const bookLabel = t("allBookableItems.actions.book");
        return (
          <div className="flex items-center gap-1">
            <TooltipIconLink label={detailsLabel} icon={<EyeIcon aria-hidden="true" />}>
              <Link
                className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                data-slot="button"
                to="/booking/bookable-items/$globalId/{-$tab}"
                params={{ globalId: row.target.globalId, tab: undefined }}
              />
            </TooltipIconLink>
            {row.capabilities?.canCreateBooking ? (
              <TooltipIconLink label={bookLabel} icon={<CalendarPlusIcon aria-hidden="true" />}>
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/calendar/bookings/add"
                  search={{ date: rowsDate, target: row.target.globalId }}
                />
              </TooltipIconLink>
            ) : null}
            {row.capabilities?.canEditConfiguration ? (
              <TooltipIconLink
                label={t("allBookableItems.actions.settings")}
                icon={<SettingsIcon aria-hidden="true" />}
              >
                <Link
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/bookable-items/$globalId/{-$tab}"
                  params={{ globalId: row.target.globalId, tab: "details" }}
                  search={{ edit: true }}
                />
              </TooltipIconLink>
            ) : null}
            {/* The Administration lifecycle actions; listed items are always active, so never Restore. */}
            <BookingConfigurationActionsMenu
              configuration={row}
              itemName={row.target.value.name}
              directSysadmin={directSysadmin}
              // The neighbouring links render borderless at 40 px.
              triggerClassName="min-h-10 min-w-10 border-transparent"
              onAction={activate}
            />
          </div>
        );
      },
      renderInteraction: ({ actionId, row, close }) =>
        actionId === "archive" ? (
          <ArchiveBookableItemDialog
            configuration={row}
            close={close}
            onArchive={(id, version) => archiveBookingConfiguration(id, version, token)}
            onArchived={async (id) => {
              alertsRef.current?.push(bookableItemLifecycleNotice("archived", row, t));
              await onLifecycleChanged([id]);
            }}
          />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={(id, version) => permanentlyDeleteBookingConfiguration(id, version, token)}
            onDeleted={async () => {
              alertsRef.current?.push(bookableItemLifecycleNotice("deleted", row, t));
              await onLifecycleChanged([row.id], true);
            }}
          />
        ) : null,
    }),
    [directSysadmin, onLifecycleChanged, rowsDate, t, token],
  );

  const availabilityFilters: TableListFilterButtons = {
    legend: t("allBookableItems.quickFilters.legend"),
    align: "end",
    controlsOnSeparateRow: true,
    controls: (
      <BookingDateControls
        className="mr-auto"
        date={selectedDate}
        today={userToday}
        timeZone={preferences.timeZone}
        controlsLabel={t("allBookableItems.dateControls")}
        navigationLabel={t("allBookableItems.dateNavigation")}
        previousLabel={t("allBookableItems.actions.previousDay")}
        todayLabel={t("allBookableItems.actions.today")}
        nextLabel={t("allBookableItems.actions.nextDay")}
        jumpToDateLabel={t("allBookableItems.jumpToDate")}
        onPrevious={() => setDate(addCalendarDays(selectedDate, -1))}
        onNext={() => setDate(addCalendarDays(selectedDate, 1))}
        onDateChange={setDate}
      />
    ),
    hasChanges: selectedDate !== userToday || Boolean(types?.length) || mine,
    buttons: [
      {
        id: "mine",
        label: t("allBookableItems.quickFilters.myItems"),
        icon: <PackageCheckIcon aria-hidden="true" />,
        pressed: mine,
        onClick: () => {
          setSelectedRowIds(new Set());
          void navigate({
            search: (current) => ({ ...current, mine: mine ? undefined : true, page: undefined }),
            replace: true,
          });
        },
      },
      ...(["available-now", "free-later-today"] as const).map((mode) => {
        // The two categories are mutually exclusive, so each chip explains which items it counts.
        const text = quickFilterText[mode];
        return {
          id: mode,
          label: (
            <>
              {text.label}
              <span
                aria-hidden="true"
                className="ml-0.5 min-w-5 rounded-sm bg-foreground px-1 text-[10px] text-background"
              >
                {counts.isError ? (
                  "—"
                ) : counts.data ? (
                  mode === "available-now" ? (
                    counts.data.availableNow
                  ) : (
                    counts.data.freeLaterToday
                  )
                ) : (
                  <Skeleton className="h-3 w-3" />
                )}
              </span>
            </>
          ),
          description: text.description,
          icon: mode === "available-now" ? <Clock3Icon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />,
          pressed: quickMode === mode,
          onClick: () =>
            setFilters({
              ...filters,
              expression: withAvailability(filters.expression, quickMode === mode ? undefined : mode),
            }),
        };
      }),
    ],
    onReset: resetView,
  };

  return (
    <main className="space-y-5 p-4 sm:p-8">
      {tableProps.status === "loading" ? (
        <p role="status" className="sr-only">
          {t("allBookableItems.quickFilters.loading")}
        </p>
      ) : null}
      {counts.isError ? (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("allBookableItems.quickFilters.error")}</span>
          <Button type="button" variant="outline" onClick={() => void counts.refetch()}>
            {t("allBookableItems.quickFilters.retry")}
          </Button>
        </div>
      ) : null}
      <p role="status" className="text-sm text-primary">
        {notificationFeedback
          ? notificationFeedback.enabled
            ? t("notificationSubscriptions.bulk.subscribedCount", { count: notificationFeedback.count })
            : t("notificationSubscriptions.bulk.unsubscribedCount", { count: notificationFeedback.count })
          : null}
      </p>
      <TableList
        {...tableProps}
        alertsRef={attachAlerts}
        headingClassName="text-2xl font-semibold"
        onReset={resetView}
        filterButtons={availabilityFilters}
        presentations={{ table: "wide", cards: "narrow" }}
        uiColumns={[
          {
            id: "availability",
            label: t("calendar.availability"),
            minWidth: 320,
            width: 520,
            card: { fullWidth: true },
            renderCell: (row) => {
              const target = row.target;
              if (!target || !row.timezone) return t("calendar.availabilityUnavailable");
              const item = {
                name: target.value.name,
                globalId: target.globalId,
                ...(target.location ? { location: target.location } : {}),
              };
              if (availability.isPending)
                return (
                  <div aria-busy="true">
                    <span role="status" className="sr-only">
                      {t("calendar.availabilityLoading")}
                    </span>
                    <Skeleton aria-hidden="true" className="h-16 w-full" />
                  </div>
                );
              if (availability.isError || !availability.data) {
                return <span role="status">{t("calendar.availabilityUnavailable")}</span>;
              }
              return (
                <AvailabilityBar
                  intervals={availability.data.get(target.globalId) ?? []}
                  periodStart={new Date(rowsBounds.start)}
                  periodEnd={new Date(rowsBounds.end)}
                  now={rowsDate === userToday ? new Date(minute) : undefined}
                  showBookingContextDetails={false}
                  showCurrentAvailability
                  showPeriodLabels
                  timeZone={preferences.timeZone}
                  instrumentTimeZone={row.timezone}
                  item={item}
                />
              );
            },
          },
        ]}
        rowActions={rowActions}
        emptyDescription={
          hasNoBookableItems ? (
            <>
              {t("bookableItems.primer.description")}
              {canAdd && (
                <>
                  {" "}
                  <Link to="/booking/bookable-items/add">{t("bookableItems.addTitle")}</Link>
                </>
              )}
            </>
          ) : undefined
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
