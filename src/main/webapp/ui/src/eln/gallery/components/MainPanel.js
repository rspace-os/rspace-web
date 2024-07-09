//@flow

import React, {
  type Node,
  Children,
  type ElementConfig,
  type ComponentType,
} from "react";
import DialogContent from "@mui/material/DialogContent";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Chip from "@mui/material/Chip";
import Fade from "@mui/material/Fade";
import {
  gallerySectionLabel,
  COLOR,
  SELECTED_OR_FOCUS_BORDER,
  SELECTED_OR_FOCUS_BLUE,
  type GallerySection
} from "../common";
import { styled } from "@mui/material/styles";
import useViewportDimensions from "../../../util/useViewportDimensions";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import Avatar from "@mui/material/Avatar";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import * as FetchingData from "../../../util/fetchingData";
import { type GalleryFile, type Id, idToString } from "../useGalleryListing";
import {
  useGalleryActions,
  folderDestination,
  rootDestination,
} from "../useGalleryActions";
import { useGallerySelection } from "../useGallerySelection";
import { doNotAwait, match } from "../../../util/Util";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import {
  useDroppable,
  useDraggable,
  useDndContext,
  DndContext,
  useSensor,
  MouseSensor,
  TouchSensor,
  KeyboardSensor,
} from "@dnd-kit/core";
import Button from "@mui/material/Button";
import GridIcon from "@mui/icons-material/ViewCompact";
import TreeIcon from "@mui/icons-material/AccountTree";
import Menu from "@mui/material/Menu";
import NewMenuItem from "./NewMenuItem";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Slide from "@mui/material/Slide";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import { useFileImportDropZone } from "../../../components/useFileImportDragAndDrop";
import ActionsMenu from "./ActionsMenu";
import RsSet from "../../../util/set";
import TreeView from "./TreeView";
import { COLORS as baseThemeColors } from "../../../theme";
import PlaceholderLabel from "./PlaceholderLabel";
import SwapVertIcon from "@mui/icons-material/SwapVert";
import HorizontalRuleIcon from "@mui/icons-material/HorizontalRule";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Link from "@mui/material/Link";
import { Link as ReactRouterLink } from "react-router-dom";

const BreadcrumbLink = ({
  folder,
  section,
  clearPath,
}: {|
  folder?: GalleryFile,
  section: string,
  clearPath: () => void,
|}) => {
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: `/${[
      section,
      ...(folder?.path.map(({ name }) => name) ?? []),
      folder?.name ?? "",
    ].join("/")}/`,
    disabled: false,
    data: {
      path: folder?.path ?? [],
      destination: folder ? folderDestination(folder) : rootDestination(),
    },
  });
  const dndContext = useDndContext();
  const dndInProgress = Boolean(dndContext.active);
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: SELECTED_OR_FOCUS_BORDER,
      }
    : dndInProgress
    ? {
        border: "2px solid white",
        borderWidth: "2px",
        animation: "drop 2s linear infinite",
      }
    : {
        border: "2px solid transparent",
      };
  return (
    <Link
      component={ReactRouterLink}
      to={""}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        (folder?.open ?? clearPath)();
      }}
      ref={(node) => {
        setDropRef(node);
      }}
      style={{
        ...dropStyle,
        borderRadius: "6px",
        paddingLeft: "1px",
        paddingRight: "1px",
        paddingTop: "1px",
        fontSize: "0.885rem",
      }}
    >
      {folder?.name ?? section}
    </Link>
  );
};

