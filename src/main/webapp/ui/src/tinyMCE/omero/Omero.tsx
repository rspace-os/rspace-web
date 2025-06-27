import React, { useState, useEffect, useMemo, useRef } from "react";
import {
  getOmeroData,
  getImages,
  getWells,
  getDatasets,
  getPlates,
  getAnnotations,
  getImage,
  getPlateAcquisitions,
} from "./OmeroClient";
import Grid from "@mui/material/Grid";
import { FormControlLabel, Stack } from "@mui/material";
import CircularProgress from "@mui/material/CircularProgress";
import materialTheme from "../../theme";
import { ErrorReason, type ErrorReasonType, Order } from "./Enums";
import ErrorView from "./ErrorView";
import ResultsTable from "./ResultsTable";
import useLocalStorage from "../../util/useLocalStorage";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { makeStyles } from "tss-react/mui";
import { $PropertyExists, type OmeroItem, type OmeroArgs } from "./OmeroTypes";
import type { Cell } from "../../components/EnhancedTableHead";

const TABLE_HEADER_CELLS: Array<Cell<string>> = [
  { id: "path", numeric: false, label: "Path" },
  { id: "description", numeric: false, label: "Description" },
];

let SELECTED_ITEMS: Array<OmeroItem> = [];

const VISIBLE_HEADER_CELLS = TABLE_HEADER_CELLS;

