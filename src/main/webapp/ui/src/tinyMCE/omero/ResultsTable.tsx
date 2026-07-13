import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell, { tableCellClasses } from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableRow, { tableRowClasses } from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import React, { forwardRef } from "react";
import { useTranslation } from "react-i18next";
import EnhancedTableHead, { type Cell } from "../../components/EnhancedTableHead";
import { getSorting } from "../../util/table";
import { Order } from "./Enums";
import type { OmeroDataTypes, OmeroItem } from "./OmeroTypes";

function getLinkToOmero(item: OmeroItem, omero_web_url: string): string {
  if (item.type !== "plateAcquisition") {
    return `${omero_web_url}webclient/?show=${item.type}-${item.id}`;
  }
  if (!item.fake) {
    //fake plate acquisitions use the plate ID
    return `${omero_web_url}webclient/?show=acquisition-${item.id}`;
  }
  return `${omero_web_url}webclient/?show=plate-${item.id}`;
}

// Row background colour by item type
const ROW_BACKGROUND_COLOR: Partial<Record<OmeroDataTypes, string>> = {
  project: "#EFEFEF",
  screen: "#edfcfc",
  plate: "#f5fcfc",
  dataset: "#F4F4F4",
  image: "#FCFCFC",
};

// Left indent (px) of the checkbox cell, nesting children under their parents
const CHECKBOX_CELL_INDENT: Partial<Record<OmeroDataTypes, number>> = {
  project: 16,
  screen: 16,
  dataset: 28,
  plate: 28,
  image: 40,
};

/**
 * @param results
 * @returns the data sorted by parents and then by children such that children always succeed parents and
 * precede any other parent that sorting would make succeed their parent. For example, sorting by name,
 * the order will be parentA {name:'A'}, ChildOfA {name:'D'},ChildOfA {name:'Z'}, parentB: {name:'B'}, childOfB{name:'A'} etc
 * Since Omero data of different types can have the same ID, types must always be used in comparisons and it is assumed that projects -> datasets -> images,
 * whereas Screens -> Plates ->PlateAcquisitions -> images
 */