const Path = styled(({ className, section, path, clearPath }) => {
  const str = ArrayUtils.last(path)
    .map((folder) => folder.pathAsString())
    .orElse(`/${section}/`);
  const [hasFocus, setHasFocus] = React.useState(false);
  const textFieldRef = React.useRef();
  return (
    <>
      <TextField
        className={className}
        value={hasFocus ? str : ""}
        onChange={() => {}}
        fullWidth
        size="small"
        onFocus={() => {
          setHasFocus(true);
        }}
        onBlur={() => {
          setHasFocus(false);
        }}
        inputProps={{
          ref: textFieldRef,
        }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">Path</InputAdornment>
          ),
        }}
      />
      {/*
       * These two divs create a horizonally scrolling box without a scrollbar,
       * mimicing the standard behaviour of a text field.
       */}
      {!hasFocus && (
        <div
          style={{
            width: "calc(100% - 85px)",
            position: "absolute",
            top: "5px",
            right: "10px",
            overflow: "hidden",
          }}
        >
          <Stack
            onClick={() => {
              textFieldRef.current?.focus();
            }}
            direction="row"
            spacing={0.25}
            sx={{
              whiteSpace: "nowrap",
              overflowX: "auto",
              marginBottom: "-50px",
              paddingBottom: "50px",
              cursor: "text",
            }}
          >
            <BreadcrumbLink section={section} clearPath={clearPath} />
            {path.map((f) => (
              <>
                <span>›</span>
                <BreadcrumbLink
                  folder={f}
                  section={section}
                  clearPath={clearPath}
                />
              </>
            ))}
          </Stack>
        </div>
      )}
    </>
  );
})(() => ({
  "& input": {
    height: "21px",
  },
}));

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

const ImportDropzone = styled(
  //eslint-disable-next-line react/display-name
  React.forwardRef(
    (
      {
        className,
        folderId,
        path,
        refreshListing,
      }: {|
        className: string,
        folderId: FetchingData.Fetched<Id>,
        path: $ReadOnlyArray<GalleryFile>,
        refreshListing: () => void,
      |},
      ref
    ) => {
      const { uploadFiles } = useGalleryActions();
      const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
        useFileImportDropZone({
          onDrop: doNotAwait(async (files) => {
            const fId = FetchingData.getSuccessValue<Id>(folderId).orElseGet(
              () => {
                throw new Error("Unknown folder id");
              }
            );
            await uploadFiles(path, fId, files);
            refreshListing();
          }),
          stopPropagation: false,
        });

      return (
        <Card
          ref={ref}
          onDragOver={onDragOver}
          onDragEnter={onDragEnter}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          className={className}
          sx={
            over
              ? {
                  borderColor: `hsl(${baseThemeColors.primary.hue}deg, 100%, 37%)`,
                  backgroundColor: `hsl(${baseThemeColors.primary.hue}deg, 50%, 90%)`,
                  color: `hsl(${baseThemeColors.primary.hue}deg, 100%, 37%) !important`,
                }
              : {}
          }
        >
          <ListItem disablePadding>
            <ListItemIcon>
              <UploadFileIcon />
            </ListItemIcon>
            <ListItemText primary="Drop here to upload" />
          </ListItem>
        </Card>
      );
    }
  )
)(({ theme }) => ({
  position: "absolute",
  bottom: 0,
  height: "100px",
  left: 0,
  right: 0,
  borderRadius: 0,
  borderLeft: "none",
  borderRight: "none",
  borderBottom: "none",
  textAlign: "center",
  fontSize: "2em",
  verticalAlign: "middle",
  letterSpacing: "0.03em",
  color: theme.palette.primary.saturated,
  padding: "20px",
  "& .MuiListItem-root": {
    marginLeft: "auto",
    marginRight: "auto",
    width: "max-content",
  },
  "& .MuiListItemIcon-root": {
    color: "inherit",
  },
  "& .MuiSvgIcon-root": {
    width: "2em",
    height: "2em",
    color: "inherit",
  },
  "& .MuiListItemText-primary": {
    fontSize: "2rem",
    color: "inherit",
  },
}));

const Breadcrumb = ({
  label,
  onClick,
  path,
  selectedSection,
  folder,
}: {|
  label: string,
  onClick: () => void,
  path: $ReadOnlyArray<GalleryFile>,
  selectedSection: string,
  folder?: GalleryFile,
|}) => {
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: `/${[
      selectedSection,
      ...path.map(({ name }) => name),
      folder?.name ?? "",
    ].join("/")}/`,
    disabled: false,
    data: {
      path,
      destination: folder ? folderDestination(folder) : rootDestination(),
    },
  });
  const dndContext = useDndContext();
  const dndInProgress = Boolean(dndContext.active);
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: SELECTED_OR_FOCUS_BORDER,
      }
    : dndInProgress
    ? {
        border: "2px solid white",
        borderWidth: "2px",
        animation: "drop 2s linear infinite",
      }
    : {
        border: "2px solid transparent",
      };

  return (
    <Chip
      ref={(node) => {
        setDropRef(node);
      }}
      style={{
        ...dropStyle,
      }}
      size="small"
      clickable
      label={label}
      onClick={onClick}
      sx={{ mt: 0.5 }}
    />
  );
};

