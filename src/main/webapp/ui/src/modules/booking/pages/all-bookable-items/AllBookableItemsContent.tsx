import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import {
  CalendarClockIcon,
  CalendarPlusIcon,
  Clock3Icon,
  EyeIcon,
  PackageCheckIcon,
  PlusIcon,
  SettingsIcon,
} from "lucide-react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookingNotificationSubscriptionsQueryKey,
  updateBookingNotificationSubscriptions,
} from "@/modules/booking/domain/bookingNotificationSubscriptions";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/adapters/apiV2/apiV2FilterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/adapters/apiV2/useApiV2RuntimeFields";
import {
  parseRsqlExpression,
  rsqlSelectors,
  serializeRsqlExpression,
} from "@/modules/common/table-list/rsql/rsqlCodec";
import {
  TableList,
  type TableListFilterButtons,
  type TableListProps,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterExpression, FilterState } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";
import { ArchiveBookableItemDialog } from "../bookable-items/ArchiveBookableItemDialog";
import { BookableItemsBulkActions } from "../bookable-items/BookableItemsBulkActions";
import {
  archiveBookingConfiguration,
  mutateBookableItems,
  permanentlyDeleteBookingConfiguration,
} from "../bookable-items/BookableItemsContent";
import { BookingConfigurationActionsMenu } from "../bookable-items/BookingConfigurationActionsMenu";
import { calendarSubscriptionQueryKey } from "../bookable-items/bookableItemCalendarSubscription";
import type { BookableItemsBulkAction } from "../bookable-items/bookableItemLifecycleHelpers";
import { useEligibleBookingTargets } from "../bookable-items/bookableItemsAdministrationAccess";
import { PermanentDeleteBookableItemDialog } from "../bookable-items/PermanentDeleteBookableItemDialog";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import {
  type AllBookableItem,
  type AvailabilityQuickFilter,
  hasAvailabilityFilter,
  keepAcrossMinutes,
  serverAvailabilityFilter,
  todayAvailabilityWindow,
  useAvailabilityCounts,
  withAvailability,
} from "./availabilityQuickFilters";
import { BookingNotificationBulkActions } from "./BookingNotificationBulkActions";

/** The instrument's Inventory location; the tooltip explains a bare container name such as "WB user1a". */
function InstrumentLocation({ name, globalId }: { name?: string | null; globalId?: string | null }) {
  const { t } = useTranslation("booking");
  if (name == null || globalId == null) return null;
  const workbench = name.startsWith("WB ");
  return (
    <Tooltip>
      <TooltipTrigger render={<span className="inline-flex min-w-0 items-center gap-1" />}>
        <InventoryLocationLink name={name} globalId={globalId} />
      </TooltipTrigger>
      <TooltipContent role="tooltip" className="rounded-sm">
        {workbench ? t("allBookableItems.location.workbenchDescription") : t("allBookableItems.location.description")}
      </TooltipContent>
    </Tooltip>
  );
}

function createAllBookableItemsConfig(
  availableNow: string,
  freeLaterToday: string,
  openRecordLabel: (globalId: string) => string,
) {
  return resolveCollectionConfig({
    slug: "all-bookable-items",
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:allBookableItems.singular",
      pluralKey: "booking:allBookableItems.plural",
    },
    defaultColumns: ["target"],
    listSearchableFields: ["target.name"],
    relationshipSources: bookingRelationshipSources,
    fields: [
      { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
      {
        name: "target",
        type: "relationship",
        relationTo: "booking-instruments",
        hasMany: false,
        labelKey: "booking:bookableItems.fields.target",
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: {
          width: 280,
          minWidth: 240,
          renderCell: ({ row }: { row: AllBookableItem }) =>
            row.target ? (
              <InventoryItem
                name={row.target.value.name}
                globalId={row.target.globalId}
                href={`/globalId/${row.target.globalId}`}
                idLinkLabel={openRecordLabel(row.target.globalId)}
                size="xs"
              >
                <InstrumentLocation
                  name={row.target.value.parentContainerName}
                  globalId={row.target.value.parentContainerGlobalId}
                />
              </InventoryItem>
            ) : (
              <UnknownItem size="xs" />
            ),
        },
      },
      {
        name: "availability",
        type: "select",
        labelKey: "booking:calendar.availability",
        options: [
          { label: availableNow, value: "available-now" },
          { label: freeLaterToday, value: "free-later-today" },
        ],
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: false,
        form: false,
      },
    ],
  } as const satisfies CollectionConfig<AllBookableItem>);
}

