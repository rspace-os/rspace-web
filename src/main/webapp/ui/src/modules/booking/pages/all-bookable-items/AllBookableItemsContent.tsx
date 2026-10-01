import { useNavigate, useSearch } from "@tanstack/react-router";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { TableListProps } from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/types";
import { AllBookableItemsTable } from "./AllBookableItemsTable";
import { type AllBookableItem, hasAvailabilityFilter, withAvailability } from "./availabilityQuickFilters";
import { useAllBookableItemsData } from "./useAllBookableItemsData";

const currentDate = () => new Date();

export type AllBookableItemsContentProps = {
  clock?: () => Date;
};

export function AllBookableItemsContent({ clock = currentDate }: AllBookableItemsContentProps = {}) {
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
  } = useSearch({ from: "/booking/all-items/" });
  const navigate = useNavigate({ from: "/booking/all-items/" });
  const { config, invalidFilter, catalogue, runtimeFieldState, currentUser, ...data } = useAllBookableItemsData({
    clock,
    date,
    routeAvailability,
    target,
    mine,
    where,
    q,
    types,
    page,
    pageSize,
  });
  const { filters, userToday, rows, quickFilterError, quickFilterPending } = data;

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
        mine: undefined,
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
  const removeRestoredViewIssue = () =>
    void navigate({
      search: (current) => ({ ...current, where: undefined, page: undefined }),
      replace: true,
    });
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
      runtimeFieldState.error !== null ||
      runtimeFieldState.missing.length > 0 ||
      invalidFilter ||
      quickFilterError ||
      catalogue.isError
        ? "error"
        : runtimeFieldState.pending || quickFilterPending || catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: runtimeFieldState.error ?? (quickFilterError ? undefined : catalogue.error),
    restoredViewIssue,
    onSelectRuntimeField: runtimeFieldState.selectRuntimeField,
    runtimeFieldDefinitions: runtimeFieldState.runtimeFields,
    runtimeFieldAuthScope: currentUser.id,
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
      {...data}
      types={types}
      mine={mine}
      tableProps={tableProps}
      setDate={setDate}
      resetView={resetView}
      setFilters={setFilters}
    />
  );
}
