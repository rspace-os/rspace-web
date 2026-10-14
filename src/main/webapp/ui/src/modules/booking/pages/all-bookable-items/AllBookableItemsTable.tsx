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
import type { FilterState } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { TooltipIconLink } from "@/modules/common/ui/tooltip-icon-link";
import { cn } from "@/modules/common/utils/cn";
import { type AllBookableItem, AvailabilityCandidateLimitError, withAvailability } from "./availabilityQuickFilters";
import type { useAllBookableItemsData } from "./useAllBookableItemsData";

type AllBookableItemsTableProps = Omit<
  ReturnType<typeof useAllBookableItemsData>,
  | "config"
  | "invalidFilter"
  | "catalogue"
  | "runtimeFieldState"
  | "selectedNotificationIds"
  | "notificationSubscriptionMutation"
> & {
  types?: readonly string[];
  mine: boolean;
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
  runtimeFilterBlocked,
  quickMode,
  quickFilterLabels,
  bounds,
  quickIndex,
  quickFilterPending,
  quickFilterError,
  rows,
  hasNoBookableItems,
  setSelectedNotificationIds,
  notificationFeedback,
  useQuickAvailability,
  availability,
  types,
  mine,
  tableProps,
  setDate,
  resetView,
  setFilters,
}: AllBookableItemsTableProps) {
  const { t } = useTranslation("booking");
  const navigate = useNavigate({ from: "/booking/all-items/" });
  const rowActions = useMemo<TableListRowActions<AllBookableItem>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 176,
      minWidth: 120,
      renderCell: ({ row }) => {
        if (!row.target) return null;
        const rowDate = quickMode ? (quickIndex.data?.get(row.target.globalId)?.date ?? selectedDate) : selectedDate;
        const detailsLabel = t("allBookableItems.actions.viewDetails");
        const bookLabel = t("allBookableItems.actions.book");
        return (
          <div className="flex gap-1">
            <TooltipIconLink label={detailsLabel} icon={<EyeIcon aria-hidden="true" />}>
              <Link
                aria-label={detailsLabel}
                className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                data-slot="button"
                to="/booking/bookable-items/$globalId/{-$tab}"
                params={{ globalId: row.target.globalId, tab: undefined }}
              />
            </TooltipIconLink>
            {row.capabilities?.canCreateBooking ? (
              <TooltipIconLink label={bookLabel} icon={<CalendarPlusIcon aria-hidden="true" />}>
                <Link
                  aria-label={bookLabel}
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/calendar/bookings/add"
                  search={{ date: rowDate, target: row.target.globalId }}
                />
              </TooltipIconLink>
            ) : null}
            {row.capabilities?.canEditConfiguration ? (
              <TooltipIconLink
                label={t("allBookableItems.actions.settings")}
                icon={<SettingsIcon aria-hidden="true" />}
              >
                <Link
                  aria-label={t("allBookableItems.actions.settings")}
                  className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                  data-slot="button"
                  to="/booking/bookable-items/$globalId/{-$tab}"
                  params={{ globalId: row.target.globalId, tab: "details" }}
                  search={{ edit: true }}
                />
              </TooltipIconLink>
            ) : null}
          </div>
        );
      },
      renderInteraction: () => null,
    }),
    [quickIndex.data, quickMode, selectedDate, t],
  );

  const availabilityFilters: TableListFilterButtons = {
    legend: t("allBookableItems.quickFilters.legend"),
    controlsOnSeparateRow: true,
    controls: (
      <BookingDateControls
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
          setSelectedNotificationIds(new Set());
          void navigate({
            search: (current) => ({ ...current, mine: mine ? undefined : true, page: undefined }),
            replace: true,
          });
        },
      },
      ...(["available-now", "free-later-today"] as const).map((mode) => {
        // The two categories are mutually exclusive, so each chip explains which items it counts.
        const description =
          mode === "available-now"
            ? t("allBookableItems.quickFilters.availableNowDescription")
            : t("allBookableItems.quickFilters.freeLaterTodayDescription");
        return {
          id: mode,
          label: (
            <>
              {quickFilterLabels[mode]}
              <span
                aria-hidden="true"
                className="ml-0.5 min-w-5 rounded-sm bg-foreground px-1 text-[10px] text-background"
              >
                {quickIndex.isError ? (
                  "—"
                ) : quickIndex.data ? (
                  [...quickIndex.data.values()].filter(({ category }) => category === mode).length
                ) : (
                  <Skeleton className="h-3 w-3" />
                )}
              </span>
            </>
          ),
          description,
          icon: mode === "available-now" ? <Clock3Icon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />,
          pressed: quickMode === mode,
          disabled: quickIndex.error instanceof AvailabilityCandidateLimitError && quickMode !== mode,
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
      {quickIndex.isError ? (
        <div role="alert" className="flex items-center gap-3">
          <span>
            {t(
              quickIndex.error instanceof AvailabilityCandidateLimitError
                ? "allBookableItems.quickFilters.limit"
                : "allBookableItems.quickFilters.error",
            )}
          </span>
          {quickIndex.error instanceof AvailabilityCandidateLimitError ? null : (
            <Button type="button" variant="outline" onClick={() => void quickIndex.refetch()}>
              {t("allBookableItems.quickFilters.retry")}
            </Button>
          )}
        </div>
      ) : null}
      {notificationFeedback ? (
        <p role="status" className="text-sm text-primary">
          {t(
            notificationFeedback.enabled
              ? "notificationSubscriptions.bulk.subscribedCount"
              : "notificationSubscriptions.bulk.unsubscribedCount",
            { count: notificationFeedback.count },
          )}
        </p>
      ) : null}
      <TableList
        {...tableProps}
        headingClassName="text-2xl font-semibold"
        onReset={resetView}
        rows={runtimeFilterBlocked || quickFilterPending || quickFilterError ? [] : rows}
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
                ...(target.value.parentContainerName != null && target.value.parentContainerGlobalId != null
                  ? {
                      location: {
                        name: target.value.parentContainerName,
                        globalId: target.value.parentContainerGlobalId,
                      },
                    }
                  : {}),
              };
              const quickEntry = quickIndex.data?.get(target.globalId);
              if (quickMode || (useQuickAvailability && quickEntry)) {
                if (!quickEntry) return t("calendar.availabilityUnavailable");
                return (
                  <AvailabilityBar
                    intervals={quickEntry.intervals}
                    periodStart={new Date(quickEntry.bounds.start)}
                    periodEnd={new Date(quickEntry.bounds.end)}
                    now={quickIndex.now}
                    showBookingContextDetails={false}
                    showCurrentAvailability
                    showPeriodLabels
                    timeZone={preferences.timeZone}
                    instrumentTimeZone={row.timezone}
                    item={item}
                  />
                );
              }
              if ((useQuickAvailability && quickIndex.isPending) || availability.isPending)
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
                  periodStart={new Date(bounds.start)}
                  periodEnd={new Date(bounds.end)}
                  now={selectedDate === userToday ? quickIndex.now : undefined}
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
              {t("bookableItems.primer.description")}{" "}
              <Link to="/booking/bookable-items/add">{t("bookableItems.addTitle")}</Link>
            </>
          ) : undefined
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