function comparisons(
  expression: FilterExpression<AllBookableItem> | null,
): readonly Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>[] {
  if (!expression) return [];
  if (expression.kind === "comparison") return [expression];
  if (expression.kind === "or") return [];
  return expression.children.flatMap(comparisons);
}

function filterValue(
  expression: FilterExpression<AllBookableItem> | null,
  field: "availability" | "target",
): string | undefined {
  const value = comparisons(expression).find(
    (comparison) => comparison.field === field && comparison.operator === "equals",
  )?.value;
  return typeof value === "string" ? value : undefined;
}

function availabilityMode(expression: FilterExpression<AllBookableItem> | null): AvailabilityQuickFilter | undefined {
  const values = new Set(
    comparisons(expression)
      .filter((rule) => rule.field === "availability")
      .map((rule) => rule.value),
  );
  if (values.size !== 1) return undefined;
  const value = filterValue(expression, "availability");
  return value === "available-now" || value === "free-later-today" ? value : undefined;
}

function filterExpression(
  target: string | undefined,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  const values: FilterExpression<AllBookableItem>[] = [];
  if (target) values.push({ kind: "comparison", field: "target", operator: "equals", value: target });
  if (availability) {
    values.push({ kind: "comparison", field: "availability", operator: "equals", value: availability });
  }
  return values.length === 0 ? null : values.length === 1 ? values[0] : { kind: "and", children: values };
}

const currentDate = () => new Date();

export type AllBookableItemsContentProps = {
  clock?: () => Date;
  /** @deprecated Display timezone comes from Booking preferences. */
  userTimeZone?: string;
};

export function AllBookableItemsContent({
  clock = currentDate,
  userTimeZone: _legacyUserTimeZone,
}: AllBookableItemsContentProps = {}) {
  const { data: currentUser } = useCurrentUserQuery();
  return (
    <AllBookableItemsContentForUser
      key={currentUser.id}
      subjectId={currentUser.id}
      directSysadmin={currentUser.hasSysAdminRole && !currentUser.session.operatedAs}
      clock={clock}
    />
  );
}

