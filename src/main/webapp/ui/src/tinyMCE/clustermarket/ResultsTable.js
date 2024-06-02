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
import Typography from "@mui/material/Typography";
import { BookingType, Order } from "./Enums";
import PropTypes from "prop-types";

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
  clustermarket_web_url,
  visibleHeaderCells,
  results,
  order,
  setOrder,
  orderBy,
  setOrderBy,
  selectedBookingIds,
  setSelectedBookingIds,
  bookingType,
}) {
  const { classes } = useStyles();

  function onRowClick(event, item_id) {
    const selectedIndex = selectedBookingIds.indexOf(item_id);
    let newSelected = [];

    if (selectedIndex === -1) {
      newSelected = newSelected.concat(selectedBookingIds, item_id);
    } else if (selectedIndex === 0) {
      newSelected = newSelected.concat(selectedBookingIds.slice(1));
    } else if (selectedIndex === selectedBookingIds.length - 1) {
      newSelected = newSelected.concat(selectedBookingIds.slice(0, -1));
    } else if (selectedIndex > 0) {
      newSelected = newSelected.concat(
        selectedBookingIds.slice(0, selectedIndex),
        selectedBookingIds.slice(selectedIndex + 1)
      );
    }

    setSelectedBookingIds(newSelected);
  }

  function handleRequestSort(event, property) {
    const isDesc = orderBy === property && order === Order.desc;
    setOrder(isDesc ? Order.asc : Order.desc);
    setOrderBy(property);
  }

  const getBookingOrEquipmentID = (booking) => {
    if (bookingType === BookingType.EQUIPMENT) {
      return booking.equipmentID;
    }
    return booking.bookingID;
  };

  return (
    <>
      <TableContainer className={classes.tableContainer}>
        <Table aria-label="booking search results">
          <EnhancedTableHead
            headStyle={classes.tableHead}
            headCells={visibleHeaderCells}
            order={order}
            orderBy={orderBy}
            onRequestSort={handleRequestSort}
            selectAll={true}
            onSelectAllClick={(event) => {
              if (event.target.checked) {
                const newSelected = results.map((booking) =>
                  getBookingOrEquipmentID(booking)
                );
                return setSelectedBookingIds(newSelected);
              }
              setSelectedBookingIds([]);
            }}
            numSelected={selectedBookingIds.length}
            rowCount={results.length}
          />
          <TableBody>
            {stableSort(results, getSorting(order, orderBy)).map(
              (booking, index) => {
                const isItemSelected =
                  selectedBookingIds.indexOf(
                    getBookingOrEquipmentID(booking)
                  ) !== -1;
                const labelId = `booking-search-results-checkbox-${index}`;

                return (
                  <TableRow
                    id={labelId}
                    className={classes.tableRow}
                    hover
                    tabIndex={-1}
                    role="checkbox"
                    onClick={(event) =>
                      onRowClick(event, getBookingOrEquipmentID(booking))
                    }
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
                        {cell.id === "bookingID" ? (
                          <a
                            target="_blank"
                            href={
                              clustermarket_web_url +
                              "accounts/" +
                              booking.labID +
                              "/my_bookings/" +
                              booking[cell.id]
                            }
                            rel="noreferrer"
                          >
                            {booking[cell.id]}
                          </a>
                        ) : cell.id === "equipmentName" ? (
                          <a
                            target="_blank"
                            href={
                              clustermarket_web_url +
                              "accounts/" +
                              booking.labID +
                              "/equipment/" +
                              booking.equipmentID
                            }
                            rel="noreferrer"
                          >
                            {booking[cell.id]}
                          </a>
                        ) : (
                          booking[cell.id]
                        )}
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
          Selected: {selectedBookingIds.length}
        </Typography>
      </div>
    </>
  );
}
ResultsTable.propTypes = {
  clustermarket_web_url: PropTypes.string,
  visibleHeaderCells: PropTypes.array,
  results: PropTypes.array,
  order: PropTypes.string,
  setOrder: PropTypes.func,
  orderBy: PropTypes.string,
  setOrderBy: PropTypes.func,
  selectedBookingIds: PropTypes.array,
  setSelectedBookingIds: PropTypes.func,
  bookingType: PropTypes.string,
};
