import React, { type ComponentType, forwardRef } from "react";
import { FormControlLabel, TableContainer } from "@mui/material";
import Table from "@mui/material/Table";
import EnhancedTableHead, {
  type Cell,
} from "../../components/EnhancedTableHead";
import TableBody from "@mui/material/TableBody";
import { getSorting, stableSort } from "../../util/table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Checkbox from "@mui/material/Checkbox";
import { makeStyles } from "tss-react/mui";
import Typography from "@mui/material/Typography";
import { Order } from "./Enums";
import type { OmeroItem } from "./OmeroTypes";
import clsx from "clsx";

const useStyles = makeStyles()(() => ({
  tableContainer: {
    marginBottom: "40px",
  },
  img: {
    "&:hover": {
      border: "10px solid green",
      opactity: "1",
    },
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
  formControlLabel: {},
  tableCell: {
    "&.MuiTableCell-root": {
      padding: "5px 5px 10px 5px",
      wordWrap: "break-word",
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
  project: {
    backgroundColor: "#EFEFEF",
  },
  boldText: {
    fontWeight: "bold",
  },
  nameText: {
    fontWeight: "bold",
    fontSize: "16px",
  },
  screen: {
    backgroundColor: "#edfcfc",
  },
  dataset: {
    backgroundColor: "#F4F4F4",
  },
  plate: {
    backgroundColor: "#f5fcfc",
  },
  well: {
    backgroundColor: "#edebeb",
  },
  omeroImg: {
    backgroundColor: "#FCFCFC",
  },
  type: {
    color: "#37393b",
  },
}));

function getLinkToOmero(item: OmeroItem, omero_web_url: string): string {
  if (item.type !== "plateAcquisition") {
    return omero_web_url + "webclient/?show=" + item.type + "-" + item.id;
  }
  if (!item.fake) {
    //fake plate acquisitions use the plate ID
    return omero_web_url + "webclient/?show=acquisition-" + item.id;
  }
  return omero_web_url + "webclient/?show=plate-" + item.id;
}

/**
 * @param results
 * @returns the data sorted by parents and then by children such that children always succeed parents and
 * precede any other parent that sorting would make succeed their parent. For example, sorting by name,
 * the order will be parentA {name:'A'}, ChildOfA {name:'D'},ChildOfA {name:'Z'}, parentB: {name:'B'}, childOfB{name:'A'} etc
 * Since Omero data of different types can have the same ID, types must always be used in comparisons and it is assumed that projects -> datasets -> images,
 * whereas Screens -> Plates ->PlateAcquisitions -> images
 */
export const omeroSort = (
  results: Array<OmeroItem>,
  order: "asc" | "desc",
  orderBy: string
): Array<OmeroItem> => {
  const sorted = stableSort(results, getSorting(order, orderBy));
  const notTopParent: Array<OmeroItem> = [];
  const sortedByParentThenChild: Array<OmeroItem> = [];
  sorted.forEach((item) => {
    if (itemHasNoParent(item, sorted)) {
      sortedByParentThenChild.push(item);
    } else {
      notTopParent.push(item);
    }
  });
  insertChildrenAfterTheirParent(sortedByParentThenChild, notTopParent);
  return sortedByParentThenChild;
};
//1) Omero has orphan data and
//2) User can select children for insertion into doc without selecting parent
const itemHasNoParent = (
  item: OmeroItem,
  allItems: Array<OmeroItem>
): boolean => {
  if (item.type === "project" || item.type === "screen") {
    return true;
  }
  return !allItems.some(
    (potentialParent) =>
      potentialParent.name === item.parentName &&
      potentialParent.type === item.parentType
  );
};

const insertChildrenAfterTheirParent = (
  parents: Array<OmeroItem>,
  children: Array<OmeroItem>
): void => {
  if (!children || children.length === 0) {
    return;
  }
  //the order of the types is important - parents must be inserted before children!
  const types = ["dataset", "plate", "plateAcquisition", "image"];
  types.forEach((type) => {
    const parentsHavingChildren: Array<OmeroItem> = [];
    children.forEach((child) => {
      const parentOfThisChild = parents.filter(
        (aParent) =>
          aParent.id === child.parentId &&
          child.type === type &&
          aParent.type === child.parentType
      );
      if (parentOfThisChild && parentOfThisChild.length > 0) {
        if (
          !parentsHavingChildren.some(
            (testee) => testee.name === parentOfThisChild[0].name
          )
        ) {
          parentsHavingChildren.push(parentOfThisChild[0]);
        }
      }
    });

    parentsHavingChildren.forEach((parent) => {
      const matchingChildren = children.filter(
        (child) =>
          parent.id === child.parentId &&
          child.type === type &&
          parent.type === child.parentType
      );
      const insertPointForMatchingChildren = parents.findIndex((testee) => {
        return testee.id === parent.id && testee.type === parent.type;
      });
      parents.splice(
        insertPointForMatchingChildren + 1,
        0,
        ...matchingChildren
      );
    });
  });
};
type ResultsTableArgs = {
  populateOmeroItemWithFetchedChildrenOrShowHiddenChildren: (
    item: OmeroItem
  ) => Promise<void>;
  hideChildren: (item: OmeroItem, showGrid?: boolean) => void;
  onRowClick: (id: string) => void;
  omero_web_url: string;
  visibleHeaderCells: Array<Cell<string>>;
  results: Array<OmeroItem>;
  order: (typeof Order)[keyof typeof Order];
  setOrder: (order: (typeof Order)[keyof typeof Order]) => void;
  orderBy: string;
  setOrderBy: (orderBy: string) => void;
  selectedItemIds: Array<string>;
  setSelectedItemIds: (ids: Array<string>) => void;
  refreshItems: () => void;
  addDetailsToItem: (item: OmeroItem) => Promise<void>;
  addGridOfThumbnailsToItem: (item: OmeroItem, pos: number) => Promise<void>;
};
const ResultsTable: ComponentType<ResultsTableArgs> = forwardRef(
  (
    {
      populateOmeroItemWithFetchedChildrenOrShowHiddenChildren,
      hideChildren,
      onRowClick,
      omero_web_url,
      visibleHeaderCells,
      results,
      selectedItemIds,
      setSelectedItemIds,
      order,
      orderBy,
      setOrder,
      setOrderBy,
      refreshItems,
      addDetailsToItem,
      addGridOfThumbnailsToItem,
    },
    ref
  ) => {
    const { classes } = useStyles();

    function handleRequestSort(
      event: React.MouseEvent<HTMLSpanElement>,
      property: string
    ): void {
      const isDesc =
        orderBy === findOrderByTerm(property) && order === Order.desc;
      setOrder(isDesc ? Order.asc : Order.desc);
      const orderByValue = findOrderByTerm(property);
      setOrderBy(orderByValue);
    }

    const findOrderByTerm = (property: string): string =>
      property === "path" ? "name" : "firstDescription";

    const itemHasFetchableChildren = (item: OmeroItem): boolean => {
      return (
        item.type === "plate" ||
        (item.childCounts > 0 && !isDataSetOrPlateAcquistion(item)) ||
        (item.addedChildren && item.addedChildren.length > 0)
      );
    };
    const isDataSetOrPlateAcquistion = (item: OmeroItem): boolean =>
      item.type === "dataset" || item.type === "plateAcquisition";
    const getFetchText = (item: OmeroItem): string => {
      return item.showingChildren
        ? "hide children"
        : isDataSetOrPlateAcquistion(item)
        ? "show children"
        : item.type === "plate" && item.childCounts > 1
        ? "show plateAcquisitions "
        : item.type === "plate"
        ? " show grid of wells  "
        : item.type === "project"
        ? "show datasets"
        : item.type === "screen"
        ? "show plates"
        : "";
    };

    const toggleSelected = (item: OmeroItem): void => {
      item.selected = !item.selected;
    };

    const makeOnClick = (item: OmeroItem) => {
      const onClick = (): void => {
        toggleSelected(item);
        onRowClick(item.type + "_" + item.id);
      };
      return onClick;
    };

    return (
      <>
        <TableContainer className={classes.tableContainer} ref={ref as any}>
          <Table
            aria-label="item search results"
            sx={{ display: "table", overflowX: "auto" }}
          >
            <EnhancedTableHead
              headStyle={classes.tableHead}
              headCells={visibleHeaderCells}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
              selectAll={true}
              onSelectAllClick={(event) => {
                if (event.target.checked) {
                  const newSelected = results
                    .filter((item: OmeroItem) => !item.gridShown)
                    .map((item) => item.type + "_" + item.id);
                  return setSelectedItemIds(newSelected);
                }
                setSelectedItemIds([]);
              }}
              numSelected={selectedItemIds.length}
              rowCount={results.length}
            />
            <TableBody sx={{ width: "100%" }}>
              {omeroSort(results, order, orderBy)
                .filter((item) => !(item.hide || item.hideAsIndirectDescendant))
                .filter(
                  (item) =>
                    item.type !== "well sample" && (item.type as any) !== "well"
                )
                .map((item, index) => {
                  const isItemSelected =
                    selectedItemIds.indexOf(item.type + "_" + item.id) !== -1;
                  const labelId = `item-search-results-checkbox-${index}`;
                  return (
                    <TableRow
                      id={labelId}
                      className={
                        item.type === "project"
                          ? clsx([classes.tableRow, classes.project])
                          : item.type === "screen"
                          ? clsx([classes.tableRow, classes.screen])
                          : item.type === "plate"
                          ? clsx([classes.tableRow, classes.plate])
                          : item.type === "dataset"
                          ? clsx([classes.tableRow, classes.dataset])
                          : (item.type as any) === "well"
                          ? clsx([classes.tableRow, classes.well])
                          : item.type === "image"
                          ? clsx([classes.tableRow, classes.omeroImg])
                          : classes.tableRow
                      }
                      hover
                      tabIndex={-1}
                      role="checkbox"
                      aria-checked={isItemSelected}
                      selected={isItemSelected}
                      key={index}
                    >
                      <TableCell
                        sx={
                          item.type === "dataset" || item.type === "plate"
                            ? {
                                padding: "0px 0px 0px 28px",
                              }
                            : item.type === "project" || item.type === "screen"
                            ? {
                                padding: "0px 0px 0px 16px",
                              }
                            : item.type === "image" ||
                              (item.type as any) === "well"
                            ? {
                                padding: "0px 0px 0px 40px",
                              }
                            : {
                                padding: "0px 0px 0px 0px",
                              }
                        }
                      >
                        {!item.gridShown && (
                          <Checkbox
                            data-testid={`checkbox-${item.type}-${item.id}`}
                            color="primary"
                            checked={isItemSelected}
                            inputProps={{ "aria-labelledby": labelId }}
                            size="small"
                            onClick={makeOnClick(item)}
                          />
                        )}
                      </TableCell>
                      {visibleHeaderCells.map((cell, i) => (
                        <TableCell
                          className={classes.tableCell}
                          key={`${cell.id}${i}`}
                          data-testid={`${cell.id}${index}`}
                          sx={
                            cell.id === "description" && item.imageGridDetails
                              ? { whiteSpace: "nowrap" }
                              : cell.id === "description"
                              ? { align: "left" }
                              : undefined
                          }
                          width={cell.id === "path" ? "25%" : "75%"}
                          id={`${cell.id}_tablecell_${item.type}${item.id}`}
                        >
                          {cell.id === "description" &&
                          item.imageGridDetails ? (
                            <>
                              {item.imageGridDetails.map((wellList) =>
                                wellList.map((wells) => (
                                  <div
                                    key={
                                      // @ts-expect-error TS can't reason about this
                                      wells[0].props["data-testid"]
                                    }
                                  >
                                    {wells}
                                  </div>
                                ))
                              )}
                              <dt className={classes.nameText}>{item.paths}</dt>
                            </>
                          ) : cell.id === "path" ? (
                            <>
                              <dl>
                                <dt
                                  id={`${item.type}_name_display_${item.id}`}
                                  data-testid={`${item.type}_name_display_${item.id}`}
                                  className={classes.nameText}
                                >
                                  {item.name}
                                </dt>
                                {item.displayType ? (
                                  <dt className={classes.boldText}>
                                    {item.displayType}
                                  </dt>
                                ) : (
                                  <dt className={classes.boldText}>
                                    {item.type}
                                  </dt>
                                )}
                                <dt>
                                  {" "}
                                  {item.fetched ? (
                                    <dt
                                      id={`${item.type}_details_fetched_${item.id}`}
                                      className={classes.boldText}
                                    >
                                      details fetched
                                    </dt>
                                  ) : (
                                    <a
                                      id={`${item.type}_fetch_details_${item.id}`}
                                      data-testid={`${item.type}_fetch_details_${item.id}`}
                                      href={
                                        "#" +
                                        `${item.type}_name_display_${item.id}`
                                      }
                                      onClick={() => {
                                        void addDetailsToItem(item);
                                        item.fetched = true;
                                      }}
                                    >
                                      {item.type === "image" ? (
                                        <dt>re-draw image</dt>
                                      ) : (
                                        <dt>fetch details</dt>
                                      )}
                                    </a>
                                  )}
                                  {item.gridShown ? (
                                    <a
                                      id={`${item.type}_hide_grid_${item.id}`}
                                      data-testid={`${item.type}_hide_grid_${item.id}`}
                                      href={
                                        "#" +
                                        `${item.type}_name_display_${item.id}`
                                      }
                                      onClick={() => {
                                        hideChildren(item, true);
                                      }}
                                    >
                                      hide image grid{" "}
                                      {item.samplesUrls &&
                                      item.samplesUrls.length > 1
                                        ? " (there are other fields)"
                                        : ""}
                                    </a>
                                  ) : item.type === "plateAcquisition" ? (
                                    <>
                                      {item.samplesUrls?.map((url, pos) => (
                                        <React.Fragment key={pos}>
                                          <a
                                            href={
                                              "#" +
                                              `${item.type}_name_display_${item.id}`
                                            }
                                            onClick={() => {
                                              void addGridOfThumbnailsToItem(
                                                item,
                                                pos
                                              );
                                            }}
                                          >
                                            <dt
                                              id={`${item.type}_show_grid_${item.id}`}
                                              data-testid={`${item.type}_show_grid_${item.id}`}
                                              className={classes.boldText}
                                            >
                                              show grid of wells for field{" "}
                                              {pos + 1}
                                            </dt>
                                          </a>
                                        </React.Fragment>
                                      ))}
                                    </>
                                  ) : item.type === "dataset" ? (
                                    <>
                                      <a
                                        href={
                                          "#" +
                                          `${item.type}_name_display_${item.id}`
                                        }
                                        onClick={() => {
                                          void addGridOfThumbnailsToItem(
                                            item,
                                            0
                                          );
                                        }}
                                      >
                                        <dt
                                          id={`${item.type}_show_grid_${item.id}`}
                                          data-testid={`${item.type}_show_grid_${item.id}`}
                                          className={classes.boldText}
                                        >
                                          show image grid{" "}
                                          {item.childCounts !== 0
                                            ? " [" + item.childCounts + "] "
                                            : " [1]"}
                                        </dt>
                                      </a>
                                    </>
                                  ) : (
                                    <></>
                                  )}{" "}
                                  {item.type === "image" && (
                                    <FormControlLabel
                                      id={`${item.type}_change_thumbnail_label_${item.id}`}
                                      control={
                                        <Checkbox
                                          id={`${item.type}_change_thumbnail_${item.id}`}
                                          size="small"
                                          onClick={(e) => {
                                            e.preventDefault();
                                            item.fetchLarge = !item.fetchLarge;
                                            item.fetched = false;
                                            item.fetchNew = true;
                                            refreshItems();
                                          }}
                                          checked={Boolean(item.fetchLarge)}
                                          color="primary"
                                          inputProps={{
                                            "aria-label": "get large thumbnail",
                                          }}
                                        />
                                      }
                                      slotProps={{
                                        typography: {
                                          fontSize: "0.75em",
                                          fontWeight: "900",
                                        },
                                      }}
                                      label={`large thumbnail`}
                                    />
                                  )}
                                </dt>
                                <dt>
                                  <a
                                    id={`${item.type}_omero_link_${item.id}`}
                                    target="_blank"
                                    href={getLinkToOmero(item, omero_web_url)}
                                    rel="noreferrer"
                                  >
                                    see in omero
                                  </a>
                                </dt>
                                <dt
                                  id={`${item.type}_fetch_children_${item.id}`}
                                >
                                  {" "}
                                  {itemHasFetchableChildren(item) && (
                                    <a
                                      data-testid={`${item.type}_fetch_childrenLink_${item.id}`}
                                      href={
                                        "#" +
                                        `${item.type}_name_display_${item.id}`
                                      }
                                      onClick={() => {
                                        item.showingChildren
                                          ? hideChildren(item)
                                          : void populateOmeroItemWithFetchedChildrenOrShowHiddenChildren(
                                              item
                                            );
                                      }}
                                    >
                                      {getFetchText(item)}{" "}
                                      {isDataSetOrPlateAcquistion(item)
                                        ? "[" + item.addedChildren.length + "]"
                                        : item.childCounts !== 0
                                        ? "[" + item.childCounts + "]"
                                        : "[1]"}
                                    </a>
                                  )}
                                </dt>
                                {item.parentType && (
                                  <dt
                                    id={`${item.type}_link_parent_${item.id}`}
                                    data-testid={`${item.type}_link_parent_${item.id}`}
                                    className={classes.boldText}
                                  >
                                    {
                                      <a
                                        href={
                                          "#" +
                                          `${item.parentType}_name_display_${item.parentId}`
                                        }
                                      >
                                        {" -> parent_" + item.parentType}
                                      </a>
                                    }
                                  </dt>
                                )}
                              </dl>
                            </>
                          ) : (
                            <>
                              {item.descriptionElems}
                              <dt className={classes.nameText}>{item.paths}</dt>
                            </>
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  );
                })}
            </TableBody>
          </Table>
        </TableContainer>
        <div className={classes.tableFooterContainer}>
          <Typography component="span" variant="body2" color="textPrimary">
            Selected: {selectedItemIds.length}
          </Typography>
        </div>
      </>
    );
  }
);

ResultsTable.displayName = "ResultsTable";
export default ResultsTable;