const CustomBreadcrumbs = ({
  children,
  ...props
}: ElementConfig<typeof Breadcrumbs>) => {
  const [open, setOpen] = React.useState(false);
  let contents = Children.toArray<typeof Breadcrumb | typeof Chip>(children);
  if (!open && contents.length > 2) {
    contents = [
      contents[0],
      <Chip
        size="small"
        clickable
        label="..."
        sx={{ mt: 0.5 }}
        key="..."
        onMouseEnter={() => {
          setOpen(true);
        }}
      />,
      contents[contents.length - 1],
    ];
  }
  return (
    <Breadcrumbs
      {...props}
      onMouseLeave={() => {
        setOpen(false);
      }}
    >
      {contents}
    </Breadcrumbs>
  );
};

const GridView = observer(
  ({
    listing,
  }: {|
    listing: | {| tag: "empty", reason: string |}
      | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |},
  |}) => {
    const dndContext = useDndContext();
    const selection = useGallerySelection();

    const viewportDimensions = useViewportDimensions();
    const cardWidth = {
      xs: 6,
      sm: 4,
      md: 3,
      lg: 2,
      xl: 2,
    };
    const cols = 12 / cardWidth[viewportDimensions.viewportSize];

    /*
     * This coordinate specifies the roving tab-index.
     * When user tabs to the grid, initially the first card has focus.  They
     * can then use the arrow keys, as implemented below, to move that focus.
     * If they then tab away from the table and back again, the last card they
     * had focussed is remembered and returned to.
     */
    const [tabIndexCoord, setTabIndexCoord] = React.useState({ x: 0, y: 0 });

    const focusFileCardRef = React.useRef(null);
    const [hasFocus, setHasFocus] = React.useState(false);
    React.useEffect(() => {
      if (hasFocus) focusFileCardRef.current?.focus();
    }, [tabIndexCoord]);

    /*
     * When using the arrow keys or clicking with shift held down, the region
     * of selected files expands relative to the current focussed file and the
     * file that had focus when the shift key began being held down. This state
     * variables holds that later coordinate for the duration of the shift key
     * being held.
     */
    const [shiftOrigin, setShiftOrigin] = React.useState<null | {|
      x: number,
      y: number,
    |}>(null);

    if (listing.tag === "empty")
      return (
        <div key={listing.reason}>
          <Fade
            in={true}
            timeout={
              window.matchMedia("(prefers-reduced-motion: reduce)").matches
                ? 0
                : 300
            }
          >
            <div>
              <PlaceholderLabel>{listing.reason}</PlaceholderLabel>
            </div>
          </Fade>
        </div>
      );
    return (
      <Grid
        container
        spacing={2}
        onKeyDown={(e) => {
          if (dndContext.active) return;
          if (e.key === "Escape") {
            selection.clear();
            return;
          }
          const newCoord: {
            [string]: ({ x: number, y: number }) => {
              x: number,
              y: number,
            },
          } = {
            ArrowRight: ({ x, y }) => ({
              x: Math.min(x + 1, cols),
              y,
            }),
            ArrowLeft: ({ x, y }) => ({
              x: Math.max(x - 1, 0),
              y,
            }),
            ArrowDown: ({ x, y }) => ({
              x,
              y: Math.min(y + 1, Math.floor(listing.list.length / cols)),
            }),
            ArrowUp: ({ x, y }) => ({
              x,
              y: Math.max(y - 1, 0),
            }),
          };
          if (!(e.key in newCoord)) return;
          e.preventDefault();
          const { x, y } = newCoord[e.key](tabIndexCoord);

          /*
           * This check prevents the user from moving passed the end of the
           * last row if it doesn't fill a complete row or down in a column
           * that doesn't have `listing.list.length / cols` rows
           */
          if (y * cols + (x + 1) > listing.list.length) return;

          const origin = e.shiftKey ? shiftOrigin ?? tabIndexCoord : { x, y };
          const left = Math.min(x, origin.x);
          const right = Math.max(x, origin.x);
          const top = Math.min(y, origin.y);
          const bottom = Math.max(y, origin.y);

          selection.clear();
          listing.list.forEach((file, i) => {
            const fileX = i % cols;
            const fileY = Math.floor(i / cols);
            if (
              fileX >= left &&
              fileX <= right &&
              fileY >= top &&
              fileY <= bottom
            )
              selection.append(file);
          });

          setShiftOrigin(e.shiftKey ? shiftOrigin ?? tabIndexCoord : null);
          setTabIndexCoord({ x, y });
        }}
        onFocus={() => {
          /*
           * This is so that when the grid first receives tab focus, and thus
           * no files have been selected, the file card with a tabIndex of 0 is
           * selected. This way, the selected styling acts as a focus ring.
           * Pressing the escape key will clear the selection so when the grid
           * regains focus this will then run again
           */
          const { x, y } = tabIndexCoord;
          if (selection.isEmpty) selection.append(listing.list[y * cols + x]);
        }}
      >
        {listing.list.map((file, index) => (
          <FileCard
            ref={
              index % cols === tabIndexCoord.x &&
              Math.floor(index / cols) === tabIndexCoord.y
                ? focusFileCardRef
                : null
            }
            onFocus={() => {
              setHasFocus(true);
            }}
            onBlur={() => {
              setHasFocus(false);
            }}
            selected={selection.includes(file)}
            file={file}
            key={idToString(file.id)}
            index={index}
            tabIndex={
              index % cols === tabIndexCoord.x &&
              Math.floor(index / cols) === tabIndexCoord.y
                ? 0
                : -1
            }
            onClick={(e) => {
              if (e.shiftKey) {
                if (!shiftOrigin) return;
                const tappedCoord = {
                  x: index % cols,
                  y: Math.floor(index / cols),
                };
                const toSelect = listing.list.filter((_file, i) => {
                  const coord = {
                    x: i % cols,
                    y: Math.floor(i / cols),
                  };
                  return (
                    coord.x >= Math.min(tappedCoord.x, shiftOrigin.x) &&
                    coord.x <= Math.max(tappedCoord.x, shiftOrigin.x) &&
                    coord.y >= Math.min(tappedCoord.y, shiftOrigin.y) &&
                    coord.y <= Math.max(tappedCoord.y, shiftOrigin.y)
                  );
                });
                selection.clear();
                toSelect.forEach((f) => {
                  selection.append(f);
                });
                setTabIndexCoord({
                  x: index % cols,
                  y: Math.floor(index / cols),
                });
              } else if (e.ctrlKey || e.metaKey) {
                if (selection.includes(file)) {
                  selection.remove(file);
                } else {
                  selection.append(file);
                }
              } else {
                selection.clear();
                selection.append(file);
                setShiftOrigin({
                  x: index % cols,
                  y: Math.floor(index / cols),
                });
                setTabIndexCoord({
                  x: index % cols,
                  y: Math.floor(index / cols),
                });
              }
            }}
          />
        ))}
      </Grid>
    );
  }
);

