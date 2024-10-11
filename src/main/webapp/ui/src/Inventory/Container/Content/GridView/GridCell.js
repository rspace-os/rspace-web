//@flow

import React, {
  useRef,
  useEffect,
  type Node,
  type ComponentType,
  type ElementRef,
} from "react";
import { observer } from "mobx-react-lite";
import TableCell from "@mui/material/TableCell";
import useResizeObserver from "../../../components/ResizeObserver";
import TableContainer from "@mui/material/TableContainer";
import { type Location } from "../../../../stores/definitions/Container";
import { styled } from "@mui/material/styles";
import * as DragAndDrop from "../DragAndDrop";
import { runInAction } from "mobx";

const StyledCell = styled(
  React.forwardRef(
    ({ width: _width, hoverEffect: _hoverEffect, ...rest }, ref) => (
      <TableCell {...rest} ref={ref} />
    )
  )
)(({ theme, width, hoverEffect }) => ({
  color: "grey",
  borderBottom: "none",
  "&:hover, &:focus": { backgroundColor: theme.palette.grey[200] },
  "&:active": {},
  /*
   * This padding is chosen so that a 96-well plate can be shown, without
   * scrolling, on a typical desktop monitor.
   */
  padding: "3px !important",
  width,
  background: hoverEffect ? "rgba(0, 0, 0, 0.04)" : "unset",
}));

const calculateLocationGeometry = (
  tableCellRef: {| current: ?HTMLElement |},
  parentRef: {| current: ?HTMLElement |},
  location: Location
) => {
  const rect = tableCellRef.current?.getBoundingClientRect();
  const parentRect = parentRef.current?.getBoundingClientRect();
  if (rect && parentRect) {
    location.setPosition(
      rect.left - parentRect.left,
      rect.top - parentRect.top
    );
    location.setDimensions(rect.width, rect.height);
  }
};

type GridCellArgs = {|
  children: Node,

  /**
   * When the table cell is resized (typically because the viewport dimensions
   * have changed), this component will invoke the `setPosition` and
   * `setDimensions` methods on this `Location`
   */
  location: Location,
  /**
   * This reference to the parent TableContainer component is used to calculate
   * this new position and dimension.
   */
  parentRef: ElementRef<typeof TableContainer>,

  /**
   * CSS property of the rendered HTMPTableCellElement.
   *
   * To ensure every cell in the row has the same width, provide a value like
   * `calc(100% / ${X})` where `X` is the number cells in the row.
   */
  width: string,

  columnsUnderHover: Set<number>,
  columnIndex: number,
|};

function GridCell({
  location,
  children,
  parentRef,
  width,
  columnsUnderHover,
  columnIndex,
}: GridCellArgs): Node {
  const cellRef = useRef<?HTMLElement>(null);

  const resizeObserver = useRef(
    new ResizeObserver(() => {
      if (parentRef.current) {
        calculateLocationGeometry(cellRef, parentRef, location);
      }
    })
  );

  useEffect(() => {
    calculateLocationGeometry(cellRef, parentRef, location);
  }, [location]);

  useResizeObserver({
    callback: () => {
      if (cellRef.current) resizeObserver.current.observe(cellRef.current);
    },
    element: cellRef,
  });

  return (
    <StyledCell
      aria-selected={location.selected}
      ref={cellRef}
      align="center"
      onMouseDown={(e) => {
        e.preventDefault();
      }}
      width={width}
      onFocus={() => {
        location.toggleSelected(true);
      }}
      onMouseEnter={() => {
        runInAction(() => {
          columnsUnderHover.add(columnIndex);
        });
      }}
      onMouseLeave={() => {
        runInAction(() => {
          columnsUnderHover.delete(columnIndex);
        });
      }}
      hoverEffect={columnsUnderHover.has(columnIndex)}
    >
      <DragAndDrop.Dropzone location={location}>
        {children}
      </DragAndDrop.Dropzone>
    </StyledCell>
  );
}

export default (observer(GridCell): ComponentType<GridCellArgs>);
