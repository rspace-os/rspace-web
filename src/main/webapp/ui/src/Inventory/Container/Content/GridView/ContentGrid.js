// @flow

import React, {
  useContext,
  useRef,
  type Node,
  type ComponentType,
} from "react";
import { observer, useLocalObservable } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import LocationContent from "../LocationContent";
import GridCell, { type GridCellArgs } from "./GridCell";
import SearchContext from "../../../../stores/contexts/Search";
import TableContainer from "@mui/material/TableContainer";
import Dragger from "../Dragger";
import Skeleton from "@mui/material/Skeleton";
import { preventEventBubbling } from "../../../../util/Util";
import ContainerModel from "../../../../stores/models/ContainerModel";
import { type Location } from "../../../../stores/definitions/Container";
import Snackbar from "@mui/material/Snackbar";
import * as DragAndDrop from "../DragAndDrop";
import { runInAction } from "mobx";

/**
 * This component is a performance improvement for the columnar hover effect.
 * If every GridCell were to check whether its columnIndex is in
 * columnsUnderHover then every GridCell would have to re-render whenever
 * columnsUnderHover changed. Instead, we only re-render the GridCells whose
 * columnIndex has either been added or removed from columnsUnderHover since
 * the last render, reducing the performance characteristics thusly:
 *
 * Before:
 *    timeToRender(GridCell) * numOfRows * numOfColumns
 *
 * After:
 *    (timeToRender(WrappedGridCell) * numOfRows * numOfColumns) +
 *    (timeToRender(GridCell) * numOfRows * 2)
 *
 * Given that GridCell is a lot more costly to render with all of its
 * drag-and-drop functionality, plus the LocationContent that is passed as its
 * children, this works out to reduce the amount of time a re-render takes and
 * results in the hover effect remaining closer to the user's cursor.
 * We have to use this wrapper component because ContentGrid itself defines
 * columnsUnderHover and thus doesn't re-render when it is mutated.
 */
const WrappedGridCell = observer(
  (props: $Diff<GridCellArgs, {| hoverEffect: boolean |}>) => {
    const hoverEffect = props.columnsUnderHover.has(props.columnIndex);
    return <GridCell {...props} hoverEffect={hoverEffect} />;
  }
);

const useStyles = makeStyles()((theme) => ({
  table: {
    webkitTouchCallout: "none",
    webkitUserSelect: "none",
    userSelect: "none",
    cursor: "crosshair",
    tableLayout: "fixed",

    /*
     * If there is more than enough space, grow the table to 100% of the
     * available horizontal space and expand the size of the cells accordingly.
     * If there is not enough space, do not constrain the size of the table to
     * 100% of the width, but instead provide a horizontal scrollbar. The
     * minimum size of the grid cells has been calibrated to ensure that a
     * 96-well plate can be shown without needing a horizontal scrollbar but
     * anything larger will, on a typically desktop viewport, require one. On
     * smaller viewports, most grid containers including 96-well plates require
     * a horizontal scrollbar. The extra 8px is so that any records in the
     * right-most column with info buttons don't overflow the dimensions of the
     * table and trigger the scrollbar.
     */
    minWidth: "calc(100% - 8px)",
    width: "unset",
    maxWidth: "calc(100% - 8px)",

    "& th": {
      maxWidth: 0,
    },
    "& .MuiTableCell-head": {
      borderBottom: "none",
    },
    "& *": {
      /*
       * Ordinarily messing with the focus styles would be an accessibility
       * violation but we apply our own styles to indicate that a cell is
       * selected, which is synchronised with the content being focussed.
       */
      outline: "none",
    },
    "& .MuiTableCell-root:focus": {
      backgroundColor: "unset",
    },
  },
  loadingCell: {
    padding: theme.spacing(0.5),
    borderBottom: "none",
  },
}));