const FileCard = styled(
  //eslint-disable-next-line react/display-name
  React.forwardRef(
    (
      {
        file,
        className,
        selected,
        index,
        onClick,
        tabIndex,
        onFocus,
        onBlur,
      }: {|
        file: GalleryFile,
        className: string,
        selected: boolean,
        index: number,
        onClick: (Event) => void,
        tabIndex: number,
        onFocus: () => void,
        onBlur: () => void,
      |},
      ref
    ) => {
      const { uploadFiles } = useGalleryActions();
      const selection = useGallerySelection();
      const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
        useFileImportDropZone({
          onDrop: (files) => {
            void uploadFiles([...file.path, file], file.id, files);
            /*
             * No need to refresh the listing as the uploaded file has been
             * placed inside a folder into which the user cannot currently see
             */
          },
          disabled: !file.isFolder,
        });
      const { setNodeRef: setDropRef, isOver } = useDroppable({
        id: file.id,
        disabled: !file.isFolder,
        data: {
          path: file.path,
          destination: folderDestination(file),
        },
      });
      const {
        attributes,
        listeners,
        setNodeRef: setDragRef,
        transform,
      } = useDraggable({
        disabled: false,
        id: file.id,
        data: {
          /*
           * If this `file` is one of the selected files then all of the
           * selected files are to be moved by the drag operation. If it is not
           * included then just move this file.
           */
          filesBeingMoved: selection.includes(file)
            ? selection.asSet()
            : new RsSet([file]),
        },
      });
      /*
       * DndKit wants to set the role to "button" but if we do that then the
       * keyboard controls of MUI's SimpleTreeView stop working. Keeping the
       * correct role for tree items doesn't prevent DndKit from working.
       */
      delete attributes.role;

      const dndContext = useDndContext();
      const dndInProgress = Boolean(dndContext.active);

      const dragStyle: { [string]: string | number } = transform
        ? {
            transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.1)`,
            zIndex: 1, // just needs to be rendered above Nodes later in the DOM
            position: "relative",
            boxShadow: `hsl(${COLOR.main.hue}deg 66% 10% / 20%) 0px 2px 16px 8px`,
          }
        : {};
      const dropStyle: { [string]: string | number } = isOver
        ? {
            borderColor: SELECTED_OR_FOCUS_BLUE,
          }
        : dndInProgress && file.isFolder
        ? {
            border: "2px solid white",
            borderWidth: "2px",
            borderRadius: "8px",
            animation: "drop 2s linear infinite",
          }
        : {};
      const inGroupBeingDraggedStyle: { [string]: string | number } =
        (
          dndContext.active?.data.current?.filesBeingMoved ?? new RsSet()
        ).hasWithEq(file, (a, b) => a.id === b.id) &&
        dndContext.active?.id !== file.id
          ? {
              opacity: 0.2,
            }
          : {};
      const fileUploadDropping: { [string]: string | number } = over
        ? {
            borderColor: SELECTED_OR_FOCUS_BLUE,
          }
        : {};

      const viewportDimensions = useViewportDimensions();
      const cardWidth = {
        xs: 6,
        sm: 4,
        md: 3,
        lg: 2,
        xl: 2,
      };

      return (
        <Fade
          in={true}
          timeout={
            window.matchMedia("(prefers-reduced-motion: reduce)").matches
              ? 0
              : 400
          }
        >
          <Grid
            item
            {...cardWidth}
            sx={{
              /*
               * This way, the animation takes the same amount of time (36ms) for
               * each row of cards
               */
              transitionDelay: window.matchMedia(
                "(prefers-reduced-motion: reduce)"
              ).matches
                ? "0s"
                : `${
                    (index + 1) * cardWidth[viewportDimensions.viewportSize] * 3
                  }ms !important`,
            }}
          >
            <Card
              elevation={0}
              className={className}
              /*
               * These are for dragging files from outside the browser
               */
              onDrop={onDrop}
              onDragOver={onDragOver}
              onDragEnter={onDragEnter}
              onDragLeave={onDragLeave}
              /*
               * These are for dragging files between folders within the gallery
               */
              ref={(node) => {
                setDropRef(node);
                setDragRef(node);
                // $FlowExpectedError[prop-missing]
                if (ref) ref.current = node;
              }}
              {...listeners}
              {...attributes}
              tabIndex={tabIndex}
              onFocus={onFocus}
              onBlur={onBlur}
              style={{
                ...dragStyle,
                ...dropStyle,
                ...inGroupBeingDraggedStyle,
                ...fileUploadDropping,
                /*
                 * We don't need the outline as the selected styles will indicate
                 * which item has focus
                 */
                outline: "none",
              }}
              /*
               * We conditionally just add the onKeyDown when file has an
               * `open` action (which is to say it is a folder), leaving the
               * keyDown event to propagate up to the KeyboardSensor of the
               * drag-and-drop mechanism for all other files
               */
              {...(file.open
                ? {
                    onKeyDown: (e) => {
                      if (e.key === " ") file.open?.();
                    },
                  }
                : {})}
            >
              <CardActionArea
                role={file.open ? "button" : "checkbox"}
                aria-checked={selected}
                tabIndex={-1}
                onFocus={(e) => {
                  /*
                   * We're manually handling focus states through a roving tab
                   * index on the GridView component, so we don't need to
                   * handle focus state triggered by keyboard events here.
                   * Moreover, we don't want mouse events, such as clicking, to
                   * trigger a focus event either as the file with the current
                   * tabIndexCoord will end up selected instead of the one the
                   * user taps.
                   */
                  e.stopPropagation();
                }}
                onClick={(e) => {
                  if (file.open) file.open();
                  else onClick(e);
                }}
                sx={{ height: "100%" }}
              >
                <Grid
                  container
                  direction="column"
                  height="100%"
                  flexWrap="nowrap"
                >
                  <Grid
                    item
                    sx={{
                      flexShrink: 0,
                      padding: "8px",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      height: "calc(100% - 9999999px)",
                      flexDirection: "column",
                      flexGrow: 1,
                    }}
                  >
                    <Avatar
                      src={file.thumbnailUrl}
                      imgProps={{
                        role: "presentation",
                      }}
                      variant="rounded"
                      sx={{
                        width: "auto",
                        height: "100%",
                        aspectRatio: "1 / 1",
                        fontSize: "5em",
                        backgroundColor: "transparent",
                        pointerEvents: "none",
                      }}
                    >
                      <FileIcon fontSize="inherit" />
                    </Avatar>
                  </Grid>
                  <Grid
                    item
                    container
                    direction="row"
                    flexWrap="nowrap"
                    alignItems="baseline"
                    sx={{
                      padding: "8px",
                      paddingTop: 0,
                    }}
                  >
                    <Grid
                      item
                      sx={{
                        textAlign: "center",
                        flexGrow: 1,
                        ...(selected
                          ? {
                              backgroundColor: window.matchMedia(
                                "(prefers-contrast: more)"
                              ).matches
                                ? "black"
                                : "#35afef",
                              p: 0.25,
                              borderRadius: "4px",
                              mx: 0.5,
                            }
                          : {}),
                      }}
                    >
                      <Typography
                        sx={{
                          ...(selected
                            ? {
                                color: window.matchMedia(
                                  "(prefers-contrast: more)"
                                ).matches
                                  ? "white"
                                  : `hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 99%)`,
                              }
                            : {}),
                          fontSize: "0.8125rem",
                          fontWeight: window.matchMedia(
                            "(prefers-contrast: more)"
                          ).matches
                            ? 700
                            : 400,

                          // wrap onto a second line, but use an ellipsis after that
                          overflowWrap: "anywhere",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          display: "-webkit-box",
                          WebkitLineClamp: "2",
                          WebkitBoxOrient: "vertical",
                        }}
                      >
                        {file.name}
                      </Typography>
                    </Grid>
                  </Grid>
                </Grid>
              </CardActionArea>
            </Card>
          </Grid>
        </Fade>
      );
    }
  )
)(({ selected }) => ({
  height: "150px",
  ...(selected
    ? {
        border: window.matchMedia("(prefers-contrast: more)").matches
          ? "2px solid black"
          : SELECTED_OR_FOCUS_BORDER,
        "&:hover": {
          border: window.matchMedia("(prefers-contrast: more)").matches
            ? "2px solid black !important"
            : `${SELECTED_OR_FOCUS_BORDER} !important`,
        },
      }
    : {}),
  borderRadius: "8px",
  boxShadow: selected
    ? "none"
    : `hsl(${COLOR.main.hue} 66% 20% / 20%) 0px 2px 8px 0px`,
}));

type GalleryMainPanelArgs = {|
  selectedSection: GallerySection,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |}>,
  selectedFile: null | GalleryFile,
  setSelectedFile: (null | GalleryFile) => void,
  folderId: FetchingData.Fetched<Id>,
  refreshListing: () => void,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
  setSortOrder: ("DESC" | "ASC") => void,
  setOrderBy: ("name" | "modificationDate") => void,
|};

function GalleryMainPanel({
  selectedSection,
  path,
  clearPath,
  galleryListing,
  folderId,
  refreshListing,
  sortOrder,
  orderBy,
  setSortOrder,
  setOrderBy,
}: GalleryMainPanelArgs): Node {
  const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
    useFileImportDropZone({
      onDrop: () => {
        /*
         * Do nothing. This dropzone is purely used to open the ImportDropzone
         * panel, onto which files can then be dropped. The ImportDropzone
         * component handles the `drop` events and thus the uploading of files
         * itself. When a file is dropped elsewhere in the DialogContent,
         * nothing should happen.
         */
      },
    });
  const [viewMenuAnchorEl, setViewMenuAnchorEl] = React.useState(null);
  const [viewMode, setViewMode] = React.useState("grid");
  const [sortMenuAnchorEl, setSortMenuAnchorEl] = React.useState(null);
  const { moveFiles } = useGalleryActions();
  const selection = useGallerySelection();

  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint: {
      /*
       * This is necessary otherwise tapping gets registered as dragging
       */
      delay: 500,
      tolerance: 5,
    },
  });
  const touchSensor = useSensor(TouchSensor, {
    activationConstraint: {
      /*
       * This is necessary otherwise tapping gets registered as dragging
       */
      delay: 500,
      tolerance: 5,
    },
  });
  const keyboardSensor = useSensor(KeyboardSensor, {});

  return (
    <DialogContent
      aria-live="polite"
      sx={{
        position: "relative",
        overflowY: "hidden",
        ...(over
          ? {
              borderColor: SELECTED_OR_FOCUS_BLUE,
            }
          : {}),
      }}
      onDragEnter={onDragEnter}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      <DndContext
        sensors={[mouseSensor, touchSensor, keyboardSensor]}
        onDragEnd={(event) => {
          if (!event.over?.data.current) return;
          void moveFiles(event.active.data.current.filesBeingMoved)
            .to({
              destination: event.over.data.current.destination,
              section: selectedSection,
            })
            .then(() => {
              refreshListing();
            });
        }}
      >
        <Grid
          container
          direction="column"
          sx={{ height: "100%", flexWrap: "nowrap" }}
        >
          <Grid item>
            <Typography variant="h3" key={selectedSection}>
              <Fade
                in={true}
                timeout={
                  window.matchMedia("(prefers-reduced-motion: reduce)").matches
                    ? 0
                    : 1000
                }
              >
                <div>{gallerySectionLabel[selectedSection]}</div>
              </Fade>
            </Typography>
          </Grid>
          <Grid
            item
            container
            direction="row"
            alignItems="flex-start"
            spacing={0.5}
            sx={{ mt: 0 }}
          >
            <Grid item flexGrow={1}>
              <Stack direction="row" spacing={0.5}>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<TreeIcon />}
                  onClick={(e) => {
                    setViewMenuAnchorEl(e.target);
                  }}
                  aria-haspopup="menu"
                  sx={{ flexGrow: 1 }}
                >
                  Views
                </Button>
                <StyledMenu
                  open={Boolean(viewMenuAnchorEl)}
                  anchorEl={viewMenuAnchorEl}
                  onClose={() => setViewMenuAnchorEl(null)}
                  MenuListProps={{
                    disablePadding: true,
                  }}
                >
                  <NewMenuItem
                    title="Grid"
                    subheader="Browse by thumbnail previews"
                    backgroundColor={COLOR.background}
                    foregroundColor={COLOR.contrastText}
                    avatar={<GridIcon />}
                    onClick={() => {
                      setViewMode("grid");
                      setViewMenuAnchorEl(null);
                      selection.clear();
                    }}
                  />
                  <NewMenuItem
                    title="Tree"
                    subheader="View and manage folder hierarchy"
                    backgroundColor={COLOR.background}
                    foregroundColor={COLOR.contrastText}
                    avatar={<TreeIcon />}
                    onClick={() => {
                      setViewMode("tree");
                      setViewMenuAnchorEl(null);
                      selection.clear();
                    }}
                  />
                </StyledMenu>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<SwapVertIcon />}
                  onClick={(e) => {
                    setSortMenuAnchorEl(e.target);
                  }}
                  aria-haspopup="menu"
                  sx={{ flexGrow: 1 }}
                >
                  Sort
                </Button>
                <StyledMenu
                  open={Boolean(sortMenuAnchorEl)}
                  anchorEl={sortMenuAnchorEl}
                  onClose={() => setSortMenuAnchorEl(null)}
                  MenuListProps={{
                    disablePadding: true,
                  }}
                >
                  <NewMenuItem
                    title={`Name${match<void, string>([
                      [() => orderBy !== "name", ""],
                      [() => sortOrder === "ASC", " (Sorted from A to Z)"],
                      [() => sortOrder === "DESC", " (Sorted from Z to A)"],
                    ])()}`}
                    subheader={match<void, string>([
                      [() => orderBy !== "name", "Tap to sort from A to Z"],
                      [() => sortOrder === "ASC", "Tap to sort from Z to A"],
                      [() => sortOrder === "DESC", "Tap to sort from A to Z"],
                    ])()}
                    backgroundColor={COLOR.background}
                    foregroundColor={COLOR.contrastText}
                    avatar={match<void, Node>([
                      [
                        () => orderBy !== "name",
                        <HorizontalRuleIcon key={null} />,
                      ],
                      [
                        () => sortOrder === "ASC",
                        <ArrowDownwardIcon key={null} />,
                      ],
                      [
                        () => sortOrder === "DESC",
                        <ArrowUpwardIcon key={null} />,
                      ],
                    ])()}
                    onClick={() => {
                      setSortMenuAnchorEl(null);
                      if (orderBy === "name") {
                        if (sortOrder === "ASC") {
                          setSortOrder("DESC");
                        } else {
                          setSortOrder("ASC");
                        }
                      } else {
                        setOrderBy("name");
                        setSortOrder("ASC");
                      }
                    }}
                  />
                  <NewMenuItem
                    title={`Modification Date${match<void, string>([
                      [() => orderBy !== "modificationDate", ""],
                      [() => sortOrder === "ASC", " (Sorted oldest first)"],
                      [() => sortOrder === "DESC", " (Sorted newest first)"],
                    ])()}`}
                    subheader={match<void, string>([
                      [
                        () => orderBy !== "modificationDate",
                        "Tap to sort oldest first",
                      ],
                      [() => sortOrder === "ASC", "Tap to sort newest first"],
                      [() => sortOrder === "DESC", "Tap to sort oldest first"],
                    ])()}
                    backgroundColor={COLOR.background}
                    foregroundColor={COLOR.contrastText}
                    avatar={match<void, Node>([
                      [
                        () => orderBy !== "modificationDate",
                        <HorizontalRuleIcon key={null} />,
                      ],
                      [
                        () => sortOrder === "ASC",
                        <ArrowDownwardIcon key={null} />,
                      ],
                      [
                        () => sortOrder === "DESC",
                        <ArrowUpwardIcon key={null} />,
                      ],
                    ])()}
                    onClick={() => {
                      setSortMenuAnchorEl(null);
                      if (orderBy === "modificationDate") {
                        if (sortOrder === "ASC") {
                          setSortOrder("DESC");
                        } else {
                          setSortOrder("ASC");
                        }
                      } else {
                        setOrderBy("modificationDate");
                        setSortOrder("ASC");
                      }
                    }}
                  />
                </StyledMenu>
                <ActionsMenu
                  refreshListing={refreshListing}
                  section={selectedSection}
                />
              </Stack>
            </Grid>
            {/*
             * We use an arbitrarily large number here so that when the buttons
             * and path field are rendered on the same row, the path field
             * takes up as much space as possible. We still apply a flexGrow of
             * 1 to each of buttons so that when they are on their own row
             * above, they each take up 1/3 of the row.
             */}
            <Grid item flexGrow={99999} sx={{ position: "relative" }}>
              <Path
                section={selectedSection}
                path={path}
                clearPath={clearPath}
              />
            </Grid>
          </Grid>
          <Grid
            item
            sx={{ overflowY: "auto", mt: 1, userSelect: "none" }}
            flexGrow={1}
          >
            {viewMode === "tree" &&
              FetchingData.match(galleryListing, {
                loading: () => <></>,
                error: (error) => <>{error}</>,
                success: (listing) => (
                  <TreeView
                    listing={listing}
                    path={path}
                    selectedSection={selectedSection}
                    refreshListing={refreshListing}
                    sortOrder={sortOrder}
                    orderBy={orderBy}
                  />
                ),
              })}
            {viewMode === "grid" &&
              FetchingData.match(galleryListing, {
                loading: () => <></>,
                error: (error) => <>{error}</>,
                success: (listing) => <GridView listing={listing} />,
              })}
          </Grid>
        </Grid>
        <Slide direction="up" in={over} mountOnEnter unmountOnExit>
          <ImportDropzone
            folderId={folderId}
            path={path}
            refreshListing={refreshListing}
          />
        </Slide>
      </DndContext>
    </DialogContent>
  );
}

export default (observer(
  GalleryMainPanel
): ComponentType<GalleryMainPanelArgs>);
