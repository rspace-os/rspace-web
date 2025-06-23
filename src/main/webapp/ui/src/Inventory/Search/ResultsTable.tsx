import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import SearchContext from "../../stores/contexts/Search";
import { paginationOptions } from "../../util/table";
import { menuIDs } from "../../util/menuIDs";
import ResultRow from "./components/ResultRow";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TablePagination from "@mui/material/TablePagination";
import CustomTableHead from "./components/CustomTableHead";
import { type SplitButtonOption } from "../components/ContextMenu/ContextMenuSplitButton";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Skeleton from "@mui/material/Skeleton";
import { withStyles } from "Styles";
import ScrollBox from "./ScrollBox";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";
import useViewportDimensions from "../../util/useViewportDimensions";

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

const CustomTablePagination = withStyles<
  React.ComponentProps<typeof TablePagination>,
  { root: string }
>(() => ({
  root: {
    overflow: "unset",
  },
}))((props) => (
  <nav>
    <TablePagination labelRowsPerPage="" component="div" {...props} />
  </nav>
));

type ResultsTableArgs = {
  contextMenuId: (typeof menuIDs)[keyof typeof menuIDs];
};

function ResultsTable({ contextMenuId }: ResultsTableArgs): React.ReactNode {
  const { search } = useContext(SearchContext);

  const handleChangePageSize = (value: number) => {
    search.setPageSize(value);
  };

  const handleChangePage = (newPage: number) => {
    void search.setPage(newPage);
  };

  const toggleAll = () => {
    const results = search.filteredResults.filter(
      (r) => !search.alwaysFilterOut(r)
    );
    const selected = search.filteredResults.some((r) => r.selected === false);
    results.forEach((r) => r.toggleSelected(selected));
  };

  const onSelectOptions: Array<SplitButtonOption> = [
    {
      text: "All",
      selection: () => {
        search.filteredResults.forEach((r) => r.toggleSelected(true));
      },
    },
    {
      text: "None",
      selection: () => {
        search.filteredResults.forEach((r) => r.toggleSelected(false));
      },
    },
    {
      text: "Invert",
      selection: () => {
        search.filteredResults.forEach((r) => r.toggleSelected());
      },
    },
    {
      text: "Mine",
      selection: () => {
        search.filteredResults.forEach((r) =>
          r.toggleSelected(r.currentUserIsOwner ?? false)
        );
      },
    },
    {
      text: "Not Mine",
      selection: () => {
        search.filteredResults.forEach(
          (r) => r.toggleSelected(r.currentUserIsOwner === false) // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
    {
      text: "Current",
      selection: () => {
        search.filteredResults.forEach((r) => r.toggleSelected(!r.deleted));
      },
    },
    {
      text: "In Trash",
      selection: () => {
        search.filteredResults.forEach((r) => r.toggleSelected(r.deleted));
      },
    },
  ];

  const count = search.count;
  const rowsPerPageOptions = paginationOptions(count);

  return (
    <>
      <ScrollBox overflowY="auto">
        <Table size="small" aria-label="Search results" stickyHeader>
          <CustomTableHead
            selectedCount={search.selectedResults.length}
            onSelectOptions={onSelectOptions}
            toggleAll={toggleAll}
            contextMenuId={contextMenuId}
          />
          <TableBody>
            {search.fetcher.loading
              ? new Array<unknown>(search.fetcher.pageSize)
                  .fill(null)
                  .map((_, i) => <ResultRowSkeleton key={i} />)
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
      <CustomTablePagination
        count={count}
        rowsPerPageOptions={rowsPerPageOptions}
        labelRowsPerPage=""
        rowsPerPage={Math.min(search.fetcher.pageSize, count)}
        SelectProps={{
          renderValue: (value: unknown) =>
            typeof value === "number" && value < count
              ? value
              : `${String(value)} (All)`,
        }}
        page={Number(search.fetcher.pageNumber) || 0}
        onPageChange={(_event: unknown, page: number) => handleChangePage(page)}
        onRowsPerPageChange={(e) =>
          handleChangePageSize(Number(e.target.value))
        }
      />
    </>
  );
}

export default observer(ResultsTable);