export const getSelectedItems = (): Array<OmeroItem> => SELECTED_ITEMS;
export const getHeaders = (): typeof TABLE_HEADER_CELLS => VISIBLE_HEADER_CELLS;
const ORDER_KEY = "omeroSearchOrder";
const ORDER_BY_KEY = "omeroSearchOrderBy";
const DEFAULT_ORDER = Order.asc;
const DEFAULT_ORDERBY = "name";
export const getOrder = (): string =>
  (localStorage.getItem(ORDER_KEY) || DEFAULT_ORDER).replace(/['"]+/g, "");
export const getOrderBy = (): string =>
  (localStorage.getItem(ORDER_BY_KEY) || DEFAULT_ORDERBY).replace(/['"]+/g, "");
function Omero({ omero_web_url }: OmeroArgs): React.ReactNode {
  const [items, setItems] = useState<Array<OmeroItem>>([]);
  const [fetchDone, setFetchDone] = useState(false);
  const [errorReason, setErrorReason] = useState<ErrorReasonType>(
    ErrorReason.None
  );
  const [errorMessage, setErrorMessage] = useState("");
  const [selectedItemIds, setSelectedItemIds] = useState<Array<string>>([]);
  const [order, setOrder] = useLocalStorage(ORDER_KEY, DEFAULT_ORDER);
  const [orderBy, setOrderBy] = useLocalStorage<string>(
    ORDER_BY_KEY,
    DEFAULT_ORDERBY
  );
  const [dataTypeChoice, setDataTypeChoice] = useLocalStorage(
    "omeroDataTypeChoice",
    "Projects And Screens"
  );
  const [latestGridOfThumbnails, setLatestGridOfThumbnails] = useState<
    OmeroItem["imageGridDetails"] | null
  >(null);
  const [latestPlateAcquisition, setLatestPlateAcquisition] =
    useState<OmeroItem | null>(null);
  const [newItems, setNewItems] = useState<Array<OmeroItem>>([]);

  const scrollRef = useRef<HTMLDivElement | null>(null);
  const useStyles = makeStyles()(() => ({
    firstDescription: {
      fontStyle: "italic",
      fontWeight: "bold",
    },
    restOfDescription: {
      fontWeight: "lighter",
    },
    nameText: {
      fontWeight: "bold",
      fontSize: "16px",
    },
  }));
  const { classes } = useStyles();
  useEffect(() => {
    setItems([...items]);
  }, [latestGridOfThumbnails]);

  useEffect(() => {
    setItems([...newItems]);
  }, [newItems]);

  useEffect(() => {
    if (latestPlateAcquisition) {
      void addGridOfThumbnailsToItem(latestPlateAcquisition, 0);
    }
  }, [latestPlateAcquisition]);
  function handleRequestError(error: {
    message: string;
    response?: { status: number; data: string };
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

  const fetchOmeroData = async () => {
    setSelectedItemIds([]);
    setFetchDone(false);
    setItems([]);
    setOrder(DEFAULT_ORDER);
    setOrderBy(DEFAULT_ORDERBY);
    try {
      const projectsList: Array<OmeroItem> = await getOmeroData(dataTypeChoice);
      if (projectsList.length !== 0) {
        setItems(addDataToDisplayList(projectsList));
      }
      setFetchDone(true);
    } catch (error) {
      handleRequestError(
        error as {
          message: string;
          response?: { status: number; data: string };
        }
      );
    }
  };

  const hideChildren = (
    item: OmeroItem,
    onlyImageGridDetails: boolean = false
  ) => {
    showHideChildren(item, false, onlyImageGridDetails);
  };

  const onRowClick = (item_id: string) => {
    const selectedIndex = selectedItemIds.indexOf(item_id);
    let newSelected: Array<string> = [];

    if (selectedIndex === -1) {
      newSelected = newSelected.concat(selectedItemIds, item_id);
    } else if (selectedIndex === 0) {
      newSelected = newSelected.concat(selectedItemIds.slice(1));
    } else if (selectedIndex === selectedItemIds.length - 1) {
      newSelected = newSelected.concat(selectedItemIds.slice(0, -1));
    } else if (selectedIndex > 0) {
      newSelected = newSelected.concat(
        selectedItemIds.slice(0, selectedIndex),
        selectedItemIds.slice(selectedIndex + 1)
      );
    }

    setSelectedItemIds(newSelected);
  };

  const removeManyFromSelectedItems = (itemsToRemove: Array<string>) => {
    let newSelected = [...selectedItemIds];
    newSelected = newSelected.filter((item) => !itemsToRemove.includes(item));
    setSelectedItemIds(newSelected);
  };

  const addManyToSelectedItems = (itemsToAdd: Array<string>) => {
    const newSelected = [...selectedItemIds, ...itemsToAdd];
    setSelectedItemIds(newSelected);
  };

  const showHideChildren = (
    item: OmeroItem,
    show: boolean,
    onlyImageGridDetails: boolean = false
  ) => {
    if (onlyImageGridDetails) {
      item.gridShown = show;
    } else {
      item.showingChildren = show;
    }
    const itemsToToggleSelection: Array<string> = [];

    const toggleChildDisplay = (
      parent: OmeroItem,
      show: boolean,
      itemsToToggleSelection: Array<string>,
      directChildOfToggledElement: boolean = true
    ) => {
      const children = parent.children;
      children.map((child) => {
        if (directChildOfToggledElement) {
          child.hide = !show;
        } else {
          child.hideAsIndirectDescendant = !show;
        }
        if (!show && child.selected) {
          itemsToToggleSelection.push(child.type + "_" + child.id);
          child.selected = false;
          child.deselectedByHiding = true;
        }
        if (show && child.deselectedByHiding) {
          child.deselectedByHiding = false;
          child.selected = true;
          itemsToToggleSelection.push(child.type + "_" + child.id);
        }
        toggleChildDisplay(child, show, itemsToToggleSelection, false);
      });
    };
    if (onlyImageGridDetails) {
      if (!show) {
        if (!item.hiddenImageGridDetails) {
          item.hiddenImageGridDetails = [];
        }
        item.hiddenImageGridDetails[item.gridBeingShown] =
          item.imageGridDetails[item.gridBeingShown];
        item.imageGridDetails[item.gridBeingShown] = [];
        if (item.deselectedByHiding) {
          item.deselectedByHiding = false;
          item.selected = true;
          itemsToToggleSelection.push(item.type + "_" + item.id);
        }
      } else if (item.selected) {
        itemsToToggleSelection.push(item.type + "_" + item.id);
        item.selected = false;
        item.deselectedByHiding = true;
      }
    } else {
      toggleChildDisplay(item, show, itemsToToggleSelection);
    }
    const removeSelectedItems = onlyImageGridDetails ? show : !show;
    if (removeSelectedItems) {
      removeManyFromSelectedItems(itemsToToggleSelection);
    } else {
      addManyToSelectedItems(itemsToToggleSelection);
    }
    refreshItems();
  };

  const showChildren = (item: OmeroItem) => {
    showHideChildren(item, true);
  };

  const itemHasHiddenChildren = (item: OmeroItem) => {
    return items.some(
      (anItem) =>
        item.id === anItem.parentId &&
        (anItem.hide === true || anItem.hideAsIndirectDescendant === true)
    );
  };
  const addGridItemToDisplayedChildren = async (
    item: OmeroItem,
    gridItem: OmeroItem
  ) => {
    if (item.children.includes(gridItem)) {
      return;
    }
    let image;
    if (item.type === "plateAcquisition") {
      const wellsample = gridItem.children[0];
      image = wellsample.children[0];
    } else {
      image = gridItem;
    }

    image.fetched = true;
    item.showingChildren = true;
    item.children.unshift(gridItem);
    if (!item.addedChildren) {
      item.addedChildren = [];
    }
    item.addedChildren.unshift(gridItem);
    setItemDataOnChildren(item, item.children);
    const newItems = addChildrenToDisplayList(
      item.id,
      item.children,
      items,
      item.type
    );
    setNewItems([...newItems]);
    await addDetailsToItem(image);
    const observer = new MutationObserver((mutations, me) => {
      if (scrollRef.current) {
        const scrollTarget = scrollRef.current.querySelector(
          "#" + `${image.type}_name_display_${image.id}`
        );
        if (scrollTarget) {
          scrollTarget.scrollIntoView({
            block: "center",
            inline: "end",
            behavior: "smooth",
          });
          me.disconnect();
        }
      }
    });

    if (scrollRef.current) {
      observer.observe(scrollRef.current, {
        childList: true,
        subtree: true,
      });
    }
    await setNewItems([...newItems]);
  };

  const addimageDatailsToImage = async (image: OmeroItem) => {
    const imageData = await getImage(
      image.id,
      image.parentId,
      Boolean(image.fetchLarge)
    );

    image.imageData = makeListEntries(imageData.displayImageData, image);
    image.dataDescriptionArray = [];
    addDataToItemDataDescriptionArray(imageData.displayImageData, image);
    image.base64ThumbnailData = imageData.base64ThumbnailData;
    image.displayType = makeThumbNail(image, image.omeroConnectionKey);
  };
  const addDataToItemDataDescriptionArray = (
    datalist: Array<string>,
    item: OmeroItem
  ) => {
    if (!item.dataDescriptionArray) {
      if (item.description) {
        item.dataDescriptionArray = [item.description];
      } else {
        item.dataDescriptionArray = [];
      }
    }
    if (item.firstDescription?.indexOf("Publication Title") !== -1) {
      datalist = datalist.filter(
        (text) => text.indexOf("Publication Title") === -1
      );
    }
    item.dataDescriptionArray = item.dataDescriptionArray?.concat(datalist);
    item.descriptionElems = makeListEntries(
      $PropertyExists(item.dataDescriptionArray),
      item
    );
    item.firstDescription = $PropertyExists(item.dataDescriptionArray)[0];
  };
  const greyOutClickedThumbnails = (thumbnails: Array<OmeroItem>) => {
    thumbnails.map((thumbnail) => {
      if (thumbnail.imageSrc) {
        //thumbnails only gets an imageSrc when user clicks on grid of thumbnails, see below
        thumbnail.imageSrc.style.cssText =
          "opacity: 0.6; padding : 2px; border: 2px solid grey;";
      }
    });
  };

  /**
   * @param gridIndex - plate acquisitions can have many different 'fields' with one grid of thumbnails for each.
   * In contrast, the grid of thumbnails for a dataset will always be at gridIndex 0
   */
  const addGridOfThumbnailsToItem = async (
    item: OmeroItem,
    gridIndex: number = 0
  ) => {
    item.gridBeingShown = gridIndex;
    if (item.hiddenImageGridDetails && item.hiddenImageGridDetails[gridIndex]) {
      item.imageGridDetails[gridIndex] = item.hiddenImageGridDetails[gridIndex];
      showHideChildren(item, true, true);
      setLatestGridOfThumbnails(item.imageGridDetails);
    } else {
      showHideChildren(item, true, true);
      setFetchDone(false);
      let clickInProgress = false;
      item.gridShown = true;
      let images: Array<OmeroItem> = [];
      let rows = 0;
      let columns = 0;
      if (item.type === "plateAcquisition") {
        images = await getWells(item.id, item.parentId, false, gridIndex);
        rows = item.rows;
        columns = item.columns;
      } else if (item.type === "dataset") {
        images = await getImages(item.id, false);
        rows = Math.ceil(images.length / 10);
        columns = images.length < 10 ? images.length : 10;
      }
      if (!item.imageGridDetails) {
        item.imageGridDetails = [];
      }
      if (!item.imageGridDetails[gridIndex]) {
        item.imageGridDetails[gridIndex] = [];
      }
      item.gridOfImages = images;
      const gridOfImages = item.imageGridDetails[gridIndex];
      for (let rowpos: number = 0; rowpos < rows; rowpos++) {
        gridOfImages[rowpos] = [];
        for (let colpos = 0; colpos < columns; colpos++) {
          gridOfImages[rowpos][colpos] = makeEmptyThumbNail();
        }
      }
      images.map((dataValue, pos) => {
        let underlyingImage;
        let row: number, column: number;
        if (dataValue.type === "well") {
          underlyingImage = dataValue.children[0].children[0];
          row = dataValue.row;
          column = dataValue.column;
        } else {
          underlyingImage = dataValue;
          row = Math.floor(pos / 10);
          column = pos % 10;
        }
        const onClick = async (event: any): Promise<void> => {
          event.preventDefault();
          if (clickInProgress) {
            return;
          }
          clickInProgress = true;
          greyOutClickedThumbnails(images);
          underlyingImage.currentclicked = true;
          dataValue.imageSrc = event.target;
          gridOfImages[row][column] = makeThumbNail(
            underlyingImage,
            item.omeroConnectionKey
          );
          underlyingImage.currentclicked = false;
          await addGridItemToDisplayedChildren(item, dataValue);
          event.target.style.cssText =
            "opacity: 1; border: 4px solid blue;padding:0px";
          clickInProgress = false;
        };
        const thumbnail = makeThumbNail(
          underlyingImage,
          item.omeroConnectionKey,
          onClick
        );
        gridOfImages[row][column] = thumbnail;
      });
      setFetchDone(true);
      setLatestGridOfThumbnails(item.imageGridDetails);
    }
  };

  const addDetailsToItem = async (item: OmeroItem) => {
    if (item.fetchNew || !item.annotations || item.annotations.length === 0) {
      setFetchDone(false);
      if (item.type === "image") {
        await addimageDatailsToImage(item);
      }
      const annotations = await getAnnotations(item.id, item.type);
      item.annotations = makeListEntries(annotations, item);
      addDataToItemDataDescriptionArray(annotations, item);
      setFetchDone(true);
      refreshItems();
    }
  };

  const populateOmeroItemWithFetchedChildrenOrShowHiddenChildren = async (
    item: OmeroItem
  ) => {
    item.showingChildren = true;
    if (!item.fetchNew && itemHasHiddenChildren(item)) {
      showChildren(item);
    } else {
      item.fetchNew = false;
      setFetchDone(false);
      let children: Array<OmeroItem>;
      if (item.type === "dataset") {
        children = await getImages(item.id, item.fetchLarge === true);
      } else if (item.type === "project") {
        children = await getDatasets(item.id);
      } else if (item.type === "screen") {
        children = await getPlates(item.id);
      } else if (item.type === "plate") {
        children = await getPlateAcquisitions(item.id);
      } else {
        children = [];
      }
      item.children = children;
      const newItems = addChildrenToDisplayList(
        item.id,
        children,
        items,
        item.type
      );
      await setItems([...newItems]);
      setFetchDone(true);
      if (item.type === "plate" && children.length === 1) {
        setLatestPlateAcquisition(children[0]);
      }
    }
  };

  const setItemDataOnChildren = (
    item: OmeroItem,
    children: Array<OmeroItem>
  ) => {
    children.map((child) => {
      child.parentName = item.name;
      child.parentType = item.type;
      child.parentId = item.id;
      setItemDataOnChildren(item, child.children);
    });
  };

  const addChildrenToDisplayList = (
    parentID: number,
    children: Array<OmeroItem>,
    displayList: Array<OmeroItem>,
    type: string
  ) => {
    const newDisplayList = [...displayList];
    const parentItem = newDisplayList.filter(
      (item) => item.type === type && item.id === parentID
    )[0];
    const formattedItems = addDataToDisplayList(
      children,
      parentItem.paths,
      parentItem,
      false
    );
    newDisplayList.splice(
      newDisplayList.indexOf(parentItem) + 1,
      0,
      ...formattedItems
    );
    return newDisplayList;
  };

  const addDataToDisplayList = (
    items: Array<OmeroItem>,
    parentPaths: Array<string> = [],
    parent?: OmeroItem,
    shouldSort: boolean = true
  ): Array<OmeroItem> => {
    let displayData: Array<OmeroItem> = [];
    const omeroConnectionKey = items[0].omeroConnectionKey;
    formatDataForDisplay(
      items,
      parentPaths,
      omeroConnectionKey,
      parent,
      shouldSort
    ).map((item) => {
      displayData.push(item);
      if (item.children && item.children.length > 0) {
        displayData = displayData.concat(
          addDataToDisplayList(item.children, item.paths, item, shouldSort)
        );
      }
    });
    return displayData;
  };

  const formatDataForDisplay = (
    dataList: Array<OmeroItem>,
    thisparentPaths: Array<string>,
    omeroConnectionKey: string,
    parent?: OmeroItem,
    shouldSort: boolean = true
  ) => {
    if (shouldSort) {
      dataList.sort((data1, data2) => data1.name.localeCompare(data2.name));
    }
    return dataList.map((data) => {
      const thisPath = "/" + data.type + "/" + data.name + "/" + data.id;
      data.paths = [...thisparentPaths, thisPath];
      if (parent && !data.parentType) {
        data.parentType = parent.type;
        data.parentName = parent.name;
      }
      if (!data.annotations || data.annotations.length === 0) {
        data.path = makePathEntries(data.paths);
        addDataToItemDataDescriptionArray([], data);
      }
      if (data.displayImageData) {
        if (!data.imageData || data.imageData.length === 0) {
          data.imageData = makeListEntries(data.displayImageData, data);
          addDataToItemDataDescriptionArray(data.displayImageData, data);
        }
        data.displayType = makeThumbNail(data, omeroConnectionKey);
      }
      data.hide = false;
      return data;
    });
  };

  const makeListEntries = (
    annotations: Array<string>,
    item: OmeroItem
  ): Array<React.ReactElement> => {
    return annotations.map((annotation, index) => {
      annotation = annotation.replaceAll(
        "Screen Description",
        "Screen Description - "
      );
      annotation = annotation.replaceAll(
        "Experiment Description",
        "Experiment Description - "
      );
      annotation = annotation.replaceAll(
        "Study Description",
        "Study Description - "
      );
      let publicationTitle =
        annotation.indexOf("Screen Description") !== -1
          ? annotation.substring(0, annotation.indexOf("Screen Description"))
          : annotation.indexOf("Experiment Description") !== -1
          ? annotation.substring(
              0,
              annotation.indexOf("Experiment Description")
            )
          : annotation.indexOf("Study Description") !== -1
          ? annotation.substring(0, annotation.indexOf("Study Description"))
          : annotation;
      const originalText = publicationTitle;
      publicationTitle = publicationTitle.replaceAll("Publication Title", "");
      publicationTitle = publicationTitle.trim();
      publicationTitle = 'Publication Title: "' + publicationTitle + '"';
      const restOfText = annotation.substring(originalText.length);
      return annotation.indexOf("Publication Title") !== -1 ? (
        <>
          {" "}
          <dl>
            <dt
              id={`${item.type}_first_description_${item.id}`}
              className={classes.firstDescription}
            >
              {" "}
              {publicationTitle}
            </dt>
            <dt
              id={`${item.type}_rest_description_${item.id}`}
              className={classes.restOfDescription}
            >
              {" "}
              {restOfText}
            </dt>
          </dl>
        </>
      ) : (
        <li data-testid={`${item.type}_annotation_${item.id}_${index}`}>
          {annotation}
        </li>
      );
    });
  };

  const makePathEntries = (paths: Array<string>) => {
    return paths.map((path) => <dt key={path}>{path}</dt>);
  };
  const makeEmptyThumbNail = () => {
    return (
      <img
        title={"no image"}
        style={{ padding: "4px" }}
        src={"/images/White_square.png"}
      />
    );
  };
  const makeThumbNail = (
    data: OmeroItem,
    omeroConnectionKey: string,
    onClick?: (event: any) => Promise<void>
  ): React.ReactElement<"img"> => {
    const image = (
      <img
        id={`${data.type}_img_${data.id}`}
        data-testid={`${data.type}_img_${data.id}`}
        title={data.name}
        onClick={onClick}
        tabIndex={-1}
        style={
          data.currentclicked
            ? { padding: "2px", opacity: "0.6", border: "2px solid grey" }
            : { padding: "4px" }
        }
        src={
          data.base64ThumbnailData
            ? data.base64ThumbnailData
            : "/public/publicView/apps/omero/thumbnail/" +
              omeroConnectionKey +
              "/" +
              data.id
        }
      />
    );
    return image;
  };
  //return true if item has a string property that includes 'term' - also recursively checks items object
  //properties for a match but ignores item's `children` field, as they are separately displayed as their own table rows
  const doesItemHaveMatchingValue = (item: any, term: string): boolean => {
    let doesHaveMatch = false;
    for (const [key, value] of Object.entries(item)) {
      if (value && typeof value === "object") {
        if (key !== "children") {
          if (doesItemHaveMatchingValue(value, term)) {
            doesHaveMatch = true;
            break;
          }
        }
      } else if (
        value &&
        typeof value === "string" &&
        value.toLowerCase().indexOf(term.toLowerCase()) !== -1
      ) {
        doesHaveMatch = true;
        break;
      }
    }
    return doesHaveMatch;
  };
  const refreshItems = () => {
    setItems([...items]);
  };
  //filters the data by a text term and also deselects any items that end up hidden
  const filterDataAndDeselectHidden = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") {
      const filterTerm = (event.target as HTMLInputElement).value;
      const newData = [...items];
      const itemsToUnSelect: Array<string> = [];
      newData.map((item) => {
        item.hide = !doesItemHaveMatchingValue(item, filterTerm);
        if (item.hide) {
          itemsToUnSelect.push(item.type + "_" + item.id);
        }
      });
      removeManyFromSelectedItems(itemsToUnSelect);
      setItems(newData);
    }
  };

  useEffect(() => {
    void fetchOmeroData();
  }, [dataTypeChoice]);

  SELECTED_ITEMS = useMemo(() => {
    const selected_items = items.filter((item) =>
      selectedItemIds.includes(item.type + "_" + item.id)
    );

    window.parent.postMessage(
      {
        mceAction: selected_items.length > 0 ? "enable" : "disable",
      },
      "*"
    );

    return selected_items;
  }, [selectedItemIds]);

  if (errorReason !== ErrorReason.None) {
    return <ErrorView errorReason={errorReason} errorMessage={errorMessage} />;
  }
  const handleDataTypeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const datatype = e.target.value;
    setDataTypeChoice(datatype);
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <RadioGroup
          row
          aria-label="Display All Data, Only Project Data or only Screen Data"
          name="data type choice"
          defaultValue={dataTypeChoice}
          onChange={handleDataTypeChange}
        >
          <FormControlLabel
            value="Projects And Screens"
            control={<Radio color="primary" />}
            label="Projects And Screens"
          />
          <FormControlLabel
            value="Projects"
            control={<Radio color="primary" />}
            label="Projects"
          />
          <FormControlLabel
            value="Screens"
            control={<Radio color="primary" />}
            label="Screens"
          />
        </RadioGroup>
        <label htmlFor="omeroFilter">Filter results</label>
        <input
          id="omeroFilterID"
          type="text"
          name="omeroFilter"
          onKeyPress={filterDataAndDeselectHidden}
        />
        <Grid container spacing={1}>
          <Grid item xs={12}>
            <ResultsTable
              populateOmeroItemWithFetchedChildrenOrShowHiddenChildren={
                populateOmeroItemWithFetchedChildrenOrShowHiddenChildren
              }
              hideChildren={hideChildren}
              onRowClick={onRowClick}
              omero_web_url={omero_web_url}
              visibleHeaderCells={VISIBLE_HEADER_CELLS}
              results={items}
              selectedItemIds={selectedItemIds}
              setSelectedItemIds={setSelectedItemIds}
              order={order}
              orderBy={orderBy}
              setOrder={(orderValue) =>
                setOrder(orderValue as typeof DEFAULT_ORDER)
              }
              setOrderBy={setOrderBy}
              refreshItems={refreshItems}
              addDetailsToItem={addDetailsToItem}
              addGridOfThumbnailsToItem={addGridOfThumbnailsToItem}
              // @ts-ignore - The component uses forwardRef but TypeScript isn't recognizing it
              ref={scrollRef}
            />
          </Grid>
          <Grid item xs={12} sx={{ textAlign: "center" }}>
            {!fetchDone && (
              <Stack
                alignItems="center"
                sx={{
                  m: 1,
                  position: "fixed",
                  top: "50%",
                  left: "50%",
                  /* bring your own prefixes */
                  transform: "translate(-50% , -50%)",
                }}
              >
                <CircularProgress color="primary" />
                <Typography
                  sx={{
                    display: "inline-block",
                    color: "primary.main",
                    p: 1,
                  }}
                >
                  Data is loading...
                </Typography>
              </Stack>
            )}
          </Grid>
        </Grid>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export default Omero;
