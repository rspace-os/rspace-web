import TableCell from "@mui/material/TableCell";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useEffect, useRef } from "react";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Location } from "../../../../stores/definitions/Container";
import useResizeObserver from "../../../components/ResizeObserver";
import * as DragAndDrop from "../DragAndDrop";

const calculateLocationGeometry = (
  tableCellRef: React.RefObject<HTMLTableCellElement>,
  parentRef: React.RefObject<HTMLElement>,
  location: Location,
) => {
  const rect = tableCellRef.current?.getBoundingClientRect();
  const parentRect = parentRef.current?.getBoundingClientRect();
  if (rect && parentRect) {
    location.setPosition(rect.left - parentRect.left, rect.top - parentRect.top);
    location.setDimensions(rect.width, rect.height);
  }
};

export type GridCellArgs = {
  children: ReactNode;

  /**
   * When the table cell is resized (typically because the viewport dimensions
   * have changed), this component will invoke the `setPosition` and
   * `setDimensions` methods on this `Location`
   */
  location: Location;
  /**
   * This reference to the parent TableContainer component is used to calculate
   * this new position and dimension.
   */
  parentRef: React.RefObject<HTMLElement>;

  /**
   * CSS property of the rendered HTMPTableCellElement.
   *
   * To ensure every cell in the row has the same width, provide a value like
   * `calc(100% / ${X})` where `X` is the number cells in the row.
   */
  width: string;

  columnsUnderHover: Set<number>;
  columnIndex: number;
  hoverEffect: boolean;
};

function GridCell({
  location,
  children,
  parentRef,
  width,
  columnsUnderHover,
  columnIndex,
  hoverEffect,
}: GridCellArgs): ReactNode {
  const cellRef = useRef<HTMLTableCellElement>(null);

  const resizeObserver = useRef(
    new ResizeObserver(() => {
      if (parentRef.current) {
        calculateLocationGeometry(cellRef, parentRef, location);
      }
    }),
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
    <TableCell
      aria-selected={location.selected}
      ref={cellRef}
      align="center"
      onMouseDown={(e: React.MouseEvent) => {
        e.preventDefault();
      }}
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
      sx={(theme) => ({
        color: "grey",
        borderBottom: "none",
        "&:hover, &:focus": { backgroundColor: theme.palette.grey[200] },
        padding: "3px !important",
        width,
        background: hoverEffect ? "rgba(0, 0, 0, 0.04)" : "unset",
      })}
    >
      <DragAndDrop.Dropzone location={location}>{children}</DragAndDrop.Dropzone>
    </TableCell>
  );
}

export default observer(GridCell);
