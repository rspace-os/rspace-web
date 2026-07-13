import Skeleton from "@mui/material/Skeleton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import React, { useContext } from "react";
import { useTranslation } from "react-i18next";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import SearchContext from "../../stores/contexts/Search";
import { hasRequiredPermissions } from "../../stores/definitions/InventoryRecord";
import type { menuIDs } from "../../util/menuIDs";
import { paginationOptions } from "../../util/table";
import type { SplitButtonOption } from "../components/ContextMenu/ContextMenuSplitButton";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";
import CustomTableHead from "./components/CustomTableHead";
import ResultRow from "./components/ResultRow";
import ScrollBox from "./ScrollBox";

const ResultRowSkeleton = () => {
  const { isViewportSmall, isViewportLarge } = useViewportDimensions();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  let cols = 3;
  if (!isViewportSmall && isSingleColumnLayout) cols++;
  if (isViewportLarge && isSingleColumnLayout) cols++;
  return (
    <TableRow>
      <TableCell colSpan={cols}>
        <Skeleton variant="rectangular" width="100%" height={36} />
      </TableCell>
    </TableRow>
  );
};
type ResultsTableArgs = {
  contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};
function ResultsTable({ contextMenuId }: ResultsTableArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);
  const canSelectResult = React.useCallback(
    (record: (typeof search.filteredResults)[number]) =>
      !search.alwaysFilterOut(record) &&
      hasRequiredPermissions(record.permittedActions, search.uiConfig?.requiredPermissions),
    [search],
  );
  const selectableResults = React.useCallback(
    () => search.filteredResults.filter(canSelectResult),
    [canSelectResult, search],
  );
  const handleChangePageSize = (value: number) => {
    search.setPageSize(value);
  };
  const handleChangePage = (newPage: number) => {
    void search.setPage(newPage);
  };
  const toggleAll = () => {
    const results = selectableResults();
    const selected = results.some((r) => r.selected === false);
    // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
    results.forEach((r) => r.toggleSelected(selected));
  };
  const onSelectOptions: Array<SplitButtonOption> = [
    {
      text: t("search.resultsTable.selection.all"),
      selection: () => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        selectableResults().forEach((r) => r.toggleSelected(true));
      },
    },
    {
      text: t("search.resultsTable.selection.none"),
      selection: () => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        search.filteredResults.forEach((r) => r.toggleSelected(false));
      },
    },
    {
      text: t("search.resultsTable.selection.invert"),
      selection: () => {
        search.filteredResults.forEach((r) => {
          if (canSelectResult(r)) {
            r.toggleSelected();
            return;
          }
          r.toggleSelected(false);
        });
      },
    },
    {
      text: t("search.resultsTable.selection.mine"),
      selection: () => {
        search.filteredResults.forEach((r) => {
          r.toggleSelected(canSelectResult(r) && (r.currentUserIsOwner ?? false));
        });
      },
    },
    {
      text: t("search.resultsTable.selection.notMine"),
      selection: () => {
        search.filteredResults.forEach((r) => {
          r.toggleSelected(canSelectResult(r) && r.currentUserIsOwner === false); // if currentUserIsOwner cannot be determined then don't select
        });
      },
    },
    {
      text: t("search.resultsTable.selection.current"),
      selection: () => {
        search.filteredResults.forEach((r) => {
          r.toggleSelected(canSelectResult(r) && !r.deleted);
        });
      },
    },
    {
      text: t("search.resultsTable.selection.inTrash"),
      selection: () => {
        search.filteredResults.forEach((r) => {
          r.toggleSelected(canSelectResult(r) && r.deleted);
        });
      },
    },
  ];
  const count = search.count;
  const rowsPerPageOptions = paginationOptions(count);
  React.useEffect(() => {
    search.resetColumnLabelSettingsIfUnknown();
  });

  return (
    <>
      <ScrollBox overflowY="auto" overflowX="auto">
        <Table size="small" aria-label={t("search.resultsTable.label")} stickyHeader>
          <CustomTableHead
            selectedCount={search.selectedResults.length}
            onSelectOptions={onSelectOptions}
            toggleAll={toggleAll}
            contextMenuId={contextMenuId}
          />
          <TableBody>
            {search.fetcher.loading
              ? new Array<unknown>(search.fetcher.pageSize).fill(null).map((_, i) => <ResultRowSkeleton key={i} />)
              : search.filteredResults.map((result) => (
                  <ResultRow
                    key={result.globalId}
                    result={result}
                    adjustableColumns={search.uiConfig.adjustableColumns}
                  />
                ))}
          </TableBody>
        </Table>
      </ScrollBox>
      <nav>
        <TablePagination
          sx={{ overflow: "unset" }}
          labelRowsPerPage=""
          component="div"
          count={count}
          rowsPerPageOptions={rowsPerPageOptions}
          rowsPerPage={Math.min(search.fetcher.pageSize, count)}
          page={Number(search.fetcher.pageNumber) || 0}
          onPageChange={(_event: unknown, page: number) => handleChangePage(page)}
          onRowsPerPageChange={(e) => handleChangePageSize(Number(e.target.value))}
          slotProps={{
            select: {
              renderValue: (value: unknown) =>
                typeof value === "number" && value < count
                  ? value
                  : t("search.resultsTable.allRows", { count: String(value) }),
            },
          }}
        />
      </nav>
    </>
  );
}
export default observer(ResultsTable);
