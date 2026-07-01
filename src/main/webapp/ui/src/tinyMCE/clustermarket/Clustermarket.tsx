// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import Checkbox from "@mui/material/Checkbox";
import CircularProgress from "@mui/material/CircularProgress";
import FormControl from "@mui/material/FormControl";
import Grid from "@mui/material/Grid";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import useLocalStorage from "../../hooks/browser/useLocalStorage";
import i18n from "../../modules/common/i18n";
import AnalyticsContext from "../../stores/contexts/Analytics";
import materialTheme from "../../theme";
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

const makeBookingHeaderCells = () => [
  {
    id: "bookingID" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.bookingId", { ns: "apps" }),
  },
  {
    id: "equipmentName" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.equipmentName", { ns: "apps" }),
  },
  {
    id: "manufacturer" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.manufacturer", { ns: "apps" }),
  },
  {
    id: "model" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.model", { ns: "apps" }),
  },
  {
    id: "requesterName" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.bookedBy", { ns: "apps" }),
  },
  {
    id: "start_time" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.startTime", { ns: "apps" }),
  },
  {
    id: "duration" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.durationMins", { ns: "apps" }),
  },
  {
    id: "bookingType" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.bookingType", { ns: "apps" }),
  },
  {
    id: "status" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.status", { ns: "apps" }),
  },
];
// Notes CAN be edited for bookings, however, only 3% of people ever add an additional note
// therefore its OK to just cache notes in the DB along with the other booking details
const makeMaintenanceNotesHeader = () => ({
  id: "maintenance_notes",
  numeric: false,
  label: i18n.t("tinyMce.clustermarket.columns.maintenanceNotes", { ns: "apps" }),
});
const makeEquipmentHeaderCells = () => [
  {
    id: "equipmentID" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.equipmentId", { ns: "apps" }),
  },
  {
    id: "equipmentName" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.equipmentName", { ns: "apps" }),
  },
  {
    id: "manufacturer" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.manufacturer", { ns: "apps" }),
  },
  {
    id: "model" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.model", { ns: "apps" }),
  },
  {
    id: "bookingType" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.bookingType", { ns: "apps" }),
  },
  {
    id: "bookingID" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.lastUse", { ns: "apps" }),
  },
  {
    id: "start_time" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.onDate", { ns: "apps" }),
  },
  {
    id: "requesterName" as const,
    numeric: false,
    label: i18n.t("tinyMce.clustermarket.columns.bookedBy", { ns: "apps" }),
  },
];
type ClustermarketArgs = {
  defaultBookingType: BOOKING_TYPE[keyof BOOKING_TYPE];
  clustermarket_web_url: string;
};
let VISIBLE_HEADER_CELLS: ReturnType<typeof makeBookingHeaderCells> | ReturnType<typeof makeEquipmentHeaderCells> =
  makeBookingHeaderCells();
let SELECTED_BOOKINGS: ReadonlyArray<BookingAndEquipmentDetails | EquipmentWithBookingDetails> = [];
export const getSelectedBookings = (): ReadonlyArray<BookingAndEquipmentDetails | EquipmentWithBookingDetails> =>
  SELECTED_BOOKINGS;
export const getHeaders = (): typeof VISIBLE_HEADER_CELLS => VISIBLE_HEADER_CELLS;
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
  const { t } = useTranslation("apps");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const bookingHeaderCells = makeBookingHeaderCells();
  const equipmentHeaderCells = makeEquipmentHeaderCells();
  const maintenanceNotes = makeMaintenanceNotesHeader();
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
  VISIBLE_HEADER_CELLS = bookingType === BookingType.EQUIPMENT ? equipmentHeaderCells : bookingHeaderCells;
  const addMaintanceNotesToHeaders = (headers: typeof VISIBLE_HEADER_CELLS) => {
    if (!headers.find((header) => header.id === maintenanceNotes.id)) {
      // @ts-expect-error type mismatch
      headers.splice(4, 0, maintenanceNotes);
    }
    return headers;
  };
  const removeMaintanceNotesFromHeaders = (headers: typeof VISIBLE_HEADER_CELLS) => {
    if (headers.find((header) => header.id === maintenanceNotes.id)) {
      headers.splice(4, 1);
    }
    return headers;
  };
  const setHeadersByBookingType = (newBookingType: BOOKING_TYPE[keyof BOOKING_TYPE], newIsMaintenance: boolean) => {
    if (newBookingType === BookingType.EQUIPMENT) {
      VISIBLE_HEADER_CELLS = newIsMaintenance
        ? addMaintanceNotesToHeaders(equipmentHeaderCells)
        : removeMaintanceNotesFromHeaders(equipmentHeaderCells);
    } else {
      VISIBLE_HEADER_CELLS = newIsMaintenance
        ? addMaintanceNotesToHeaders(bookingHeaderCells)
        : removeMaintanceNotesFromHeaders(bookingHeaderCells);
    }
  };
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
    setHeadersByBookingType(bookingType, isMaintenance);
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
    const selected_bookings: ReadonlyArray<BookingAndEquipmentDetails | EquipmentWithBookingDetails> =
      bookingType === BookingType.EQUIPMENT
        ? equipment.filter((item) => selectedBookingIds.includes(item.equipmentID))
        : bookings.filter((booking) => selectedBookingIds.includes(booking.bookingID));
    window.parent.postMessage(
      {
        mceAction: selected_bookings.length > 0 ? "enable" : "disable",
      },
      "*",
    );
    return selected_bookings;
  }, [selectedBookingIds]);
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
              label={t("tinyMce.clustermarket.bookingTypes.booked")}
            />
            <FormControlLabel
              data-testid="booked_and_completed_radio"
              value={BookingType.ALL}
              control={<Radio />}
              label={t("tinyMce.clustermarket.bookingTypes.all")}
            />
            <FormControlLabel
              value={BookingType.EQUIPMENT}
              control={<Radio />}
              label={t("tinyMce.clustermarket.bookingTypes.equipment")}
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
                  "aria-label": t("tinyMce.clustermarket.maintenanceLabel"),
                },
              }}
            />
          }
          label={t("tinyMce.clustermarket.maintenanceOnly")}
        />
        <Grid container spacing={1}>
          <Grid size={12}>
            <ResultsTable
              clustermarket_web_url={clustermarket_web_url}
              // @ts-expect-error type mismatch
              visibleHeaderCells={VISIBLE_HEADER_CELLS}
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
export default Clustermarket;
