// @flow

import React, { useEffect, type Node } from "react";
import { TableContainer } from "@mui/material";
import Table from "@mui/material/Table";
import EnhancedTableHead, {
  type Cell,
} from "../../components/EnhancedTableHead";
import TableBody from "@mui/material/TableBody";
import { getSorting, stableSort } from "../../util/table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Checkbox from "@mui/material/Checkbox";
import { makeStyles } from "tss-react/mui";
import TablePagination from "@mui/material/TablePagination";
import Typography from "@mui/material/Typography";
import { Order } from "./Enums";
import { type Article, type ArticleId } from "./JoveClient";

const useStyles = makeStyles()(() => ({
  tableContainer: {
    marginBottom: "40px",
  },
  tableHead: {
    background: "#F6F6F6",
  },
  tableRow: {
    "&.Mui-selected": {
      backgroundColor: "#e3f2fd",
    },
    "&.Mui-selected:hover": {
      backgroundColor: "#e3f2fd",
    },
  },
  tableFooterContainer: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    position: "fixed",
    left: "0",
    bottom: "0",
    width: "calc(100% - 16px)",
    marginLeft: "8px",
    backgroundColor: "#f6f6f6",
  },
  selectedRowCounter: {
    paddingLeft: "16px",
  },
  image: {
    maxWidth: "104px",
  },
}));

type ResultsTableArgs = {|
  page: number,
  visibleHeaderCells: Array<Cell<"" | "thumbnail" | "title" | "section">>,
  searchResults: Array<Article>,
  setSearchResults: (Array<Article>) => void,
  order: $Values<typeof Order>,
  setOrder: ($Values<typeof Order>) => void,
  orderBy: "" | "thumbnail" | "title" | "section",
  setOrderBy: ("" | "thumbnail" | "title" | "section") => void,
  selectedJoveIds: Array<ArticleId>,
  setSelectedJoveIds: (Array<ArticleId>) => void,
  onRowsPerPageChange: (number) => void,
  onPageChange: (number) => void,
  rowsPerPage: number,
  count: number,
|};

export default function ResultsTable({
  page,
  visibleHeaderCells,
  searchResults,
  setSearchResults,
  order,
  setOrder,
  orderBy,
  setOrderBy,
  selectedJoveIds,
  setSelectedJoveIds,
  onRowsPerPageChange,
  onPageChange,
  rowsPerPage,
  count,
}: ResultsTableArgs): Node {
  const { classes } = useStyles();

  function onRowClick(event: mixed, id: ArticleId) {
    const selectedIndex = selectedJoveIds.indexOf(id);
    let newSelected: Array<ArticleId> = [];

    if (selectedIndex === -1) {
      newSelected = newSelected.concat(selectedJoveIds, id);
    } else if (selectedIndex === 0) {
      newSelected = newSelected.concat(selectedJoveIds.slice(1));
    } else if (selectedIndex === selectedJoveIds.length - 1) {
      newSelected = newSelected.concat(selectedJoveIds.slice(0, -1));
    } else if (selectedIndex > 0) {
      newSelected = newSelected.concat(
        selectedJoveIds.slice(0, selectedIndex),
        selectedJoveIds.slice(selectedIndex + 1)
      );
    }

    setSelectedJoveIds(newSelected);
  }

  function handleRequestSort(
    event: mixed,
    property: "" | "thumbnail" | "title" | "section"
  ) {
    const isDesc = orderBy === property && order === Order.desc;
    setOrder(isDesc ? Order.asc : Order.desc);
    setOrderBy(property);
  }

  function handleChangePage(_: mixed, newPage: number) {
    onPageChange(newPage);
  }

  function handleChangeRowsPerPage(event: {
    target: { value: number, ... },
    ...
  }) {
    const num = parseInt(event.target.value, 10);
    onRowsPerPageChange(num);
  }

  useEffect(() => {
    setSearchResults(stableSort(searchResults, getSorting(order, orderBy)));
  }, [order, orderBy, page]);

  return (
    <>
      <TableContainer className={classes.tableContainer}>
        <Table aria-label="search results">
          <EnhancedTableHead
            headStyle={classes.tableHead}
            headCells={visibleHeaderCells}
            order={order}
            orderBy={orderBy}
            onRequestSort={handleRequestSort}
            selectAll={true}
            onSelectAllClick={(event) => {
              if (event.target.checked) {
                const newSelected = searchResults.map((article) => article.id);
                return setSelectedJoveIds(newSelected);
              }
              setSelectedJoveIds([]);
            }}
            numSelected={selectedJoveIds.length}
            rowCount={searchResults.length}
          />
          <TableBody>
            {searchResults.map((article, index) => {
              const isItemSelected = selectedJoveIds.indexOf(article.id) !== -1;
              const labelId = `article-search-results-checkbox-${index}`;

              return (
                <TableRow
                  id={labelId}
                  className={classes.tableRow}
                  hover
                  tabIndex={-1}
                  role="checkbox"
                  onClick={(event) => onRowClick(event, article.id)}
                  aria-checked={isItemSelected}
                  selected={isItemSelected}
                  key={index}
                >
                  <TableCell padding="checkbox">
                    <Checkbox
                      color="primary"
                      checked={isItemSelected}
                      inputProps={{ "aria-labelledby": labelId }}
                    />
                  </TableCell>
                  {visibleHeaderCells.map((cell, i) => (
                    <TableCell
                      key={`${cell.id}${i}`}
                      data-testid={`${cell.id}${index}`}
                    >
                      {cell.id === "title" &&
                        article[cell.id].replace(/<[^>]*>?/gm, "")}
                      {cell.id === "section" && article[cell.id]}
                      {cell.id === "thumbnail" && (
                        <img
                          src={article[cell.id]}
                          rel="noreferrer"
                          className={classes.image}
                        ></img>
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <div className={classes.tableFooterContainer}>
        <Typography
          className={classes.selectedRowCounter}
          component="span"
          variant="body2"
          color="textPrimary"
        >
          Selected: {selectedJoveIds.length}
        </Typography>
        <TablePagination
          rowsPerPageOptions={[5, 10, 20, 40].filter((c) => c <= count)}
          component="div"
          count={count}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </div>
    </>
  );
}