function AllBookableItemsContentForUser({
  clock = currentDate,
  subjectId,
  directSysadmin,
}: AllBookableItemsContentProps & { subjectId: number; directSysadmin: boolean }) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  // Add disappears once the caller is known to have no instrument to set up, so it offers no dead
  // end; it stays while that answer loads so the toolbar does not shift for everyone else.
  const eligibleTargets = useEligibleBookingTargets().data;
  const canAdd = eligibleTargets === undefined || eligibleTargets.length > 0;
  const sourceConfig = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
        (globalId) => commonT("tableList.filters.openRecord", { globalId }),
      ),
    [commonT, t],
  );
  const {
    date,
    availability: routeAvailability,
    target,
    mine = false,
    where,
    q,
    types,
    page = 1,
    pageSize = 20,
  } = useSearch({ from: "/booking/all-items" });
  const navigate = useNavigate({ from: "/booking/all-items" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const queryClient = useQueryClient();
  const [selectedRowIds, setSelectedRowIds] = useState<ReadonlySet<string>>(new Set());
  const [notificationFeedback, setNotificationFeedback] = useState<{ enabled: boolean; count: number }>();
  const runtimeSelectors = useMemo(() => (where ? rsqlSelectors(where) : []), [where]);
  const runtimeFieldState = useApiV2RuntimeFields<AllBookableItem>({
    resourceName: "booking-configurations",
    selectors: runtimeSelectors,
    request: { token, authScope: subjectId },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: ["availability"],
        // The same default-namespace translator as the REST API v2 table hook: shared table labels
        // live in `common`, and this page's own keys carry their `booking:` namespace.
        translate: (key, values) => String(commonT(key as never, values as never)),
      }),
    [commonT, runtimeFieldState.metadata, runtimeFieldState.runtimeFields, sourceConfig],
  );
  const preferences = useBookingDisplayPreferences();
  const userToday = todayInTimeZone(preferences.timeZone, clock());
  const selectedDate = date ?? userToday;
  const filters = useMemo<FilterState<AllBookableItem>>(
    () => ({
      search: q ?? "",
      expression: where ? parseRsqlExpression(where, config) : filterExpression(target, routeAvailability),
    }),
    [config, q, routeAvailability, target, where],
  );
  // The server applies availability as one top-level rule; an availability rule nested in an OR
  // group, which only a hand-written saved view can hold, is reported like an unknown field.
  const serverFilter = serverAvailabilityFilter(filters.expression);
  const invalidFilter =
    !runtimeFieldState.pending &&
    runtimeFieldState.error === null &&
    (Boolean(where && !filters.expression) || !serverFilter.supported);
  const runtimeFilterBlocked =
    runtimeFieldState.pending ||
    runtimeFieldState.error !== null ||
    runtimeFieldState.missing.length > 0 ||
    invalidFilter;
  const quickMode = availabilityMode(filters.expression);
  const bounds = useMemo(
    () =>
      displayInterval(
        selectedDate,
        preferences.timeZone,
        preferences.availabilityWindow.start,
        preferences.availabilityWindow.end,
      ),
    [preferences.availabilityWindow.end, preferences.availabilityWindow.start, preferences.timeZone, selectedDate],
  );
  const minute = useAlignedMinute(clock);
  const today = useMemo(
    () =>
      todayAvailabilityWindow(
        new Date(minute),
        preferences.timeZone,
        preferences.availabilityWindow.start,
        preferences.availabilityWindow.end,
      ),
    [minute, preferences.availabilityWindow.end, preferences.availabilityWindow.start, preferences.timeZone],
  );
  const serverWhere =
    serverFilter.supported && serverFilter.where ? serializeRsqlExpression(serverFilter.where) : undefined;
  const serverAvailability = serverFilter.supported ? serverFilter.availability : undefined;
  // Counts describe the item rules alone, so each chip shows what selecting it would find.
  const countsFilter = withAvailability(filters.expression, undefined);
  const counts = useAvailabilityCounts(
    token,
    subjectId,
    { q, types, mine, where: countsFilter ? serializeRsqlExpression(countsFilter) : undefined },
    today,
    !runtimeFilterBlocked,
  );
  // The table reads its own page; the server applies an availability rule before paging.
  const catalogueQueryKey = [
    "api-v2",
    "bookings",
    "booking-catalogue",
    "all-items",
    token,
    q,
    types,
    mine,
    serverWhere,
    page,
    pageSize,
    ...(serverAvailability ? [serverAvailability, today.start, today.end, today.now] : []),
  ];
  const catalogue = useQuery({
    queryKey: catalogueQueryKey,
    queryFn: ({ signal }) =>
      fetchBookingCatalogue(
        {
          q,
          types,
          mine,
          where: serverWhere,
          page,
          pageSize,
          availability: serverAvailability,
          availabilityWindow: today,
        },
        token,
        signal,
      ),
    enabled: !runtimeFilterBlocked && token.length > 0,
    staleTime: 30_000,
    placeholderData: serverAvailability ? keepAcrossMinutes(catalogueQueryKey) : undefined,
  });
  const catalogueItems = catalogue.data?.items ?? [];
  const rows = runtimeFilterBlocked ? [] : catalogueItems.map(catalogueItemAsConfiguration);
  // An unfiltered empty catalogue means the user has no bookable items yet, not that a filter hid them.
  const hasNoBookableItems =
    catalogue.isSuccess &&
    catalogue.data.total === 0 &&
    !q &&
    !where &&
    !target &&
    !routeAvailability &&
    !mine &&
    !types?.length;
  const notificationSubscriptionMutation = useMutation({
    mutationFn: ({ configurationIds, enabled }: { configurationIds: readonly number[]; enabled: boolean }) =>
      updateBookingNotificationSubscriptions(configurationIds, enabled, token),
    onMutate: () => setNotificationFeedback(undefined),
    onSuccess: async (subscriptions, variables) => {
      setSelectedRowIds(new Set());
      setNotificationFeedback({ enabled: variables.enabled, count: subscriptions.length });
      await queryClient.invalidateQueries({ queryKey: bookingNotificationSubscriptionsQueryKey.all(subjectId) });
    },
  });
  // A disabled, archived or deleted item leaves this list, so it also leaves the selection.
  const onLifecycleChanged = useCallback(
    async (configurationIds: readonly number[], permanent = false) => {
      setSelectedRowIds((current) => {
        const next = new Set(current);
        for (const id of configurationIds) next.delete(String(id));
        return next;
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        ...configurationIds.map((id) => queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(id) })),
        // A permanently deleted item's instrument becomes an eligible target again.
        ...(permanent
          ? [queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configuration-targets"] })]
          : []),
      ]);
    },
    [queryClient],
  );
  const [failedBulkAction, setFailedBulkAction] = useState<BookableItemsBulkAction | null>(null);
  const bulkMutation = useMutation({
    mutationFn: ({ action, rowIds }: { action: BookableItemsBulkAction; rowIds: readonly string[] }) =>
      mutateBookableItems(action, rowIds, token),
    onMutate: () => setFailedBulkAction(null),
    onSuccess: (_data, { rowIds }) => onLifecycleChanged(rowIds.map(Number)),
    onError: (_error, { action }) => setFailedBulkAction(action),
  });
  const selectionPending = notificationSubscriptionMutation.isPending || bulkMutation.isPending;
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  // A quick filter always describes today, so its rows show today's bars and book today.
  const rowsDate = quickMode ? today.date : selectedDate;
  const rowsBounds = quickMode ? today.bounds : bounds;
  const availability = useCalendarAvailability(availabilityRows, rowsBounds, token, subjectId);

  const setDate = (nextDate: string) => {
    setSelectedRowIds(new Set());
    const remaining = withAvailability(filters.expression, undefined);
    void navigate({
      search: (current) => ({
        ...current,
        date: nextDate === userToday ? undefined : nextDate,
        availability: undefined,
        target: undefined,
        where: remaining ? serializeRsqlExpression(remaining) : undefined,
        page: undefined,
      }),
      replace: true,
    });
  };
  const resetView = () => {
    setSelectedRowIds(new Set());
    void navigate({
      search: (current) => ({
        ...current,
        date: undefined,
        availability: undefined,
        target: undefined,
        mine: undefined,
        where: undefined,
        q: undefined,
        types: undefined,
        page: undefined,
      }),
      replace: true,
    });
  };
  const setFilters = (next: FilterState<AllBookableItem>) => {
    const nextWhere = next.expression ? serializeRsqlExpression(next.expression) : undefined;
    const nextSearch = next.search || undefined;
    if (nextSearch === q && nextWhere === where && !target && !routeAvailability && page === 1) {
      return;
    }
    setSelectedRowIds(new Set());
    void navigate({
      search: (current) => ({
        ...current,
        date: hasAvailabilityFilter(next.expression) ? undefined : current.date,
        availability: undefined,
        target: undefined,
        where: nextWhere,
        q: nextSearch,
        page: undefined,
      }),
      replace: true,
    });
  };
  const removeRestoredViewIssue = () => {
    setSelectedRowIds(new Set());
    void navigate({
      search: (current) => ({ ...current, where: undefined, page: undefined }),
      replace: true,
    });
  };
  const restoredViewIssue = runtimeFieldState.error
    ? {
        kind: "network" as const,
        encoded: where ?? "",
        retry: () => void runtimeFieldState.retry(),
        remove: removeRestoredViewIssue,
      }
    : !runtimeFieldState.pending && (runtimeFieldState.missing.length > 0 || invalidFilter)
      ? { kind: "invalid" as const, encoded: where ?? "", remove: removeRestoredViewIssue }
      : undefined;
  const tableProps: TableListProps<AllBookableItem> = {
    config,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    status:
      runtimeFieldState.error !== null || runtimeFieldState.missing.length > 0 || invalidFilter || catalogue.isError
        ? "error"
        : runtimeFieldState.pending || catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: runtimeFieldState.error ?? catalogue.error,
    restoredViewIssue,
    onSelectRuntimeField: runtimeFieldState.selectRuntimeField,
    runtimeFieldDefinitions: runtimeFieldState.runtimeFields,
    runtimeFieldAuthScope: subjectId,
    queryString: false,
    features: {
      filtering: {
        value: filters,
        onChange: setFilters,
      },
      sorting: false,
      columns: false,
      pagination: {
        value: { pageIndex: page - 1, pageSize },
        rowCount: catalogue.data?.total ?? 0,
        onChange: (nextPage) => {
          const nextPageNumber = nextPage.pageSize === pageSize ? nextPage.pageIndex + 1 : 1;
          if (nextPageNumber === page && nextPage.pageSize === pageSize) return;
          // Selection survives paging, as in Administration, so rows from several pages act together.
          void navigate({
            search: (current) => ({
              ...current,
              page: nextPageNumber > 1 ? nextPageNumber : undefined,
              pageSize: nextPage.pageSize,
            }),
            replace: true,
          });
        },
      },
    },
    selection: {
      value: selectedRowIds,
      onChange: (value) => {
        setSelectedRowIds(value);
        setFailedBulkAction(null);
      },
      disabled: selectionPending,
      maximumCount: 100,
      getRowLabel: (row) => row.target?.value.name ?? commonT("values.unknownItem"),
      renderActions: (selection) => {
        const selectedConfigurationIds = [...selection.selectedRowIds].map(Number);
        return (
          <>
            <BookingNotificationBulkActions
              selection={selection}
              selectedConfigurationIds={selectedConfigurationIds}
              pending={notificationSubscriptionMutation.isPending}
              onAction={(configurationIds, enabled) =>
                notificationSubscriptionMutation.mutateAsync({ configurationIds, enabled })
              }
            />
            {/* The server allows these bulk changes to sysadmins only, as in Administration. */}
            {directSysadmin ? (
              <BookableItemsBulkActions
                selection={selection}
                disabled={selectionPending}
                activeAction={bulkMutation.isPending ? (bulkMutation.variables?.action ?? null) : null}
                failedAction={failedBulkAction}
                offerEnable={false}
                onAction={(action, rowIds) => bulkMutation.mutateAsync({ action, rowIds: [...rowIds] })}
              />
            ) : null}
          </>
        );
      },
    },
  };

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
            <Tooltip>
              <TooltipTrigger
                render={
                  <Link
                    aria-label={detailsLabel}
                    className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                    data-slot="button"
                    to="/booking/bookable-items/$globalId/{-$tab}"
                    params={{ globalId: row.target.globalId, tab: undefined }}
                  />
                }
              >
                <EyeIcon aria-hidden="true" />
              </TooltipTrigger>
              <TooltipContent role="tooltip" className="rounded-sm">
                {detailsLabel}
              </TooltipContent>
            </Tooltip>
            {row.capabilities?.canCreateBooking ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Link
                      aria-label={bookLabel}
                      className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                      data-slot="button"
                      to="/booking/calendar/bookings/add"
                      search={{ date: rowsDate, target: row.target.globalId }}
                    />
                  }
                >
                  <CalendarPlusIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip" className="rounded-sm">
                  {bookLabel}
                </TooltipContent>
              </Tooltip>
            ) : null}
            {row.capabilities?.canEditConfiguration ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Link
                      aria-label={t("allBookableItems.actions.settings")}
                      className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                      data-slot="button"
                      to="/booking/bookable-items/$globalId/{-$tab}"
                      params={{ globalId: row.target.globalId, tab: "details" }}
                      search={{ edit: true }}
                    />
                  }
                >
                  <SettingsIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip" className="rounded-sm">
                  {t("allBookableItems.actions.settings")}
                </TooltipContent>
              </Tooltip>
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
            onArchived={(id) => onLifecycleChanged([id])}
          />
        ) : actionId === "permanent-delete" ? (
          <PermanentDeleteBookableItemDialog
            configuration={row}
            close={close}
            onDelete={(id, version) => permanentlyDeleteBookingConfiguration(id, version, token)}
            onDeleted={() => onLifecycleChanged([row.id], true)}
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
        const description =
          mode === "available-now"
            ? t("allBookableItems.quickFilters.availableNowDescription")
            : t("allBookableItems.quickFilters.freeLaterTodayDescription");
        return {
          id: mode,
          label: (
            <>
              {mode === "available-now"
                ? t("allBookableItems.quickFilters.availableNow")
                : t("allBookableItems.quickFilters.freeLaterToday")}
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
          description,
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
        rows={rows}
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
