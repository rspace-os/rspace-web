import React from "react";
import { TableContainer } from "@mui/material";
import Table from "@mui/material/Table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import TableBody from "@mui/material/TableBody";
import { getSorting, stableSort } from "../../util/table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Checkbox from "@mui/material/Checkbox";
import { makeStyles } from "tss-react/mui";
import TablePagination from "@mui/material/TablePagination";
import Typography from "@mui/material/Typography";
import { Order } from "./Enums";
import useLocalStorage from "../../util/useLocalStorage";

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
}));

export default function ResultsTable({
  page,
  setPage,
  visibleHeaderCells,
  animals,
  order,
  setOrder,
  orderBy,
  setOrderBy,
  selectedAnimalIds,
  setSelectedAnimalIds,
  onRowsPerPageChange,
  onPageChange,
  rowsPerPage,
  count,
}) {
  const { classes } = useStyles();

  function onRowClick(event, eartag) {
    const selectedIndex = selectedAnimalIds.indexOf(eartag);
    let newSelected = [];

    if (selectedIndex === -1) {
      newSelected = newSelected.concat(selectedAnimalIds, eartag);
    } else if (selectedIndex === 0) {
      newSelected = newSelected.concat(selectedAnimalIds.slice(1));
    } else if (selectedIndex === selectedAnimalIds.length - 1) {
      newSelected = newSelected.concat(selectedAnimalIds.slice(0, -1));
    } else if (selectedIndex > 0) {
      newSelected = newSelected.concat(
        selectedAnimalIds.slice(0, selectedIndex),
        selectedAnimalIds.slice(selectedIndex + 1)
      );
    }

    setSelectedAnimalIds(newSelected);
  }

  function handleRequestSort(event, property) {
    const isDesc = orderBy === property && order === Order.desc;
    setOrder(isDesc ? Order.asc : Order.desc);
    setOrderBy(property);
    onPageChange(0);
  }

  function handleChangePage(_, newPage) {
    onPageChange(newPage);
  }

  function handleChangeRowsPerPage(event) {
    const num = parseInt(event.target.value, 10);
    onRowsPerPageChange(num);
  }

  return (
    <>
      <TableContainer className={classes.tableContainer}>
        <Table aria-label="animal search results">
          <EnhancedTableHead
            headStyle={classes.tableHead}
            headCells={visibleHeaderCells}
            order={order}
            orderBy={orderBy}
            onRequestSort={handleRequestSort}
            selectAll={true}
            onSelectAllClick={(event) => {
              if (event.target.checked) {
                const newSelected = animals.map(
                  (animal) => animal.eartag_or_id
                );
                return setSelectedAnimalIds(newSelected);
              }
              setSelectedAnimalIds([]);
            }}
            numSelected={selectedAnimalIds.length}
            rowCount={animals.length}
          />
          <TableBody>
            {stableSort(animals, getSorting(order, orderBy)).map(
              (animal, index) => {
                const isItemSelected =
                  selectedAnimalIds.indexOf(animal.eartag_or_id) !== -1;
                const labelId = `animal-search-results-checkbox-${index}`;

                return (
                  <TableRow
                    id={labelId}
                    className={classes.tableRow}
                    hover
                    tabIndex={-1}
                    role="checkbox"
                    onClick={(event) => onRowClick(event, animal.eartag_or_id)}
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
                      // owner and responsible person could have the same name
                      <TableCell key={`${cell.id}${i}`}>
                        {animal[cell.id]}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              }
            )}
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
          Selected: {selectedAnimalIds.length}
        </Typography>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50].filter((c) => c <= count)}
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
