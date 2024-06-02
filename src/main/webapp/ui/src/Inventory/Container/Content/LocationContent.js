//@flow

import React, { type Node, type ComponentType } from "react";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import Avatar from "@mui/material/Avatar";
import InfoBadge from "../../components/InfoBadge";
import InfoCard from "../../components/InfoCard";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer, Observer } from "mobx-react-lite";
import RelativeBox from "../../../components/RelativeBox";
import { globalStyles } from "../../../theme";
import {
  type Location,
  type Container,
} from "../../../stores/definitions/Container";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import * as DragAndDrop from "./DragAndDrop";

const border = (color: string, isImportant: boolean = false) =>
  `3px solid ${color}${isImportant ? " !important" : ""}`;

const useStyles = makeStyles()((theme) => ({
  wrapper: {
    margin: "0 auto",
    borderRadius: 3,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "white",
    transition: theme.transitions.filterToggle,
    aspectRatio: "1 / 1",

    // an arbitrary value the ensures cells don't become excessively large on enormous displays
    maxHeight: theme.spacing(10),

    // this ensures that empty location on visual containers are square
    minWidth: theme.spacing(3),

    // overrides the cascading `pointerEvents: none` of LocationWrapper
    pointerEvents: "auto",
  },
  selected: {
    border: border(theme.palette.primary.main, true),
    backgroundColor: theme.palette.primary.main,
  },
  shallowSelected: {
    border: border(theme.palette.primary.light, true),
  },
  shallowSelectedUnselectable: {
    border: border(theme.palette.secondary.light, true),
  },
  shallowUnselected: {
    border: border(theme.palette.action.disabled, true),
  },
  gridCell: {
    border: border("white"),
    color: "grey",
  },
  imageCell: {
    border: border("grey"),
  },
  emptyGridCellContentWrapper: {
    fontSize: "12px",
    backgroundColor: "white",
    width: "100%",
    height: "100%",
    textAlign: "center",
    paddingTop: "calc(50% - 10px)",
  },
  emptyImageCellContentWrapper: {
    fontSize: "12px",
    backgroundColor: "white",
    width: "100%",
    height: "100%",
    textAlign: "center",
  },
  locationContentBox: {
    width: "100%",
    height: "100%",
  },
}));

type Class = mixed;

const selectionStyle = (
  location: Location,
  classes: { [string]: string }
): Class => {
  if (location.isShallowUnselected) {
    return classes.shallowUnselected;
  }
  if (location.isShallowSelected) {
    return location.isSelectable
      ? classes.shallowSelected
      : classes.shallowSelectedUnselectable;
  }
  if (location.selected) {
    return classes.selected;
  }
  return "";
};

const wrapperClasses = (
  location: Location,
  classes: { [string]: string },
  globalClasses: { greyOut: string, ... }
): string => {
  const ret = new Set([classes.wrapper, selectionStyle(location, classes)]);
  if (location.parentContainer.cType === "GRID") ret.add(classes.gridCell);
  if (location.parentContainer.cType === "IMAGE") ret.add(classes.imageCell);
  if (location.isGreyedOut) ret.add(globalClasses.greyOut);
  return [...ret].join(" ");
};

type LocationContentArgs = {|
  location: Location,
  container: Container,
  tabIndex?: number,
  hasFocus: boolean,
|};

const ActualLocationContent = withStyles<
  {|
    content: InventoryRecord,
    location: Location,
  |},
  {
    avatar: string,
  }
>((theme, { content }) => ({
  avatar: {
    borderRadius: "2px",

    /*
     * The dimensions of the location content are determined by the parent
     * component. In the case of grid containers this is the TableCell which
     * will grow according to the space available and for visual containers
     * this is specified as an arbitrary value so as to balance the competing
     * need to identify details in the thumbnail whilst minimising how much the
     * locations image is obscured and other location markers are overlapped.
     */
    height: "unset",
    width: "unset",
    aspectRatio: "1 / 1",

    backgroundColor: content.image ? "rgba(0,0,0,0)" : "white",
    "& img": {
      WebkitUserDrag: "none",
      pointerEvents: "none",
    },
  },
}))(
  ({
    content,
    classes,
    location,
  }: {|
    content: InventoryRecord,
    classes: { avatar: string },
    location: Location,
  |}) => {
    return (
      <Observer>
        {() => (
          <>
            <Avatar
              className={classes.avatar}
              variant="rounded"
              src={content.thumbnail}
              onMouseMove={(e) => e.preventDefault()}
              style={{
                /*
                 * This is here rather than in the styles above because it needs
                 * to be inside the Observer so that when the user
                 * selects/deselect the location the sibling border
                 * appears/disappears
                 */
                border: `${
                  location.isSiblingSelected ? location.uniqueColor : "white"
                } 3px solid`,
              }}
            >
              {content.thumbnail ?? <RecordTypeIcon record={content} />}
            </Avatar>
            <InfoBadge record={content}>
              <InfoCard record={content} />
            </InfoBadge>
          </>
        )}
      </Observer>
    );
  }
);

const EmptyLocationContent = ({
  location,
  tabIndex,

  /*
   * We don't do anything special with focus here, we just let the browser do
   * its default thing with tabIndex
   */
  hasFocus: _hasFocus,
}: {
  location: Location,
  tabIndex?: number,
  hasFocus: boolean,
}) => {
  const { classes } = useStyles();
  if (location.parentContainer.cType === "GRID") {
    const gridIndex = () =>
      (location.coordY - 1) *
        (location.parentContainer.gridLayout?.columnsNumber || 0) +
      location.coordX;
    return (
      <div tabIndex={tabIndex} className={classes.emptyGridCellContentWrapper}>
        {gridIndex()}
      </div>
    );
  }
  if (location.parentContainer.cType === "IMAGE") {
    if (!location.parentContainer.sortedLocations)
      throw new Error("Locations of container must be known.");
    const sortedLocations = location.parentContainer.sortedLocations;
    const imageIndex = () =>
      sortedLocations.findIndex(
        (loc) =>
          loc.coordX === location.coordX && loc.coordY === location.coordY
      ) + 1;
    return (
      <div className={classes.emptyImageCellContentWrapper}>{imageIndex()}</div>
    );
  }
  return <></>;
};

function LocationContent({
  location,
  container,
  tabIndex,
  hasFocus,
}: LocationContentArgs): Node {
  const { classes } = useStyles();
  const { classes: globalClasses } = globalStyles();

  return (
    <div className={wrapperClasses(location, classes, globalClasses)}>
      <RelativeBox className={classes.locationContentBox}>
        {location.content ? (
          <DragAndDrop.Draggable
            container={container}
            content={location.content}
            location={location}
            tabIndex={tabIndex}
            hasFocus={hasFocus}
          >
            <ActualLocationContent
              location={location}
              content={location.content}
            />
          </DragAndDrop.Draggable>
        ) : (
          <EmptyLocationContent
            location={location}
            tabIndex={tabIndex}
            hasFocus={hasFocus}
          />
        )}
      </RelativeBox>
    </div>
  );
}

export default (observer(LocationContent): ComponentType<LocationContentArgs>);
