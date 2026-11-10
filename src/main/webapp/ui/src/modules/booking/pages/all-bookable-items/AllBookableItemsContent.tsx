import { useNavigate, useSearch } from "@tanstack/react-router";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { type TableListProps } from "@/modules/common/table-list/TableList";
import { type FilterState } from "@/modules/common/table-list/types";
import { AllBookableItemsTable } from "./AllBookableItemsTable";
import { type AllBookableItem, hasAvailabilityFilter, withAvailability } from "./availabilityQuickFilters";
import { useAllBookableItemsData } from "./useAllBookableItemsData";

export const currentDate = () => new Date();

export function AllBookableItemsContent({
  clock = currentDate,
  userTimeZone: _legacyUserTimeZone,
}: {
  clock?: () => Date;
  /** @deprecated Display timezone comes from Booking preferences. */
  userTimeZone?: string;
} = {}) {
  const {
    availability,
    bounds,
    catalogue,
    config,
    filters,
    invalidFilter,
    preferences,
    quickFilterError,
    quickFilterPending,
    quickIndex,
    quickMode,
    rows,
    selectedDate,
    t,
    userToday,
  } = useAllBookableItemsData({ clock, date, routeAvailability, target, where, q, types, page, pageSize });

  const {
    date,
    availability: routeAvailability,
    target,
    where,
    q,
    types,
    page = 1,
    pageSize = 20,
  } = useSearch({ from: "/booking/all-items/" });
  const navigate = useNavigate({ from: "/booking/all-items/" });

  const setDate = (nextDate: string) => {
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
  const resetView = () =>
    void navigate({
      search: (current) => ({
        ...current,
        date: undefined,
        availability: undefined,
        target: undefined,
        where: undefined,
        q: undefined,
        types: undefined,
        page: undefined,
      }),
      replace: true,
    });
  const setFilters = (next: FilterState<AllBookableItem>) => {
    const nextWhere = next.expression ? serializeRsqlExpression(next.expression) : undefined;
    const nextSearch = next.search || undefined;
    if (nextSearch === q && nextWhere === where && !target && !routeAvailability && page === 1) {
      return;
    }
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
  const tableProps: TableListProps<AllBookableItem> = {
    config,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    status:
      invalidFilter || quickFilterError || catalogue.isError
        ? "error"
        : quickFilterPending || catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: quickFilterError ? undefined : catalogue.error,
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
  };

  return (
    <AllBookableItemsTable
      availability={availability}
      bounds={bounds}
      filters={filters}
      preferences={preferences}
      quickFilterError={quickFilterError}
      quickFilterPending={quickFilterPending}
      quickIndex={quickIndex}
      quickMode={quickMode}
      resetView={resetView}
      rows={rows}
      selectedDate={selectedDate}
      setDate={setDate}
      setFilters={setFilters}
      t={t}
      tableProps={tableProps}
      types={types}
      userToday={userToday}
    />
  );
}
