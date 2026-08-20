import Checkbox from "@mui/material/Checkbox";
import CircularProgress from "@mui/material/CircularProgress";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import type { TFunction } from "i18next";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import useLocalStorage from "../../hooks/browser/useLocalStorage";
import AnalyticsContext from "../../stores/contexts/Analytics";
import materialTheme from "../../theme";
import { getSorting } from "../../util/table";
import type { UseState } from "../../util/types";
import { getAllBookingDetails, getAllEquipmentDetails, getBookings } from "./ClustermarketClient";
import {
  type BOOKING_TYPE,
  type BookingAndEquipmentDetails,
  type BookingDetails,
  type EquipmentDetails,
  type EquipmentWithBookingDetails,
  makeBookingAndEquipmentData,
  makeEquipmentWithBookingData,
} from "./ClustermarketData";
import { BookingType, ErrorReason, Order } from "./Enums";
import ErrorView from "./ErrorView";
import ResultsTable from "./ResultsTable";

type ClustermarketArgs = {
  defaultBookingType: BOOKING_TYPE[keyof BOOKING_TYPE];
  clustermarket_web_url: string;
};
type HeaderCell = {
  id: string;
  numeric: boolean;
  label: string;
};
const ORDER_KEY = "clustermarketSearchOrder";
const ORDER_BY_KEY = "clustermarketSearchOrderBy";
const DEFAULT_ORDER = Order.asc;
const DEFAULT_ORDERBY = "start_time";
export const getOrder = (): string => (localStorage.getItem(ORDER_KEY) || DEFAULT_ORDER).replace(/['"]+/g, "");
export const getOrderBy = (): string => (localStorage.getItem(ORDER_BY_KEY) || DEFAULT_ORDERBY).replace(/['"]+/g, "");
function Clustermarket({
  defaultBookingType = BookingType.BOOKED,
  clustermarket_web_url,
}: ClustermarketArgs): React.ReactNode {
  const { t } = useTranslation("workspace");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [bookings, setBookings]: UseState<Array<BookingAndEquipmentDetails>> = useState(
    [] as Array<BookingAndEquipmentDetails>,
  );
  const [equipment, setEquipment]: UseState<Array<EquipmentWithBookingDetails>> = useState(
    [] as Array<EquipmentWithBookingDetails>,
  );
  const [fetchDone, setFetchDone] = useState(false);
  const [errorReason, setErrorReason] = useState<(typeof ErrorReason)[keyof typeof ErrorReason]>(ErrorReason.None);
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedBookingIds, setSelectedBookingIds] = useState<Array<string>>([]);
  const [order, setOrder] = useLocalStorage<(typeof Order)[keyof typeof Order]>(ORDER_KEY, DEFAULT_ORDER);
  const [bookingType, setBookingType] = useLocalStorage("clustermarketBookingType", defaultBookingType);
  const [isMaintenance, setIsMaintenance] = useLocalStorage("clustermarketIsMaintenance", false);
  const [orderBy, setOrderBy] = useLocalStorage(ORDER_BY_KEY, DEFAULT_ORDERBY);
  const visibleHeaderCells = useMemo(() => {
    const bookingHeaderCells: Array<HeaderCell> = [
      {
        id: "bookingID",
        numeric: false,
        label: t("tinymce.clustermarket.columns.bookingId"),
      },
      {
        id: "equipmentName",
        numeric: false,
        label: t("tinymce.clustermarket.columns.equipmentName"),
      },
      {
        id: "manufacturer",
        numeric: false,
        label: t("tinymce.clustermarket.columns.manufacturer"),
      },
      {
        id: "model",
        numeric: false,
        label: t("tinymce.clustermarket.columns.model"),
      },
      {
        id: "requesterName",
        numeric: false,
        label: t("tinymce.clustermarket.columns.bookedBy"),
      },
      {
        id: "start_time",
        numeric: false,
        label: t("tinymce.clustermarket.columns.startTime"),
      },
      {
        id: "duration",
        numeric: false,
        label: t("tinymce.clustermarket.columns.durationMins"),
      },
      {
        id: "bookingType",
        numeric: false,
        label: t("tinymce.clustermarket.columns.bookingType"),
      },
      {
        id: "status",
        numeric: false,
        label: t("tinymce.clustermarket.columns.status"),
      },
    ];
    const equipmentHeaderCells: Array<HeaderCell> = [
      {
        id: "equipmentID",
        numeric: false,
        label: t("tinymce.clustermarket.columns.equipmentId"),
      },
      {
        id: "equipmentName",
        numeric: false,
        label: t("tinymce.clustermarket.columns.equipmentName"),
      },
      {
        id: "manufacturer",
        numeric: false,
        label: t("tinymce.clustermarket.columns.manufacturer"),
      },
      {
        id: "model",
        numeric: false,
        label: t("tinymce.clustermarket.columns.model"),
      },
      {
        id: "bookingType",
        numeric: false,
        label: t("tinymce.clustermarket.columns.bookingType"),
      },
      {
        id: "bookingID",
        numeric: false,
        label: t("tinymce.clustermarket.columns.lastUse"),
      },
      {
        id: "start_time",
        numeric: false,
        label: t("tinymce.clustermarket.columns.onDate"),
      },
      {
        id: "requesterName",
        numeric: false,
        label: t("tinymce.clustermarket.columns.bookedBy"),
      },
    ];
    const headers = bookingType === BookingType.EQUIPMENT ? equipmentHeaderCells : bookingHeaderCells;
    if (isMaintenance) {
      // Notes CAN be edited for bookings, however, only 3% of people ever add an additional note
      // therefore its OK to just cache notes in the DB along with the other booking details
      headers.splice(4, 0, {
        id: "maintenance_notes",
        numeric: false,
        label: t("tinymce.clustermarket.columns.maintenanceNotes"),
      });
    }
    return headers;
  }, [bookingType, isMaintenance, t]);
  function handleRequestError(error: {
    message: string;
    response: {
      status: number;
      data: string;
    } | null;
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
    setFetchDone(false);
    setBookings([]);
    setEquipment([]);
    try {
      const bookingsList = await getBookings(bookingType);
      if (bookingsList.length !== 0) {
        const bookingDetails: Array<BookingDetails> = await getAllBookingDetails(bookingsList);
        const equipmentDetails: Array<EquipmentDetails> = await getAllEquipmentDetails(bookingsList);
        if (bookingType === BookingType.EQUIPMENT) {
          const equipmentTableRows: Array<EquipmentWithBookingDetails> = makeEquipmentWithBookingData(
            bookingsList,
            bookingDetails,
            equipmentDetails,
            isMaintenance,
          );
          setEquipment(equipmentTableRows);
          trackEvent("FetchClustermarketEquipmentData", {
            count: equipmentTableRows.length,
            bookingType: bookingType,
            isMaintenance: isMaintenance,
          });
        } else {
          const bookingTableRows: Array<BookingAndEquipmentDetails> = makeBookingAndEquipmentData(
            bookingsList,
            bookingDetails,
            equipmentDetails,
            isMaintenance,
          );
          setBookings(bookingTableRows);
          trackEvent("FetchClustermarketBookingData", {
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
  const handleBookingTypeChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setBookingType(event.target.value);
  };
  const handleMaintenanceChange = (checked: boolean) => {
    setIsMaintenance(checked);
  };
  useEffect(() => {
    void fetchBookings();
  }, [bookingType, isMaintenance]);
  const selectedBookings: ReadonlyArray<BookingAndEquipmentDetails | EquipmentWithBookingDetails> = useMemo(
    () =>
      bookingType === BookingType.EQUIPMENT
        ? equipment.filter((item) => selectedBookingIds.includes(item.equipmentID))
        : bookings.filter((booking) => selectedBookingIds.includes(booking.bookingID)),
    [bookingType, bookings, equipment, selectedBookingIds],
  );

  // The dialog runs in an iframe and does not touch the editor itself. It tells
  // the plugin whether the Insert button has anything to insert.
  useEffect(() => {
    window.parent.postMessage(
      { mceAction: selectedBookings.length > 0 ? "enable" : "disable" },
      window.location.origin,
    );
  }, [selectedBookings]);

  // The plugin asks for the table when Insert is clicked and inserts what comes
  // back, so the dialog builds it only when the user is finished with it.
  useEffect(() => {
    const respondToInsertRequest = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if ((event.data as { mceAction?: string } | null)?.mceAction !== "clustermarket-insert") return;
      const table =
        selectedBookings.length > 0
          ? createTinyMceTable(selectedBookings, visibleHeaderCells, order, orderBy, clustermarket_web_url, t)
          : null;
      window.parent.postMessage(
        { mceAction: "clustermarket-table", tableHtml: table?.outerHTML ?? null },
        window.location.origin,
      );
    };
    window.addEventListener("message", respondToInsertRequest);
    return () => {
      window.removeEventListener("message", respondToInsertRequest);
    };
  }, [selectedBookings, visibleHeaderCells, order, orderBy, clustermarket_web_url, t]);
  if (errorReason !== ErrorReason.None) {
    return <ErrorView errorReason={errorReason} errorMessage={errorMessage} />;
  }
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <FormControl>
          <RadioGroup row defaultValue={bookingType} name="radio-booking-type-group" onChange={handleBookingTypeChange}>
            <FormControlLabel
              data-testid="booked_radio"
              value={BookingType.BOOKED}
              control={<Radio />}
              label={t("tinymce.clustermarket.bookingTypes.booked")}
            />
            <FormControlLabel
              data-testid="booked_and_completed_radio"
              value={BookingType.ALL}
              control={<Radio />}
              label={t("tinymce.clustermarket.bookingTypes.all")}
            />
            <FormControlLabel
              value={BookingType.EQUIPMENT}
              control={<Radio />}
              label={t("tinymce.clustermarket.bookingTypes.equipment")}
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
              slotProps={{
                input: {
                  "aria-label": t("tinymce.clustermarket.maintenanceLabel"),
                },
              }}
            />
          }
          label={t("tinymce.clustermarket.maintenanceOnly")}
        />
        <Grid container spacing={1}>
          <Grid size={12}>
            <ResultsTable
              clustermarket_web_url={clustermarket_web_url}
              // @ts-expect-error type mismatch
              visibleHeaderCells={visibleHeaderCells}
              // @ts-expect-error type mismatch
              results={bookingType === BookingType.EQUIPMENT ? equipment : bookings}
              selectedBookingIds={selectedBookingIds}
              setSelectedBookingIds={setSelectedBookingIds}
              order={order}
              orderBy={orderBy}
              setOrder={setOrder}
              setOrderBy={setOrderBy}
              bookingType={bookingType}
            />
          </Grid>
          <Grid
            sx={{
              align: "center",
            }}
            size={12}
          >
            {!fetchDone && <CircularProgress />}
          </Grid>
        </Grid>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
function createTinyMceTable(
  selectedBookings: ReadonlyArray<BookingAndEquipmentDetails | EquipmentWithBookingDetails>,
  visibleHeaderCells: Array<HeaderCell>,
  order: (typeof Order)[keyof typeof Order],
  orderBy: string,
  clustermarketWebUrl: string,
  t: TFunction<"workspace">,
) {
  const clustermarketTable = document.createElement("table");
  clustermarketTable.setAttribute("data-tableSource", "clustermarket");

  const tableHeader = document.createElement("tr");
  const headersWithNotes = visibleHeaderCells
    .slice(0, 4)
    .concat(
      [{ id: "notes", numeric: false, label: t("tinymce.clustermarket.columns.notes") }],
      visibleHeaderCells.slice(4),
    );
  headersWithNotes.forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  clustermarketTable.appendChild(tableHeader);
  selectedBookings
    .toSorted(getSorting(order, orderBy))
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    .forEach((booking: any) => {
      const row = document.createElement("tr");

      headersWithNotes.forEach((headerCell) => {
        const cell = document.createElement("td");

        const textContent = booking[headerCell.id];
        if (headerCell.id === "bookingID") {
          const link = document.createElement("a");
          link.href = `${clustermarketWebUrl}accounts/${booking.labID}/my_bookings/${booking[headerCell.id]}`;
          link.target = "_blank";
          link.rel = "noreferrer";
          link.text = booking[headerCell.id];
          cell.appendChild(link);
        } else if (headerCell.id === "equipmentName") {
          const link = document.createElement("a");
          link.href = `${clustermarketWebUrl}accounts/${booking.labID}/equipment/${booking.equipmentID}`;
          link.target = "_blank";
          link.rel = "noreferrer";
          link.text = booking[headerCell.id];
          cell.appendChild(link);
        } else if (textContent) cell.textContent = textContent;

        row.appendChild(cell);
      });

      clustermarketTable.appendChild(row);
    });
  return clustermarketTable;
}

export default Clustermarket;