export const omeroSort = (results: Array<OmeroItem>, order: "asc" | "desc", orderBy: string): Array<OmeroItem> => {
  const sorted = results.toSorted(getSorting(order, orderBy));
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
const itemHasNoParent = (item: OmeroItem, allItems: Array<OmeroItem>): boolean => {
  if (item.type === "project" || item.type === "screen") {
    return true;
  }
  return !allItems.some(
    (potentialParent) => potentialParent.name === item.parentName && potentialParent.type === item.parentType,
  );
};
const insertChildrenAfterTheirParent = (parents: Array<OmeroItem>, children: Array<OmeroItem>): void => {
  if (!children || children.length === 0) {
    return;
  }
  //the order of the types is important - parents must be inserted before children!
  const types = ["dataset", "plate", "plateAcquisition", "image"];
  types.forEach((type) => {
    const parentsHavingChildren: Array<OmeroItem> = [];
    children.forEach((child) => {
      const parentOfThisChild = parents.filter(
        (aParent) => aParent.id === child.parentId && child.type === type && aParent.type === child.parentType,
      );
      if (parentOfThisChild && parentOfThisChild.length > 0) {
        if (!parentsHavingChildren.some((testee) => testee.name === parentOfThisChild[0].name)) {
          parentsHavingChildren.push(parentOfThisChild[0]);
        }
      }
    });
    parentsHavingChildren.forEach((parent) => {
      const matchingChildren = children.filter(
        (child) => parent.id === child.parentId && child.type === type && parent.type === child.parentType,
      );
      const insertPointForMatchingChildren = parents.findIndex((testee) => {
        return testee.id === parent.id && testee.type === parent.type;
      });
      parents.splice(insertPointForMatchingChildren + 1, 0, ...matchingChildren);
    });
  });
};
type ResultsTableArgs = {
  populateOmeroItemWithFetchedChildrenOrShowHiddenChildren: (item: OmeroItem) => Promise<void>;
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
const ResultsTable = forwardRef<HTMLDivElement, ResultsTableArgs>(
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
    ref,
  ) => {
    const { t } = useTranslation("workspace");
    const renderWells = (
      wells:
        | OmeroItem["imageGridDetails"][number][number][number]
        | Array<OmeroItem["imageGridDetails"][number][number][number]>,
      rowIndex: number,
      columnIndex: number,
    ) => {
      const items = Array.isArray(wells) ? wells : [wells];
      return items.map((well, wellIndex) => {
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        const key: string =
          React.isValidElement(well) && well.key !== null
            ? well.key
            : // @ts-expect-error Fix this later
              React.isValidElement(well) && well.props["data-testid"]
              ? // @ts-expect-error Fix this later
                well.props["data-testid"]
              : `${rowIndex}-${columnIndex}-${wellIndex}`;
        return <React.Fragment key={key}>{well}</React.Fragment>;
      });
    };
    function handleRequestSort(_event: React.MouseEvent<HTMLSpanElement>, property: string): void {
      const isDesc = orderBy === findOrderByTerm(property) && order === Order.desc;
      setOrder(isDesc ? Order.asc : Order.desc);
      const orderByValue = findOrderByTerm(property);
      setOrderBy(orderByValue);
    }
    const findOrderByTerm = (property: string): string => (property === "path" ? "name" : "firstDescription");
    const itemHasFetchableChildren = (item: OmeroItem): boolean => {
      return (
        item.type === "plate" ||
        (item.childCounts > 0 && !isDataSetOrPlateAcquistion(item)) ||
        (item.addedChildren && item.addedChildren.length > 0)
      );
    };
    const isDataSetOrPlateAcquistion = (item: OmeroItem): boolean =>
      item.type === "dataset" || item.type === "plateAcquisition";
    const childCountFor = (item: OmeroItem): number =>
      isDataSetOrPlateAcquistion(item) ? item.addedChildren.length : item.childCounts !== 0 ? item.childCounts : 1;
    const getFetchText = (item: OmeroItem): string => {
      const count = childCountFor(item);
      if (item.showingChildren) return t("tinymce.omero.hideChildren", { count });
      if (isDataSetOrPlateAcquistion(item)) return t("tinymce.omero.showChildren", { count });
      if (item.type === "plate")
        return item.childCounts > 1
          ? t("tinymce.omero.showPlateAcquisitions", { count })
          : t("tinymce.omero.showGridOfWells", { count });
      if (item.type === "project") return t("tinymce.omero.showDatasets", { count });
      if (item.type === "screen") return t("tinymce.omero.showPlates", { count });
      return "";
    };
    const toggleSelected = (item: OmeroItem): void => {
      item.selected = !item.selected;
    };
    const makeOnClick = (item: OmeroItem) => {
      return (): void => {
        toggleSelected(item);
        onRowClick(`${item.type}_${item.id}`);
      };
    };
    return (
      <>
        <TableContainer sx={{ mb: "40px" }} ref={ref}>
          <Table
            aria-label={t("tinymce.omero.itemSearchResultsLabel")}
            sx={{
              display: "table",
              overflowX: "auto",
            }}
          >
            <EnhancedTableHead
              headSx={{ background: "#F6F6F6" }}
              headCells={visibleHeaderCells}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
              selectAll={true}
              onSelectAllClick={(event) => {
                if (event.target.checked) {
                  const newSelected = results
                    .filter((item: OmeroItem) => !item.gridShown)
                    .map((item) => `${item.type}_${item.id}`);
                  return setSelectedItemIds(newSelected);
                }
                setSelectedItemIds([]);
              }}
              numSelected={selectedItemIds.length}
              rowCount={results.length}
            />
            <TableBody
              sx={{
                width: "100%",
              }}
            >
              {omeroSort(results, order, orderBy)
                .filter((item) => !(item.hide || item.hideAsIndirectDescendant))
                .filter((item) => item.type !== "well sample" && item.type !== "well")
                .map((item, index) => {
                  const isItemSelected = selectedItemIds.indexOf(`${item.type}_${item.id}`) !== -1;
                  const labelId = `item-search-results-checkbox-${index}`;
                  const nameAnchorHref = `#${item.type}_name_display_${item.id}`;
                  return (
                    <TableRow
                      id={labelId}
                      sx={{
                        [`&.${tableRowClasses.selected}`]: { backgroundColor: "#e3f2fd" },
                        [`&.${tableRowClasses.selected}:hover`]: { backgroundColor: "#e3f2fd" },
                        backgroundColor: ROW_BACKGROUND_COLOR[item.type],
                      }}
                      hover
                      tabIndex={-1}
                      role="checkbox"
                      aria-checked={isItemSelected}
                      selected={isItemSelected}
                      key={index}
                    >
                      <TableCell
                        sx={{
                          padding: `0px 0px 0px ${CHECKBOX_CELL_INDENT[item.type] ?? 0}px`,
                        }}
                      >
                        {!item.gridShown && (
                          <Checkbox
                            data-testid={`checkbox-${item.type}-${item.id}`}
                            color="primary"
                            checked={isItemSelected}
                            size="small"
                            onClick={makeOnClick(item)}
                            slotProps={{
                              input: {
                                "aria-labelledby": labelId,
                              },
                            }}
                          />
                        )}
                      </TableCell>
                      {visibleHeaderCells.map((cell, i) => (
                        <TableCell
                          key={`${cell.id}${i}`}
                          data-testid={`${cell.id}${index}`}
                          sx={{
                            [`&.${tableCellClasses.root}`]: { padding: "5px 5px 10px 5px", wordWrap: "break-word" },
                            ...(cell.id === "description" && item.imageGridDetails
                              ? { whiteSpace: "nowrap" }
                              : cell.id === "description"
                                ? { align: "left" }
                                : undefined),
                          }}
                          width={cell.id === "path" ? "25%" : "75%"}
                          id={`${cell.id}_tablecell_${item.type}${item.id}`}
                        >
                          {cell.id === "description" && item.imageGridDetails ? (
                            <>
                              {item.imageGridDetails.map((wellList, rowIndex) =>
                                wellList.map((wells, columnIndex) => (
                                  <div key={`${rowIndex}-${columnIndex}`}>
                                    {renderWells(wells, rowIndex, columnIndex)}
                                  </div>
                                )),
                              )}
                              <Box sx={{ fontWeight: "bold", fontSize: "16px" }}>{item.paths}</Box>
                            </>
                          ) : cell.id === "path" ? (
                            <div>
                              <Box
                                id={`${item.type}_name_display_${item.id}`}
                                data-testid={`${item.type}_name_display_${item.id}`}
                                sx={{ fontWeight: "bold", fontSize: "16px" }}
                              >
                                {item.name}
                              </Box>
                              <Box sx={{ fontWeight: "bold" }}>{item.displayType ?? item.type}</Box>
                              <div>
                                {item.fetched ? (
                                  <Box
                                    component="span"
                                    id={`${item.type}_details_fetched_${item.id}`}
                                    sx={{ fontWeight: "bold" }}
                                  >
                                    {t("tinymce.omero.detailsFetched")}
                                  </Box>
                                ) : (
                                  <a
                                    id={`${item.type}_fetch_details_${item.id}`}
                                    data-testid={`${item.type}_fetch_details_${item.id}`}
                                    href={nameAnchorHref}
                                    onClick={() => {
                                      void addDetailsToItem(item);
                                      item.fetched = true;
                                    }}
                                  >
                                    {item.type === "image"
                                      ? t("tinymce.omero.redrawImage")
                                      : t("tinymce.omero.fetchDetails")}
                                  </a>
                                )}
                                {item.gridShown ? (
                                  <a
                                    id={`${item.type}_hide_grid_${item.id}`}
                                    data-testid={`${item.type}_hide_grid_${item.id}`}
                                    href={nameAnchorHref}
                                    onClick={() => {
                                      hideChildren(item, true);
                                    }}
                                  >
                                    {t("tinymce.omero.hideImageGrid", {
                                      hasOtherFields: item.samplesUrls && item.samplesUrls.length > 1 ? "yes" : "other",
                                    })}
                                  </a>
                                ) : item.type === "plateAcquisition" ? (
                                  <div>
                                    {item.samplesUrls?.map((_url, pos) => (
                                      <div key={pos}>
                                        <a
                                          href={nameAnchorHref}
                                          onClick={() => {
                                            void addGridOfThumbnailsToItem(item, pos);
                                          }}
                                        >
                                          <Box
                                            component="span"
                                            id={`${item.type}_show_grid_${item.id}`}
                                            data-testid={`${item.type}_show_grid_${item.id}`}
                                            sx={{ fontWeight: "bold" }}
                                          >
                                            {t("tinymce.omero.showGridOfWellsForField", { field: pos + 1 })}
                                          </Box>
                                        </a>
                                      </div>
                                    ))}
                                  </div>
                                ) : item.type === "dataset" ? (
                                  <div>
                                    <a
                                      href={nameAnchorHref}
                                      onClick={() => {
                                        void addGridOfThumbnailsToItem(item, 0);
                                      }}
                                    >
                                      <Box
                                        component="span"
                                        id={`${item.type}_show_grid_${item.id}`}
                                        data-testid={`${item.type}_show_grid_${item.id}`}
                                        sx={{ fontWeight: "bold" }}
                                      >
                                        {t("tinymce.omero.showImageGrid", {
                                          count: item.childCounts !== 0 ? item.childCounts : 1,
                                        })}
                                      </Box>
                                    </a>
                                  </div>
                                ) : null}{" "}
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
                                        slotProps={{
                                          input: {
                                            "aria-label": t("tinymce.omero.getLargeThumbnailLabel"),
                                          },
                                        }}
                                      />
                                    }
                                    slotProps={{
                                      typography: {
                                        sx: {
                                          fontSize: "0.75em",
                                          fontWeight: "900",
                                        },
                                      },
                                    }}
                                    label={t("tinymce.omero.largeThumbnail")}
                                  />
                                )}
                              </div>
                              <div>
                                <a
                                  id={`${item.type}_omero_link_${item.id}`}
                                  target="_blank"
                                  href={getLinkToOmero(item, omero_web_url)}
                                  rel="noreferrer"
                                >
                                  {t("tinymce.omero.seeInOmero")}
                                </a>
                              </div>
                              <div id={`${item.type}_fetch_children_${item.id}`}>
                                {itemHasFetchableChildren(item) && (
                                  <a
                                    data-testid={`${item.type}_fetch_childrenLink_${item.id}`}
                                    href={nameAnchorHref}
                                    onClick={() => {
                                      if (item.showingChildren) {
                                        hideChildren(item);
                                      } else {
                                        void populateOmeroItemWithFetchedChildrenOrShowHiddenChildren(item);
                                      }
                                    }}
                                  >
                                    {getFetchText(item)}
                                  </a>
                                )}
                              </div>
                              {item.parentType && (
                                <Box
                                  id={`${item.type}_link_parent_${item.id}`}
                                  data-testid={`${item.type}_link_parent_${item.id}`}
                                  sx={{ fontWeight: "bold" }}
                                >
                                  <a href={`#${item.parentType}_name_display_${item.parentId}`}>
                                    {t("tinymce.omero.parentLink", { parentType: item.parentType })}
                                  </a>
                                </Box>
                              )}
                            </div>
                          ) : (
                            <>
                              {item.descriptionElems}
                              <Box sx={{ fontWeight: "bold", fontSize: "16px" }}>{item.paths}</Box>
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
          <Typography component="span" variant="body2" color="textPrimary">
            {t("tinymce.omero.selectedCount", { count: selectedItemIds.length })}
          </Typography>
        </Box>
      </>
    );
  },
);
ResultsTable.displayName = "ResultsTable";
export default ResultsTable;
