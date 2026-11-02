import { useNavigate, useSearch } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { TableListProps } from "@/modules/common/table-list/TableList";
import type { FilterState } from "@/modules/common/table-list/types";
import { BookableItemsBulkActions } from "../bookable-items/BookableItemsBulkActions";
import type { AllBookableItem } from "../calendar/availability/availabilityQuickFilters";
import { DroppedFilterNotice } from "../DroppedFilterNotice";
import { AllBookableItemsTable } from "./AllBookableItemsTable";
import { useAllBookableItemsData } from "./useAllBookableItemsData";

export function AllBookableItemsContent() {
  const { data: currentUser } = useCurrentUserQuery();
  return (
    <AllBookableItemsContentForUser
      key={currentUser.id}
      subjectId={currentUser.id}
      directSysadmin={currentUser.hasSysAdminRole && !currentUser.session.operatedAs}
    />
  );
}

function AllBookableItemsContentForUser({ subjectId, directSysadmin }: { subjectId: number; directSysadmin: boolean }) {
  const { t: commonT } = useTranslation("common");
  const { target, where, q, types, page = 1, pageSize = 20 } = useSearch({ from: "/booking/all-items/" });
  const navigate = useNavigate({ from: "/booking/all-items/" });
  const {
    config,
    invalidFilter,
    catalogue,
    runtimeFieldState,
    selectedRowIds,
    bulkMutation,
    failedBulkAction,
    selectionPending,
    setFailedBulkAction,
    rows,
    ...data
  } = useAllBookableItemsData({
    target,
    subjectId,
    where,
    q,
    types,
    page,
    pageSize,
  });
  const { droppedFilterFields, filters, setSelectedRowIds, clearSelection } = data;

  const resetView = () => {
    clearSelection();
    void navigate({
      search: (current) => ({
        ...current,
        target: undefined,
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
    if (nextSearch === q && nextWhere === where && !target && page === 1) {
      return;
    }
    clearSelection();
    void navigate({
      search: (current) => ({
        ...current,
        target: undefined,
        where: nextWhere,
        q: nextSearch,
        page: undefined,
      }),
      replace: true,
    });
  };
  const removeRestoredViewIssue = () => {
    clearSelection();
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
    selection: directSysadmin
      ? {
          value: selectedRowIds,
          onChange: (value) => {
            setSelectedRowIds(value);
            setFailedBulkAction(null);
          },
          disabled: selectionPending,
          maximumCount: 100,
          getRowLabel: (row) => row.target?.value.name ?? commonT("values.unknownItem"),
          renderActions: (selection) => (
            <BookableItemsBulkActions
              selection={selection}
              disabled={selectionPending}
              activeAction={bulkMutation.isPending ? (bulkMutation.variables?.action ?? null) : null}
              failedAction={failedBulkAction}
              offerEnable={false}
              onAction={async (action, rowIds) => {
                await bulkMutation.mutateAsync({ action, rowIds: [...rowIds] });
              }}
            />
          ),
        }
      : undefined,
  };

  return (
    <>
      <DroppedFilterNotice fields={droppedFilterFields} />
      <AllBookableItemsTable
        {...data}
        types={types}
        directSysadmin={directSysadmin}
        tableProps={tableProps}
        resetView={resetView}
        setFilters={setFilters}
      />
    </>
  );
}
