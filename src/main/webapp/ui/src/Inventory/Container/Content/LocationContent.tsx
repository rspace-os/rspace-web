import React from "react";
import Box from "@mui/material/Box";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import Avatar from "@mui/material/Avatar";
import InfoBadge from "../../components/InfoBadge";
import InfoCard from "../../components/InfoCard";
import { useTheme } from "@mui/material/styles";
import { observer, Observer } from "mobx-react-lite";
import {
  type Location,
  type Container,
} from "../../../stores/definitions/Container";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import * as DragAndDrop from "./DragAndDrop";
import SearchContext from "../../../stores/contexts/Search";

const border = (color: string, isImportant: boolean = false) =>
  `3px solid ${color}${isImportant ? " !important" : ""}`;

// Shared styling for the placeholder number shown in empty GRID/IMAGE locations.
const numberBoxSx = {
  fontSize: "12px",
  backgroundColor: "white",
  width: "100%",
  height: "100%",
  textAlign: "center",
} as const;

type LocationContentArgs = {
  location: Location;
  container: Container;
  tabIndex?: number;
  hasFocus: boolean;
};

type ActualLocationContentProps = {
  content: InventoryRecord & {
    image?: string | null;
    thumbnail?: string | null;
  };
  location: Location;
};

function ActualLocationContent({
  content,
  location,
}: ActualLocationContentProps): React.ReactNode {
  return (
    <Observer>
      {() => (
        <>
          <Avatar
            variant="rounded"
            src={content.thumbnail || undefined}
            onMouseMove={(e: React.MouseEvent) => e.preventDefault()}
            sx={{
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
            }}
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

/**
 * Component that renders the content of a location
 */
function LocationContent({
  location,
  container,
  tabIndex,
  hasFocus,
}: LocationContentArgs): React.ReactNode {
  const theme = useTheme();
  const { search } = React.useContext(SearchContext);

  return (
    <Box
      sx={
        [
          {
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
          location.parentContainer.cType === "GRID"
            ? { border: border("white"), color: "grey" }
            : location.parentContainer.cType === "IMAGE"
              ? { border: border("grey") }
              : undefined,
          location.isShallowUnselected(search)
            ? { border: border(theme.palette.action.disabled, true) }
            : location.isShallowSelected(search)
              ? location.isSelectable(search)
                ? { border: border(theme.palette.primary.light, true) }
                : { border: border(theme.palette.secondary.light, true) }
              : location.selected
                ? {
                    border: border(theme.palette.primary.main, true),
                    backgroundColor: theme.palette.primary.main,
                  }
                : undefined,
          location.isGreyedOut(search)
            ? {
                filter: "grayscale(1)",
                pointerEvents: "none",
                opacity: 0.6,
              }
            : undefined,
        ] as React.ComponentProps<typeof Box>["sx"]
      }
    >
      <Box sx={{ position: "relative", width: "100%", height: "100%" }}>
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
        ) : location.parentContainer.cType === "GRID" ? (
          <Box
            tabIndex={tabIndex}
            sx={{ ...numberBoxSx, paddingTop: "calc(50% - 10px)" }}
          >
            {(location.coordY - 1) *
              (location.parentContainer.gridLayout?.columnsNumber || 0) +
              location.coordX}
          </Box>
        ) : location.parentContainer.cType === "IMAGE" ? (
          (() => {
            if (!location.parentContainer.sortedLocations)
              throw new Error("Locations of container must be known.");
            const sortedLocations = location.parentContainer.sortedLocations;
            const imageIndex =
              sortedLocations.findIndex(
                (loc) =>
                  loc.coordX === location.coordX &&
                  loc.coordY === location.coordY,
              ) + 1;
            return <Box sx={numberBoxSx}>{imageIndex}</Box>;
          })()
        ) : (
          <></>
        )}
      </Box>
    </Box>
  );
}

export default observer(LocationContent);