const LoadedContent = observer(
  ({ container }: {| container: ContainerModel |}) => {
    const { search } = React.useContext(SearchContext);
    const columnsUnderHover = useLocalObservable(() => new Set<number>());

    /*
     * This coordinate specifies the roving tab-index.
     * When user tabs to the table, initially the first cell has focus.
     * They can then use the arrow keys, as implemented below, to move that
     * focus. If they then tab away from the table and back again, the last cell
     * they had focussed is remembered and returned to.
     * Note that this is implemeted with 1-based indexing, just like the data
     * model.
     */
    const [tabIndexCoord, setTabIndexCoord] = React.useState({ x: 1, y: 1 });

    /*
     * Whilst elements within the table have focus, this variable is set to the
     * tabIndexCoord. When nothing in the table has focus this is set to null.
     */
    const [focusCoord, setFocusCoord] = React.useState<null | {|
      x: number,
      y: number,
    |}>(null);

    /*
     * When using the arrow keys with shift held down, the region of selected
     * locations expands relative to the current focussed location and the
     * location that had focus when the shift key began being held down. This
     * state variables holds that later coordinate for the duration of the shift
     * key being held.
     */
    const [shiftOrigin, setShiftOrigin] = React.useState<null | {|
      x: number,
      y: number,
    |}>(null);

    /*
     * When the user taps anywhere inside the table, initially we want to do
     * nothing but record where they tapped. If after 500ms they have neither
     * moved the cursor nor releases the click then drag-and-drop should be
     * begin. If they release the cursor without moving it then just that one
     * tapped location should be selected. If they move the cursor without
     * releasing the tap then drag selection should instead start.
     */
    const [mouseDownPoint, setMouseDownPoint] = React.useState<{|
      event: MouseEvent,
      offset: {| left: number, top: number, right: number, bottom: number |},
      clickTimeout: TimeoutID,
    |} | null>(null);

    const tableRef = useRef<?HTMLElement>(null);
    React.useEffect(() => {
      if (tableRef.current?.contains(document.activeElement)) {
        setFocusCoord(tabIndexCoord);
      } else {
        setFocusCoord(null);
      }
    }, [document.activeElement]);

    const topLeftCellRef = useRef(null);
    const { classes } = useStyles();
    const { disabled } = useContext(SearchContext);

    const [keyboardTips, setKeyboardTips] = React.useState(false);

    /*
     * When DndKit's keyboard-driven drag-and-drop mode is active, we don't want
     * arrow keys to change the selection of the locations. This boolean variable
     * records whether we're in the keyboard-driven drag-and-drop mode (it is set
     * by "space" and unset by "enter", "return", and "escape"; the default keys)
     * and when set all other keys are prevented from doing anything.
     */
    const [inKeyboardDragAndDropMode, setInKeyboardDragAndDropMode] =
      React.useState(false);

    const findLocation = (
      col: { value: number },
      row: { value: number }
    ): Location => {
      const loc = container.findLocation(col.value, row.value);
      if (!loc) throw new Error("Could not find location");
      return loc;
    };

    return (
      <>
        <TableContainer
          onMouseUp={() => {
            if (mouseDownPoint) {
              clearTimeout(mouseDownPoint.clickTimeout);
              container.startSelection(
                mouseDownPoint.event,
                mouseDownPoint.offset
              );
              setMouseDownPoint(null);
            }
            container.stopSelection({
              selectionLimit: search.uiConfig.selectionLimit,
              onlyAllowSelectingEmptyLocations:
                search.uiConfig.onlyAllowSelectingEmptyLocations,
            });
          }}
          ref={tableRef}
        >
          <Table
            stickyHeader
            aria-label="Grid view of container contents"
            role="grid"
            aria-multiselectable="true"
            className={classes.table}
            padding="normal"
            size="small"
            onFocus={() => setKeyboardTips(true)}
            onBlur={() => setKeyboardTips(false)}
            onMouseDown={(e) => {
              if (e.currentTarget === tableRef.current) return; // ignore taps to scrollbar
              if (!disabled) {
                setMouseDownPoint({
                  event: { ...e },
                  offset: {
                    left: topLeftCellRef.current?.clientWidth ?? 0,
                    top: topLeftCellRef.current?.clientHeight ?? 0,
                    right: 0,
                    bottom: 0,
                  },
                  clickTimeout: setTimeout(() => {
                    // if after 300ms the click is still being held
                    // then cancel it because drag-and-drop is starting
                    setMouseDownPoint(null);
                  }, 300),
                });
              }
            }}
            onMouseMove={(e) => {
              if (!disabled) {
                if (mouseDownPoint) {
                  clearTimeout(mouseDownPoint.clickTimeout);
                  container.startSelection(
                    mouseDownPoint.event,
                    mouseDownPoint.offset
                  );
                  setMouseDownPoint(null);
                }
                container.moveSelection(e, {
                  left: topLeftCellRef.current?.clientWidth ?? 0,
                  top: topLeftCellRef.current?.clientHeight ?? 0,
                  right: 0,
                  bottom: 0,
                });
              }
            }}
            onKeyDown={(e) => {
              if (e.key === " ") {
                setInKeyboardDragAndDropMode(true);
                return;
              }
              if (
                inKeyboardDragAndDropMode &&
                (e.key === "Enter" || e.key === "Return" || e.key === "Escape")
              ) {
                setInKeyboardDragAndDropMode(false);
                return;
              }
              if (inKeyboardDragAndDropMode) return;

              if (!focusCoord)
                throw new Error(
                  "A cell must have focus for key events to be handled"
                );
              if (e.key === "Escape") {
                container.toggleAllLocations(false);
                container
                  .findLocation(focusCoord.x, focusCoord.y)
                  ?.toggleSelected(true);
                e.preventDefault();
              }

              const newCoord: {
                [string]: ({ x: number, y: number }) => {
                  x: number,
                  y: number,
                },
              } = {
                ArrowRight: ({ x, y }) => ({
                  x: Math.min(x + 1, container.columns.length),
                  y,
                }),
                ArrowLeft: ({ x, y }) => ({
                  x: Math.max(x - 1, 0),
                  y,
                }),
                ArrowDown: ({ x, y }) => ({
                  x,
                  y: Math.min(y + 1, container.rows.length),
                }),
                ArrowUp: ({ x, y }) => ({
                  x,
                  y: Math.max(y - 1, 0),
                }),
              };
              if (!(e.key in newCoord)) return;
              e.preventDefault();
              const { x, y } = newCoord[e.key](focusCoord);

              const origin = e.shiftKey ? shiftOrigin ?? focusCoord : { x, y };
              const left = Math.min(x, origin.x);
              const right = Math.max(x, origin.x);
              const top = Math.min(y, origin.y);
              const bottom = Math.max(y, origin.y);

              container.locations?.forEach((l) => {
                l.toggleSelected(
                  l.coordX >= left &&
                    l.coordX <= right &&
                    l.coordY >= top &&
                    l.coordY <= bottom
                );
              });

              setShiftOrigin(e.shiftKey ? shiftOrigin ?? focusCoord : null);
              setFocusCoord({ x, y });
              setTabIndexCoord({ x, y });
            }}
          >
            <TableHead>
              <TableRow>
                <TableCell
                  align="center"
                  ref={topLeftCellRef}
                  onMouseDown={preventEventBubbling()}
                ></TableCell>
                {container.columns.map((column, columnIndex) => (
                  <TableCell
                    key={column.label}
                    align="center"
                    onMouseDown={preventEventBubbling()}
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
                  >
                    {column.label}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {container.rows.map((row) => (
                <TableRow hover tabIndex={-1} key={`row-${row.value}`}>
                  <TableCell
                    variant="head"
                    align="center"
                    onMouseDown={preventEventBubbling()}
                    role="rowheader"
                  >
                    {row.label}
                  </TableCell>
                  {container.initializedLocations &&
                    container.columns.map((col, index) => (
                      <WrappedGridCell
                        width={`calc(100% / ${container.columns.length})`}
                        key={`row-${row.value}-col-${col.value}`}
                        location={findLocation(col, row)}
                        parentRef={tableRef}
                        columnsUnderHover={columnsUnderHover}
                        columnIndex={index}
                      >
                        <LocationContent
                          location={findLocation(col, row)}
                          container={container}
                          tabIndex={
                            tabIndexCoord.x === col.value &&
                            tabIndexCoord.y === row.value
                              ? 0
                              : -1
                          }
                          hasFocus={
                            focusCoord?.x === col.value &&
                            focusCoord?.y === row.value
                          }
                        />
                      </WrappedGridCell>
                    ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Dragger container={container} parentRef={tableRef} />
        </TableContainer>
        <Snackbar
          open={keyboardTips}
          message={
            inKeyboardDragAndDropMode
              ? "Press Enter to drop items. Press Escape to cancel."
              : "Expand selection by holding Shift. Press Space to enter drag-and-drop mode. Press Escape to clear selection."
          }
        />
      </>
    );
  }
);

function ContentGrid(): Node {
  const { scopedResult } = useContext(SearchContext);
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  const { classes } = useStyles();

  return (
    <>
      {(!container.initializedLocations || container.loading) && (
        <TableContainer>
          <Table
            stickyHeader
            role="grid"
            className={classes.table}
            padding="normal"
            size="small"
          >
            <TableHead>
              <TableRow>
                <TableCell align="center"></TableCell>
                {container.columns.map((column) => (
                  <TableCell key={column.label} align="center">
                    {column.label}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {container.rows.map((row) => (
                <TableRow key={`row-${row.value}`}>
                  <TableCell variant="head" align="center">
                    {row.label}
                  </TableCell>
                  {container.columns.map((col) => (
                    <TableCell
                      key={`row-${row.value}-col-${col.value}`}
                      className={classes.loadingCell}
                    >
                      <Skeleton
                        variant="rectangular"
                        /*
                         * These dimensions are chosen to copy the size the
                         * content of the cells once the data has loaded.
                         */
                        width={30}
                        height={30}
                        sx={{ mx: "auto" }}
                      />
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {container.initializedLocations && !container.loading && (
        <DragAndDrop.Context
          container={container}
          supportKeyboard
          supportMultiple
        >
          <LoadedContent container={container} />
        </DragAndDrop.Context>
      )}
    </>
  );
}

export default (observer(ContentGrid): ComponentType<{||}>);
