// @flow
import React, { useState, useEffect, useMemo } from "react";
import {
  getAllBookingDetails,
  getAllEquipmentDetails,
  getBookings,
} from "./ClustermarketClient";
import Checkbox from "@mui/material/Checkbox";
import {
  type BookingAndEquipmentDetails,
  type BookingDetails,
  type EquipmentDetails,
  type BOOKING_TYPE,
  type EquipmentWithBookingDetails,
  makeBookingAndEquipmentData,
  makeEquipmentWithBookingData,
} from "./ClustermarketData";
import Grid from "@mui/material/Grid";
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import CircularProgress from "@mui/material/CircularProgress";
import materialTheme from "../../theme";
import { ErrorReason, Order, BookingType } from "./Enums";
import ErrorView from "./ErrorView";
import ResultsTable from "./ResultsTable";
import useLocalStorage from "../../util/useLocalStorage";
import FormControl from "@mui/material/FormControl";
import { type UseState } from "../../util/types";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";

const TABLE_HEADER_CELLS = [
  { id: "bookingID" as const, numeric: false, label: "Booking ID" },
  { id: "equipmentName" as const, numeric: false, label: "Equipment Name" },
  { id: "manufacturer" as const, numeric: false, label: "Manufacturer" },
  { id: "model" as const, numeric: false, label: "Model" },
  { id: "requesterName" as const, numeric: false, label: "Booked by" },
  { id: "start_time" as const, numeric: false, label: "Start Time" },
  { id: "duration" as const, numeric: false, label: "Duration (mins)" },
  { id: "bookingType" as const, numeric: false, label: "Booking Type" },
  { id: "status" as const, numeric: false, label: "Status" },
];
// Notes CAN be edited for bookings, however, only 3% of people ever add an additional note
// therefore its OK to just cache notes in the DB along with the other booking details
const maintenanceNotes = {
  id: "maintenance_notes",
  numeric: false,
  label: "Maintenance notes",
};

const EQUIPMENT_TABLE_HEADER_CELLS = [
  { id: "equipmentID" as const, numeric: false, label: "Equipment ID" },
  { id: "equipmentName" as const, numeric: false, label: "Equipment Name" },
  { id: "manufacturer" as const, numeric: false, label: "Manufacturer" },
  { id: "model" as const, numeric: false, label: "Model" },
  { id: "bookingType" as const, numeric: false, label: "Booking Type" },
  { id: "bookingID" as const, numeric: false, label: "Last use" },
  { id: "start_time" as const, numeric: false, label: "On date" },
  { id: "requesterName" as const, numeric: false, label: "Booked by" },
];
type ClustermarketArgs = {
  defaultBookingType: BOOKING_TYPE[keyof BOOKING_TYPE];
  clustermarket_web_url: string;
};

let VISIBLE_HEADER_CELLS:
  | typeof TABLE_HEADER_CELLS
  | typeof EQUIPMENT_TABLE_HEADER_CELLS = TABLE_HEADER_CELLS;
let SELECTED_BOOKINGS: ReadonlyArray<
  BookingAndEquipmentDetails | EquipmentWithBookingDetails
> = [];
export const getSelectedBookings = (): ReadonlyArray<
  BookingAndEquipmentDetails | EquipmentWithBookingDetails
> => SELECTED_BOOKINGS;
export const getHeaders = (): typeof VISIBLE_HEADER_CELLS =>
  VISIBLE_HEADER_CELLS;
