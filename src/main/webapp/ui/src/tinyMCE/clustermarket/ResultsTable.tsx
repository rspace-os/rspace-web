import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableRow, { tableRowClasses } from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import EnhancedTableHead, { type Cell } from "../../components/EnhancedTableHead";
import { getSorting } from "../../util/table";
import type { BookingAndEquipmentDetails } from "./ClustermarketData";
import { BookingType, Order } from "./Enums";

type HeaderCellId =
  | "bookingID"
  | "equipmentName"
  | "manufacturer"
  | "model"
  | "requesterName"
  | "start_time"
  | "duration"
  | "bookingType"
  | "status";
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
}: {
  clustermarket_web_url: string;
  visibleHeaderCells: Array<Cell<HeaderCellId>>;
  results: Array<BookingAndEquipmentDetails>;
  order: (typeof Order)[keyof typeof Order];
  setOrder: (newOrder: (typeof Order)[keyof typeof Order]) => void;
  orderBy: string;
  setOrderBy: (newOrderBy: string) => void;
  selectedBookingIds: Array<string>;
  setSelectedBookingIds: (newSelection: Array<BookingAndEquipmentDetails["bookingID"]>) => void;
  bookingType: string;
}) {
  const { t } = useTranslation("apps");
  function onRowClick(_event: unknown, item_id: BookingAndEquipmentDetails["bookingID"]) {
    const newSelected = selectedBookingIds.includes(item_id)
      ? selectedBookingIds.filter((id) => id !== item_id)
      : [...selectedBookingIds, item_id];
    setSelectedBookingIds(newSelected);
  }
  function handleRequestSort(_event: React.MouseEvent<HTMLSpanElement>, property: string) {
    const isDesc = orderBy === property && order === Order.desc;
    setOrder(isDesc ? Order.asc : Order.desc);
    setOrderBy(property);
  }
  const getBookingOrEquipmentID = (booking: BookingAndEquipmentDetails) => {
    if (bookingType === BookingType.EQUIPMENT) {
      return booking.equipmentID;
    }
    return booking.bookingID;
  };
  return (
    <>
      <TableContainer sx={{ mb: "40px" }}>
        <Table aria-label={t("tinyMce.clustermarket.tableAria")}>
          <EnhancedTableHead
            headSx={{ background: "#F6F6F6" }}
            headCells={visibleHeaderCells}
            order={order}
            orderBy={orderBy}
            onRequestSort={handleRequestSort}
            selectAll={true}
            onSelectAllClick={(event) => {
              if (event.target.checked) {
                const newSelected = results.map((booking) => getBookingOrEquipmentID(booking));
                // @ts-expect-error looks like a mix up of id types
                return setSelectedBookingIds(newSelected);
              }
              setSelectedBookingIds([]);
            }}
            numSelected={selectedBookingIds.length}
            rowCount={results.length}
          />
          <TableBody>
            {results.toSorted(getSorting(order, orderBy)).map((booking, index) => {
              const isItemSelected =
                selectedBookingIds.indexOf(
                  // @ts-expect-error looks like a mix up of id types
                  getBookingOrEquipmentID(booking),
                ) !== -1;
              const labelId = `booking-search-results-checkbox-${index}`;
              return (
                <TableRow
                  id={labelId}
                  sx={{
                    [`&.${tableRowClasses.selected}`]: {
                      backgroundColor: "#e3f2fd",
                    },
                    [`&.${tableRowClasses.selected}:hover`]: {
                      backgroundColor: "#e3f2fd",
                    },
                  }}
                  hover
                  tabIndex={-1}
                  role="checkbox"
                  onClick={(event) =>
                    // @ts-expect-error looks like a mix up of id types
                    onRowClick(event, getBookingOrEquipmentID(booking))
                  }
                  aria-checked={isItemSelected}
                  selected={isItemSelected}
                  key={getBookingOrEquipmentID(booking)}
                >
                  <TableCell padding="checkbox">
                    <Checkbox
                      color="primary"
                      checked={isItemSelected}
                      slotProps={{
                        input: {
                          "aria-labelledby": labelId,
                        },
                      }}
                    />
                  </TableCell>
                  {visibleHeaderCells.map((cell, i) => (
                    <TableCell key={`${cell.id}${i}`} data-testid={`${cell.id}${index}`}>
                      {cell.id === "bookingID" ? (
                        <a
                          target="_blank"
                          href={`${clustermarket_web_url}accounts/${booking.labID}/my_bookings/${booking[cell.id]}`}
                          rel="noreferrer"
                        >
                          {booking[cell.id]}
                        </a>
                      ) : cell.id === "equipmentName" ? (
                        <a
                          target="_blank"
                          href={`${clustermarket_web_url}accounts/${booking.labID}/equipment/${booking.equipmentID}`}
                          rel="noreferrer"
                        >
                          {booking[cell.id]}
                        </a>
                      ) : (
                        (booking[cell.id] as React.ReactNode)
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          position: "fixed",
          left: 0,
          bottom: 0,
          width: "calc(100% - 16px)",
          ml: "8px",
          backgroundColor: "#f6f6f6",
        }}
      >
        <Typography sx={{ pl: "16px" }} component="span" variant="body2" color="textPrimary">
          {"Selected: "}
          {selectedBookingIds.length}
        </Typography>
      </Box>
    </>
  );
}
