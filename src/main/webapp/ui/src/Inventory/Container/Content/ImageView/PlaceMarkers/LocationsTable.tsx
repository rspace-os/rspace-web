import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React, { type MouseEvent } from "react";
import { useTranslation } from "react-i18next";
import type { Location } from "../../../../../stores/definitions/Container";
import InventoryBaseRecord from "../../../../../stores/models/InventoryBaseRecord";
import { preventEventBubbling } from "../../../../../util/Util";
import InfoBadge from "../../../../components/InfoBadge";
import InfoCard from "../../../../components/InfoCard";
import { LOCATION_TAPPED_EVENT } from "../../../Fields/LocationsImageMarkersDialog";
import NumberedLocation from "../NumberedLocation";
import type { TappedLocationData } from "./ContentImage";

type LocationsTableArgs = {
  locations: Array<Location>;
  onRemove: ({ location, number }: { location: Location; number: number }) => void;
  isCompactView: boolean;
  parentRef: React.RefObject<HTMLElement | null>;
  selected?: number;
  onClick: (data: TappedLocationData) => void;
};

function LocationsTable({
  locations,
  onRemove,
  isCompactView,
  parentRef,
  selected,
  onClick,
}: LocationsTableArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const tableBody = React.useRef(null);

  type CustomEvent = {
    detail: { number: number };
  };

  const listener = (num: number, rowRef: React.RefObject<HTMLTableRowElement | null>) => (event: Event) => {
    const customEvent = event as unknown as CustomEvent;
    const tappedNum = customEvent.detail.number;
    if (tappedNum === num && isCompactView && rowRef.current) {
      rowRef.current.scrollIntoView({
        behavior: "smooth",
      });
    }
  };

  const LocationTableRow = ({ location, number }: { location: Location; number: number }) => {
    const rowRef = React.createRef<HTMLTableRowElement>();
    const l = listener(number, rowRef);
    const mark = { location, number, point: { left: 0, top: 0 } }; // point is unused, but necessary for type

    React.useEffect(() => {
      const tb = parentRef.current;
      if (tb) {
        tb.addEventListener(LOCATION_TAPPED_EVENT, l);
        return () => tb.removeEventListener(LOCATION_TAPPED_EVENT, l);
      }
      return undefined;
    });

    return (
      <TableRow key={number} ref={rowRef} onClick={() => onClick(mark)}>
        <TableCell sx={{ padding: 0 }}>
          <NumberedLocation number={number} inline selected={Boolean(selected) && selected === number} />
        </TableCell>
        <TableCell sx={{ padding: "10px" }}>
          {location.name && location.content instanceof InventoryBaseRecord && (
            <InfoBadge inline record={location.content}>
              <InfoCard record={location.content} />
            </InfoBadge>
          )}
          {location.name ?? (
            <Typography variant="inherit" component="span" sx={{ paddingLeft: "20px" }}>
              {"—"}
            </Typography>
          )}
        </TableCell>
        <TableCell sx={{ paddingRight: 0 }}>
          <ButtonGroup color="primary" size="small">
            {!location.hasContent && (
              <Button onClick={(e: MouseEvent<HTMLButtonElement>) => preventEventBubbling(() => onRemove(mark))(e)}>
                {t("container.content.placeMarkers.actions.remove")}
              </Button>
            )}
          </ButtonGroup>
        </TableCell>
      </TableRow>
    );
  };

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell sx={{ padding: "10px" }}>{t("container.content.placeMarkers.columns.location")}</TableCell>
            <TableCell sx={{ padding: "10px" }}>{t("container.content.placeMarkers.columns.content")}</TableCell>
            <TableCell sx={{ width: 1 }} align="right" component="th">
              {t("container.content.placeMarkers.columns.actions")}
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody ref={tableBody}>
          {locations.map((location, index) => (
            <LocationTableRow location={location} number={index + 1} key={index} />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default observer(LocationsTable);