const ORDER_KEY = "clustermarketSearchOrder";
const ORDER_BY_KEY = "clustermarketSearchOrderBy";
const DEFAULT_ORDER = Order.asc;
const DEFAULT_ORDERBY = "start_time";
export const getOrder = (): string =>
  (localStorage.getItem(ORDER_KEY) || DEFAULT_ORDER).replace(/['"]+/g, "");
export const getOrderBy = (): string =>
  (localStorage.getItem(ORDER_BY_KEY) || DEFAULT_ORDERBY).replace(/['"]+/g, "");
function Clustermarket({
  defaultBookingType = BookingType.BOOKED,
  clustermarket_web_url,
}: ClustermarketArgs): React.ReactNode {
  const [bookings, setBookings]: UseState<Array<BookingAndEquipmentDetails>> =
    useState([] as Array<BookingAndEquipmentDetails>);
  const [equipment, setEquipment]: UseState<
    Array<EquipmentWithBookingDetails>
  > = useState([] as Array<EquipmentWithBookingDetails>);
  const [fetchDone, setFetchDone] = useState(false);
  const [errorReason, setErrorReason] = useState<
    (typeof ErrorReason)[keyof typeof ErrorReason]
  >(ErrorReason.None);
  const [errorMessage, setErrorMessage] = useState("");

  const [selectedBookingIds, setSelectedBookingIds] = useState<Array<string>>(
    []
  );
  const [order, setOrder] = useLocalStorage<(typeof Order)[keyof typeof Order]>(
    ORDER_KEY,
    DEFAULT_ORDER
  );
  const [bookingType, setBookingType] = useLocalStorage(
    "clustermarketBookingType",
    defaultBookingType
  );
  const [isMaintenance, setIsMaintenance] = useLocalStorage(
    "clustermarketIsMaintenance",
    false
  );
  const [orderBy, setOrderBy] = useLocalStorage(ORDER_BY_KEY, DEFAULT_ORDERBY);

  const addMaintanceNotesToHeaders = (headers: typeof VISIBLE_HEADER_CELLS) => {
    if (!headers.find((header) => header.id === maintenanceNotes.id)) {
      // @ts-expect-error type mismatch
      headers.splice(4, 0, maintenanceNotes);
    }
    return headers;
  };

  const removeMaintanceNotesFromHeaders = (
    headers: typeof VISIBLE_HEADER_CELLS
  ) => {
    if (headers.find((header) => header.id === maintenanceNotes.id)) {
      headers.splice(4, 1);
    }
    return headers;
  };

  const setHeadersByBookingType = (
    newBookingType: BOOKING_TYPE[keyof BOOKING_TYPE],
    newIsMaintenance: boolean
  ) => {
    if (newBookingType === BookingType.EQUIPMENT) {
      VISIBLE_HEADER_CELLS = isMaintenance
        ? addMaintanceNotesToHeaders(EQUIPMENT_TABLE_HEADER_CELLS)
        : removeMaintanceNotesFromHeaders(EQUIPMENT_TABLE_HEADER_CELLS);
    } else {
      VISIBLE_HEADER_CELLS = newIsMaintenance
        ? addMaintanceNotesToHeaders(TABLE_HEADER_CELLS)
        : removeMaintanceNotesFromHeaders(TABLE_HEADER_CELLS);
    }
  };

  function handleRequestError(error: {
    message: string;
    response: { status: number; data: string } | null;
  }) {
    if (error.message === "Network Error") {
      setErrorReason(ErrorReason.NetworkError);
    } else if (error.message.startsWith("timeout")) {
      setErrorReason(ErrorReason.Timeout);
    } else if (error.response) {
      if (error.response.status === 404) {
        setErrorReason(ErrorReason.NotFound);
      } else if (error.response.status === 401) {
        setErrorReason(ErrorReason.Unauthorized);
      } else if (error.response.status === 400) {
        setErrorMessage(error.response.data);
        setErrorReason(ErrorReason.BadRequest);
      }
    } else {
      setErrorReason(ErrorReason.UNKNOWN);
    }
  }

  const fetchBookings = async () => {
    setSelectedBookingIds([]);
    setHeadersByBookingType(bookingType, isMaintenance);
    setFetchDone(false);
    setBookings([]);
    setEquipment([]);
    try {
      const bookingsList = await getBookings(bookingType);
      if (bookingsList.length !== 0) {
        const bookingDetails: Array<BookingDetails> =
          await getAllBookingDetails(bookingsList);
        const equipmentDetails: Array<EquipmentDetails> =
          await getAllEquipmentDetails(bookingsList);
        if (bookingType === BookingType.EQUIPMENT) {
          const equipmentTableRows: Array<EquipmentWithBookingDetails> =
            makeEquipmentWithBookingData(
              bookingsList,
              bookingDetails,
              equipmentDetails,
              isMaintenance
            );
          setEquipment(equipmentTableRows);
          // eslint-disable-next-line no-undef
          // @ts-expect-error global
          RS.trackEvent("FetchClustermarketEquipmentData", {
            count: equipmentTableRows.length,
            bookingType: bookingType,
            isMaintenance: isMaintenance,
          });
        } else {
          const bookingTableRows: Array<BookingAndEquipmentDetails> =
            makeBookingAndEquipmentData(
              bookingsList,
              bookingDetails,
              equipmentDetails,
              isMaintenance
            );
          setBookings(bookingTableRows);
          // eslint-disable-next-line no-undef
          // @ts-expect-error global
          RS.trackEvent("FetchClustermarketBookingData", {
            count: bookingTableRows.length,
            bookingType: bookingType,
            isMaintenance: isMaintenance,
          });
        }
      }
      setFetchDone(true);
    } catch (error) {
      // @ts-expect-error error type mismatch
      handleRequestError(error);
    }
  };

  const handleBookingTypeChange = (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const newBookingType = event.target.value;
    setBookingType(newBookingType);
    setHeadersByBookingType(newBookingType, isMaintenance);
  };

  const handleMaintenanceChange = (checked: boolean) => {
    setIsMaintenance(checked);
  };

  useEffect(() => {
    void fetchBookings();
  }, [bookingType, isMaintenance]);

  SELECTED_BOOKINGS = useMemo(() => {
    const selected_bookings: ReadonlyArray<
      BookingAndEquipmentDetails | EquipmentWithBookingDetails
    > =
      bookingType === BookingType.EQUIPMENT
        ? equipment.filter((item) =>
            selectedBookingIds.includes(item.equipmentID)
          )
        : bookings.filter((booking) =>
            selectedBookingIds.includes(booking.bookingID)
          );

    window.parent.postMessage(
      {
        mceAction: selected_bookings.length > 0 ? "enable" : "disable",
      },
      "*"
    );

    return selected_bookings;
  }, [selectedBookingIds]);

  if (errorReason !== ErrorReason.None) {
    return <ErrorView errorReason={errorReason} errorMessage={errorMessage} />;
  }
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <FormControl>
          <RadioGroup
            row
            defaultValue={bookingType}
            name="radio-booking-type-group"
            onChange={handleBookingTypeChange}
          >
            <FormControlLabel
              data-testid="booked_radio"
              value={BookingType.BOOKED}
              control={<Radio />}
              label="Booked"
            />
            <FormControlLabel
              data-testid="booked_and_completed_radio"
              value={BookingType.ALL}
              control={<Radio />}
              label="Booked and Completed"
            />
            <FormControlLabel
              value={BookingType.EQUIPMENT}
              control={<Radio />}
              label="Booked Equipment"
            />
          </RadioGroup>
        </FormControl>
        <FormControlLabel
          control={
            <Checkbox
              checked={isMaintenance === true}
              size="small"
              onChange={(e) => handleMaintenanceChange(e.target.checked)}
              color="primary"
              inputProps={{ "aria-label": "maintenance" }}
            />
          }
          label={`maintenance only`}
        />
        <Grid container spacing={1}>
          <Grid item xs={12}>
            <ResultsTable
              clustermarket_web_url={clustermarket_web_url}
              // @ts-expect-error type mismatch
              visibleHeaderCells={VISIBLE_HEADER_CELLS}
              // @ts-expect-error type mismatch
              results={
                bookingType === BookingType.EQUIPMENT ? equipment : bookings
              }
              selectedBookingIds={selectedBookingIds}
              setSelectedBookingIds={setSelectedBookingIds}
              order={order}
              orderBy={orderBy}
              setOrder={setOrder}
              setOrderBy={setOrderBy}
              bookingType={bookingType}
            />
          </Grid>
          <Grid item xs={12} sx={{ align: "center" }}>
            {!fetchDone && <CircularProgress />}
          </Grid>
        </Grid>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export default Clustermarket;
