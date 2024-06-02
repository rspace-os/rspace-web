// @flow

import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import NumberedLocation from "../NumberedLocation";
import React, {
  type Node,
  type ComponentType,
  type ElementRef,
  type ElementProps,
} from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { preventEventBubbling } from "../../../../../util/Util";
import { observer } from "mobx-react-lite";
import { withStyles } from "Styles";
import InfoBadge from "../../../../components/InfoBadge";
import InfoCard from "../../../../components/InfoCard";
import Result from "../../../../../stores/models/Result";
import { LOCATION_TAPPED_EVENT } from "../../../Fields/LocationsImageMarkersDialog";
import { type TappedLocationData } from "./ContentImage";
import Box from "@mui/material/Box";
import { type Location } from "../../../../../stores/definitions/Container";

type LocationsTableArgs = {|
  locations: Array<Location>,
  onRemove: ({ location: Location, number: number }) => void,
  isCompactView: boolean,
  parentRef: ElementRef<typeof Box>,
  selected: ?number,
  onClick: (TappedLocationData) => void,
|};

function LocationsTable({
  locations,
  onRemove,
  isCompactView,
  parentRef,
  selected,
  onClick,
}: LocationsTableArgs): Node {
  const tableBody = React.useRef(null);

  const CompactTableCell = withStyles<
    ElementProps<typeof TableCell>,
    {},
    { root: string }
  >(() => ({
    root: {
      padding: 10,
    },
  }))(TableCell);

  const MinColumnWidthHeadCell = withStyles<
    ElementProps<typeof TableCell>,
    {},
    { root: string }
  >(() => ({
    root: {
      width: 1,
    },
  }))(CompactTableCell);

  const NumberedLocationTableCell = withStyles<
    ElementProps<typeof TableCell>,
    {},
    { root: string }
  >(() => ({
    root: {
      padding: 0,
    },
  }))(CompactTableCell);

  const ActionsTableCell = withStyles<
    ElementProps<typeof TableCell>,
    {},
    { root: string }
  >(() => ({
    root: {
      paddingRight: 0,
    },
  }))(CompactTableCell);

  const listener =
    (num: number, rowRef: ElementRef<typeof TableRow>) =>
    ({ detail: { number: tappedNum } }: { detail: { number: number } }) => {
      if (tappedNum === num && isCompactView && rowRef.current) {
        rowRef.current.scrollIntoView({
          behavior: "smooth",
        });
      }
    };

  const LocationTableRow = ({
    location,
    number,
  }: {
    location: Location,
    number: number,
  }) => {
    const rowRef = React.createRef<typeof TableRow>();
    const l = listener(number, rowRef);
    const mark = { location, number, point: { left: 0, top: 0 } }; // point is unused, but necessary for type

    React.useEffect(() => {
      const tb = parentRef.current;
      tb.addEventListener(LOCATION_TAPPED_EVENT, l);
      return () => tb.removeEventListener(LOCATION_TAPPED_EVENT, l);
    });

    return (
      <TableRow key={number} ref={rowRef} onClick={() => onClick(mark)}>
        <NumberedLocationTableCell>
          <NumberedLocation
            number={number}
            inline
            selected={Boolean(selected) && selected === number}
          />
        </NumberedLocationTableCell>
        <CompactTableCell>
          {location.name && location.content instanceof Result && (
            <InfoBadge inline record={location.content}>
              <InfoCard record={location.content} />
            </InfoBadge>
          )}
          {location.name ?? <span style={{ paddingLeft: 20 }}>&mdash;</span>}
        </CompactTableCell>
        <ActionsTableCell>
          <ButtonGroup color="primary" size="small">
            {!location.hasContent && (
              <Button onClick={preventEventBubbling(() => onRemove(mark))}>
                Remove
              </Button>
            )}
          </ButtonGroup>
        </ActionsTableCell>
      </TableRow>
    );
  };

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            <CompactTableCell>Location</CompactTableCell>
            <CompactTableCell>Content</CompactTableCell>
            <MinColumnWidthHeadCell align="right">
              Actions
            </MinColumnWidthHeadCell>
          </TableRow>
        </TableHead>
        <TableBody ref={tableBody}>
          {locations.map((location, index) => (
            <LocationTableRow
              location={location}
              number={index + 1}
              key={index}
            />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default (observer(LocationsTable): ComponentType<LocationsTableArgs>);
