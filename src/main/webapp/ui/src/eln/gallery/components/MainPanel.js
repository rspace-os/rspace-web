//@flow

import React, {
  type Node,
  type ComponentType,
  type Ref,
  type ElementConfig,
} from "react";
import DialogContent from "@mui/material/DialogContent";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import Fade from "@mui/material/Fade";
import {
  gallerySectionLabel,
  gallerySectionIcon,
  COLOR,
  SELECTED_OR_FOCUS_BORDER,
  SELECTED_OR_FOCUS_BLUE,
  type GallerySection,
} from "../common";
import { styled, alpha } from "@mui/material/styles";
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
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import { useFileImportDropZone } from "../../../components/useFileImportDragAndDrop";
import ActionsMenu from "./ActionsMenu";
import RsSet from "../../../util/set";
import TreeView from "./TreeView";
import PlaceholderLabel from "./PlaceholderLabel";
import SwapVertIcon from "@mui/icons-material/SwapVert";
import HorizontalRuleIcon from "@mui/icons-material/HorizontalRule";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import Link from "@mui/material/Link";
import { Link as ReactRouterLink } from "react-router-dom";
import useOneDimensionalRovingTabIndex from "../../../components/useOneDimensionalRovingTabIndex";
import Box from "@mui/material/Box";
import Fab from "@mui/material/Fab";
import useUiPreference, { PREFERENCES } from "../../../util/useUiPreference";
import Divider from "@mui/material/Divider";
import {
  InfoPanelForSmallViewports,
  InfoPanelForLargeViewports,
} from "./InfoPanel";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";
import usePrimaryAction from "../primaryActionHooks";
import { Optional, getByKey } from "../../../util/optional";
import LoadMoreButton from "./LoadMoreButton";
import Carousel from "./Carousel";
import ViewCarouselIcon from "@mui/icons-material/ViewCarousel";
import { useDeploymentProperty } from "../../useDeploymentProperty";
import { useFolderOpen } from "./OpenFolderProvider";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Chip from "@mui/material/Chip";

const StyledBreadcrumbs = styled(Breadcrumbs)(({ theme }) => ({
  "& .MuiBreadcrumbs-ol": {
    /*
     * Scroll horizontally when there is no longer enough space. We don't want
     * to wrap because deep hierarchies would end up taking up lots of space on
     * mobile. We don't want to use the `maxItems` property as that prevents
     * the possibility of using drag-and-drop to move files in to ancestor
     * folders, and would only lead to wrapping once the button is tapped
     * anyway.
     */
    flexWrap: "nowrap",
    overflowX: "auto",
  },
  "& .MuiBreadcrumbs-separator": {
    marginLeft: theme.spacing(0.5),
    marginRight: theme.spacing(0.5),
  },
}));

const StyledBreadcrumb = styled(
  React.forwardRef((props, ref) => <Chip ref={ref} {...props} clickable />)
)(({ theme }) => ({
  height: theme.spacing(3.5),
  color: alpha(theme.palette.primary.contrastText, 0.85),
  paddingLeft: theme.spacing(0.5),
  paddingRight: theme.spacing(0.5),
  paddingTop: theme.spacing(0.25),
  paddingBottom: theme.spacing(0.25),
  border: `2px solid ${theme.palette.primary.main}`,
  fontWeight: 500,
  fontSize: "1rem",
  cursor: "pointer",
  "& .MuiChip-icon": {
    fontSize: "1.05rem",
    marginRight: theme.spacing(-0.5),
    color: alpha(theme.palette.primary.contrastText, 0.85),
  },
}));

const DragCancelFab = () => {
  const dndContext = useDndContext();
  const dndInProgress = Boolean(dndContext.active);
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: "cancel",
    disabled: false,
    data: null,
  });
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: SELECTED_OR_FOCUS_BORDER,
      }
    : {
        border: "2px solid white",
        animation: "drop 2s linear infinite",
      };
  return (
    <>
      {dndInProgress && (
        <div
          style={{
            position: "fixed",
            right: "16px",
            bottom: "16px",
          }}
        >
          <Fab
            variant="extended"
            color="primary"
            ref={(node) => {
              setDropRef(node);
            }}
            style={dropStyle}
          >
            Cancel
          </Fab>
        </div>
      )}
    </>
  );
};

const BreadcrumbLink = React.forwardRef<
  ElementConfig<typeof Link>,
  null | typeof Link
>(
  (
    {
      folder,
      section,
      clearPath,
      tabIndex,
      sx,
    }: {|
      /*
       * Undefined means that it is a link to the root of the section
       */
      folder?: GalleryFile,
      section: string,
      clearPath: () => void,
      tabIndex: number,
      sx: mixed,
    |},
    ref:
      | null
      | { -current: null | Ref<typeof Link> }
      | ((null | Ref<typeof Link>) => mixed)
  ) => {
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
      : {};
    const { openFolder } = useFolderOpen();

    return (
      <StyledBreadcrumb
        component={ReactRouterLink}
        to={""}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          if (folder) {
            openFolder(folder);
          } else {
            clearPath();
          }
        }}
        ref={(node) => {
          setDropRef(node);
          if (!ref) return;
          if (typeof ref === "function") ref(node);
          else ref.current = node;
        }}
        style={{
          ...dropStyle,
        }}
        tabIndex={tabIndex}
        label={
          folder?.name ??
          getByKey(section, gallerySectionLabel).orElse("UNKNOWN")
        }
        icon={
          folder ? null : getByKey(section, gallerySectionIcon).orElse(null)
        }
        sx={sx}
      />
    );
  }
);

const Path = ({
  section,
  path,
  clearPath,
}: {|
  section: string,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
|}) => {
  const {
    eventHandlers: { onFocus, onBlur, onKeyDown },
    getTabIndex,
    getRef,
  } = useOneDimensionalRovingTabIndex<typeof Link>({
    max: path.length,
    direction: "row",
  });
  const selection = useGallerySelection();

  return (
    <StyledBreadcrumbs onFocus={onFocus} onBlur={onBlur} onKeyDown={onKeyDown}>
      <BreadcrumbLink
        section={section}
        clearPath={clearPath}
        sx={{
          pl: 1,
        }}
        ref={getRef(0)}
        tabIndex={getTabIndex(0)}
      />
      {selection
        .asSet()
        .only.map((f) => f.path)
        .orElse(path)
        .map((f, i) => (
          <BreadcrumbLink
            key={idToString(f.id)}
            folder={f}
            section={section}
            clearPath={clearPath}
            ref={getRef(i + 1)}
            tabIndex={getTabIndex(i + 1)}
          />
        ))}
    </StyledBreadcrumbs>
  );
};

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

const GridView = observer(
  ({
    listing,
  }: {|
    listing:
      | {| tag: "empty", reason: string, refreshing: boolean |}
      | {|
          tag: "list",
          list: $ReadOnlyArray<GalleryFile>,
          totalHits: number,
          loadMore: Optional<() => Promise<void>>,
          refreshing: boolean,
        |},
  |}) => {
    const dndContext = useDndContext();
    const selection = useGallerySelection();
    const { openImagePreview } = useImagePreview();
    const { openPdfPreview } = usePdfPreview();
    const { openAsposePreview } = useAsposePreview();
    const { openFolder } = useFolderOpen();
    const primaryAction = usePrimaryAction();

    const viewportDimensions = useViewportDimensions();
    const cardWidth = {
      xs: 6,
      sm: 4,
      md: 4,
      lg: 3,
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
     * This variable stores the Id of the last file that was focussed -- either
     * by tapping or by moving the focus with the arrow keys -- without shift
     * being held down. It forms the corner point of a selected region of
     * files; the opposite corner being defined by the file that is focussed
     * whilst shift is held.
     *
     * We use an Id, rather than a reference to the GalleryFile or the
     * coordinates in the grid, because if the listing is refreshed after an
     * action has been performed, the selection is maintained because it too is
     * based on Ids (see ../useGallerySelection) and thus expanding the
     * selection with shift should continue from that existing selection.
     *
     * It is null when this component is initially mounted but as soon as the
     * user has focussed any file (without shift being held) it holds the Id of
     * that last focussed file. Even if the selection is cleared with the
     * escape key or the only selected files deleted, this variable maintains
     * that Id.
     */
    const [shiftOriginFileId, setShiftOriginFileId] = React.useState<null | Id>(
      null
    );

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
              <PlaceholderLabel>
                {listing.refreshing
                  ? "Refreshing..."
                  : listing.reason ?? "There are no folders."}
              </PlaceholderLabel>
            </div>
          </Fade>
        </div>
      );
    return (
      <>
        <Grid
          role="region"
          aria-label="grid view of files"
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

            let origin = { x, y };
            if (e.shiftKey) {
              if (shiftOriginFileId) {
                const indexOfShiftOriginFile = listing.list.findIndex(
                  (f) => f.id === shiftOriginFileId
                );
                const shiftOriginX = indexOfShiftOriginFile % cols;
                const shiftOriginY = Math.floor(indexOfShiftOriginFile / cols);
                origin = { x: shiftOriginX, y: shiftOriginY };
              } else {
                origin = tabIndexCoord;
              }
            }
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

            setShiftOriginFileId(
              e.shiftKey
                ? shiftOriginFileId ?? listing.list[y * cols + x].id
                : listing.list[y * cols + x].id
            );
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
                  if (!shiftOriginFileId) return;
                  const indexOfShiftOriginFile = listing.list.findIndex(
                    (f) => f.id === shiftOriginFileId
                  );
                  /*
                   * if shiftOriginFileId is an Id of a file that has been
                   * deleted then it will no longer be in the listing, will not
                   * be visible and this code should act as if no file has been
                   * focussed
                   */
                  if (indexOfShiftOriginFile === -1) return;
                  const tappedCoord = {
                    x: index % cols,
                    y: Math.floor(index / cols),
                  };
                  const shiftOriginX = indexOfShiftOriginFile % cols;
                  const shiftOriginY = Math.floor(
                    indexOfShiftOriginFile / cols
                  );
                  const toSelect = listing.list.filter((_file, i) => {
                    const coord = {
                      x: i % cols,
                      y: Math.floor(i / cols),
                    };
                    return (
                      coord.x >= Math.min(tappedCoord.x, shiftOriginX) &&
                      coord.x <= Math.max(tappedCoord.x, shiftOriginX) &&
                      coord.y >= Math.min(tappedCoord.y, shiftOriginY) &&
                      coord.y <= Math.max(tappedCoord.y, shiftOriginY)
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
                  // on double click, try and figure out what the user would want
                  // to do with a file of this type based on what services are
                  // configured
                  if (e.detail > 1) {
                    primaryAction(file).do((action) => {
                      if (action.tag === "open") {
                        openFolder(file);
                        return;
                      }
                      if (action.tag === "image") {
                        void action.downloadHref().then((downloadHref) => {
                          openImagePreview(downloadHref, {
                            caption: action.caption,
                          });
                        });
                        return;
                      }
                      if (action.tag === "collabora") {
                        window.open(action.url);
                        return;
                      }
                      if (action.tag === "officeonline") {
                        window.open(action.url);
                        return;
                      }
                      if (action.tag === "pdf") {
                        void action.downloadHref().then((downloadHref) => {
                          openPdfPreview(downloadHref);
                        });
                        return;
                      }
                      if (action.tag === "aspose") {
                        void openAsposePreview(file);
                      }
                    });
                    return;
                  }
                  selection.clear();
                  selection.append(file);
                  setShiftOriginFileId(file.id);
                  setTabIndexCoord({
                    x: index % cols,
                    y: Math.floor(index / cols),
                  });
                }
              }}
            />
          ))}
        </Grid>
        {listing.loadMore
          .map((loadMore) => (
            <Box key={null} sx={{ mt: 1 }}>
              <LoadMoreButton onClick={loadMore} />
            </Box>
          ))
          .orElse(null)}
      </>
    );
  }
);

const FileCard = styled(
  observer(
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
        const NAME_STYLES = {
          LINE_HEIGHT: 1.5,
          FONT_SIZE: "0.8125rem",
        };
        const { uploadFiles } = useGalleryActions();
        const { openFolder } = useFolderOpen();
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

        /*
         * When the user taps the FileCard, we don't want to immediately
         * trigger useDraggable's listeners because otherwise react gets bogged
         * down in re-rendering all of the FileCards a couple of times over
         * before actually updating the new selection state. When the user taps,
         * onMouseDown/onTouchStart trigger which causes a re-rendering with
         * drag-and-drop active, then onMouseUp/onTouchEnd fire immediately
         * after triggering another re-rendering with drag-and-drop not active,
         * and then onClick fires to update the selection state.
         *
         * This state variable contains a reference to a setTimeout that is
         * intended to only fire onMouseDown/onTouchStart if the user holds the
         * mouse key/their finger down for more than half a second to prevent
         * these excessive re-renders and make the UI more responsive in
         * updating the selection state.
         */
        const [dndDebounce, setDndDebounce] = React.useState<null | TimeoutID>(
          null
        );

        const dragStyle: { [string]: string | number } = transform
          ? {
              transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.1)`,
              zIndex: 1400, // Above the sidebar
              position: "fixed",
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
          md: 4,
          lg: 3,
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
                      (index + 1) *
                      cardWidth[viewportDimensions.viewportSize] *
                      3
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
                onMouseDown={(...args) => {
                  setDndDebounce(
                    setTimeout(() => {
                      listeners.onMouseDown(...args);
                    }, 500)
                  );
                }}
                onMouseUp={() => {
                  clearTimeout(dndDebounce);
                }}
                onTouchStart={(...args) => {
                  setDndDebounce(
                    setTimeout(() => {
                      listeners.onTouchStart(...args);
                    }, 500)
                  );
                }}
                onTouchEnd={() => {
                  clearTimeout(dndDebounce);
                }}
                onKeyDown={listeners.onKeyDown}
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
                {...file.canOpen
                  .map(() => ({
                    onKeyDown: (e: KeyboardEvent) => {
                      if (e.key === " ") openFolder(file);
                    },
                  }))
                  .orElse({})}
              >
                <CardActionArea
                  role={file.canOpen.map(() => "button").orElse("checkbox")}
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
                    onClick(e);
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
                          // give room for two lines of text
                          lineHeight: NAME_STYLES.LINE_HEIGHT,
                          fontSize: NAME_STYLES.FONT_SIZE,
                          minHeight: `calc(2* ${NAME_STYLES.LINE_HEIGHT}* ${NAME_STYLES.FONT_SIZE})`,

                          display: "flex",
                          flexDirection: "column",
                          justifyContent: "end",
                          flexGrow: 1,
                          textAlign: "center",
                        }}
                      >
                        <Typography
                          sx={{
                            ...(selected
                              ? {
                                  backgroundColor: (theme) =>
                                    window.matchMedia(
                                      "(prefers-contrast: more)"
                                    ).matches
                                      ? "black"
                                      : theme.palette.callToAction.main,
                                  p: 0.25,
                                  borderRadius: "4px",
                                  mx: 0.5,
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
                  {typeof file.version === "number" && file.version > 1 && (
                    <div
                      className="versionIndicator"
                      aria-label={`version ${file.version}`}
                    >
                      v{file.version}
                    </div>
                  )}
                </CardActionArea>
              </Card>
            </Grid>
          </Fade>
        );
      }
    )
  )
)(({ selected, theme }) => ({
  height: "150px",
  "& .versionIndicator": {
    position: "absolute",
    top: "0",
    right: "0",
    margin: theme.spacing(0.25),
    padding: theme.spacing(0.25, 0.5),
    borderRadius: "5px",
    color: window.matchMedia("(prefers-contrast: more)").matches
      ? "rgb(255,255,255)"
      : `hsl(${COLOR.contrastText.hue}deg, ${COLOR.contrastText.saturation}%, ${COLOR.contrastText.lightness}%, 100%)`,
    border: `1px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, ${COLOR.background.lightness}%)`,
    ...(selected
      ? {
          borderColor: window.matchMedia("(prefers-contrast: more)").matches
            ? "black"
            : theme.palette.callToAction.main,
        }
      : {}),
  },
  ...(selected
    ? {
        border: window.matchMedia("(prefers-contrast: more)").matches
          ? "2px solid black"
          : SELECTED_OR_FOCUS_BORDER,
        "&:hover": {
          border: window.matchMedia("(prefers-contrast: more)").matches
            ? "2px solid black !important"
            : `${SELECTED_OR_FOCUS_BORDER} !important`,
          backgroundColor: `${alpha(
            theme.palette.callToAction.background,
            0.05
          )} !important`,
        },
        backgroundColor: alpha(theme.palette.callToAction.background, 0.15),
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
    | {| tag: "empty", reason: string, refreshing: boolean |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        totalHits: number,
        loadMore: Optional<() => Promise<void>>,
        refreshing: boolean,
      |}
  >,
  folderId: FetchingData.Fetched<Id>,
  refreshListing: () => Promise<void>,
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
  const viewportDimensions = useViewportDimensions();
  const filestoresEnabled = useDeploymentProperty("netfilestores.enabled");
  const { uploadFiles } = useGalleryActions();
  const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
    useFileImportDropZone({
      onDrop: doNotAwait(async (files) => {
        const fId = FetchingData.getSuccessValue<Id>(folderId).orElseGet(() => {
          throw new Error("Unknown folder id");
        });
        await uploadFiles(path, fId, files);
        void refreshListing();
      }),
    });
  const [viewMenuAnchorEl, setViewMenuAnchorEl] = React.useState(null);
  const [viewMode, setViewMode] = useUiPreference(
    PREFERENCES.GALLERY_VIEW_MODE,
    {
      defaultValue: "grid",
    }
  );
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

  return FetchingData.match(filestoresEnabled, {
    loading: () => null,
    error: () => (
      <PlaceholderLabel>
        Erorr checking if filestores are enabled.
      </PlaceholderLabel>
    ),
    success: (filestoresEnabled) => {
      const validGallerySections = new Set([
        "Images",
        "Audios",
        "Videos",
        "Documents",
        "Chemistry",
        "DMPs",
        "Snippets",
        "Miscellaneous",
        ...(filestoresEnabled === true ? ["NetworkFiles"] : []),
        "PdfDocuments",
      ]);
      if (!validGallerySections.has(selectedSection))
        return (
          <PlaceholderLabel>Not a valid Gallery section.</PlaceholderLabel>
        );
      return (
        <DialogContent
          aria-live="polite"
          sx={{
            position: "relative",
            overflowY: "hidden",
            pr: 2.5,
            ...(over
              ? {
                  outline: `3px solid ${SELECTED_OR_FOCUS_BLUE}`,
                  outlineOffset: "-3px",
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
              /*
               * When tapping, holding, and then releasing on a folder onDragEnd is
               * invoked with event.over.data.current.destination.folder being and
               * event.active.data.current.filesBeingMoved including the container
               * that is tapped. This is clearly an unintended operation and we
               * should not attempt to move the container into itself.
               */
              if (
                event.active.data.current.filesBeingMoved.has(
                  event.over.data.current.destination.folder
                )
              )
                return;
              void moveFiles(event.active.data.current.filesBeingMoved)
                .to({
                  destination: event.over.data.current.destination,
                  section: selectedSection,
                })
                .then(() => {
                  void refreshListing();
                });
            }}
          >
            <Grid
              container
              direction="column"
              sx={{ height: "100%", flexWrap: "nowrap" }}
              spacing={1}
            >
              <Grid item sx={{ pt: "0 !important" }}>
                <Path
                  section={selectedSection}
                  path={path}
                  clearPath={clearPath}
                />
              </Grid>
              <Grid item sx={{ maxWidth: "100% !important" }}>
                <Stack
                  direction="row"
                  spacing={0.5}
                  alignItems="center"
                  role="region"
                  aria-label="files listing controls"
                >
                  <ActionsMenu
                    refreshListing={refreshListing}
                    section={selectedSection}
                    folderId={folderId}
                  />
                  <Box sx={{ flexGrow: 1 }}></Box>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<TreeIcon />}
                    onClick={(e) => {
                      setViewMenuAnchorEl(e.currentTarget);
                    }}
                    aria-haspopup="menu"
                    aria-expanded={viewMenuAnchorEl ? "true" : "false"}
                  >
                    Views
                  </Button>
                  <StyledMenu
                    open={Boolean(viewMenuAnchorEl)}
                    anchorEl={viewMenuAnchorEl}
                    onClose={() => setViewMenuAnchorEl(null)}
                    MenuListProps={{
                      disablePadding: true,
                      "aria-label": "view options",
                    }}
                  >
                    <NewMenuItem
                      title="Grid"
                      subheader="Browse by thumbnail previews."
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
                      subheader="View and manage folder hierarchy."
                      backgroundColor={COLOR.background}
                      foregroundColor={COLOR.contrastText}
                      avatar={<TreeIcon />}
                      onClick={() => {
                        setViewMode("tree");
                        setViewMenuAnchorEl(null);
                        selection.clear();
                      }}
                    />
                    <NewMenuItem
                      title="Carousel"
                      subheader="Flick through all files to find one."
                      backgroundColor={COLOR.background}
                      foregroundColor={COLOR.contrastText}
                      avatar={<ViewCarouselIcon />}
                      onClick={() => {
                        setViewMode("carousel");
                        setViewMenuAnchorEl(null);
                        /*
                         * We don't clear the selection because we want
                         * carousel view to default to the selected file,
                         * if there is one
                         */
                      }}
                    />
                  </StyledMenu>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={<SwapVertIcon />}
                    onClick={(e) => {
                      setSortMenuAnchorEl(e.currentTarget);
                    }}
                    aria-haspopup="menu"
                    aria-expanded={sortMenuAnchorEl ? "true" : "false"}
                    disabled={selectedSection === "NetworkFiles"}
                  >
                    Sort
                  </Button>
                  <StyledMenu
                    open={Boolean(sortMenuAnchorEl)}
                    anchorEl={sortMenuAnchorEl}
                    onClose={() => setSortMenuAnchorEl(null)}
                    MenuListProps={{
                      disablePadding: true,
                      "aria-label": "sort listing",
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
                        [
                          () => sortOrder === "DESC",
                          "Tap to sort oldest first",
                        ],
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
                </Stack>
              </Grid>
              <Grid item sx={{ display: { xs: "none", md: "block" } }}>
                <Divider orientation="horizontal" />
              </Grid>
              <Grid
                item
                container
                direction="row"
                sx={{
                  /*
                   * This removes the gap between the listing and info panel and
                   * the divider so that the vertical divider separating the
                   * listing from the info panel reaches up and touches the
                   * horizontal one above. The listing and info panel then add
                   * their own whitespace.
                   */
                  pt: "0 !important",

                  minHeight: 0,
                  /*
                   * This prevents content from being hidden underneath the
                   * floating info panel. 136px was found by visual inspection.
                   */
                  maxHeight:
                    viewportDimensions.isViewportSmall && !selection.isEmpty
                      ? "calc(100% - 136px)"
                      : "100%",
                }}
                flexWrap="nowrap"
                flexGrow="1"
              >
                <Grid
                  item
                  sx={{ overflowY: "auto", userSelect: "none", mt: 1 }}
                  md={7}
                  lg={8}
                  xl={9}
                  flexGrow={1}
                  role="region"
                  aria-label="files listing"
                >
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
                            foldersOnly={false}
                          />
                        ),
                      })}
                    {viewMode === "grid" &&
                      FetchingData.match(galleryListing, {
                        loading: () => <></>,
                        error: (error) => <>{error}</>,
                        success: (listing) => <GridView listing={listing} />,
                      })}
                    {viewMode === "carousel" &&
                      FetchingData.match(galleryListing, {
                        loading: () => <></>,
                        error: (error) => <>{error}</>,
                        success: (listing) => <Carousel listing={listing} />,
                      })}
                  </Grid>
                </Grid>
                <Grid
                  item
                  sx={{ mx: 1.5, display: { xs: "none", md: "block" } }}
                >
                  <Divider orientation="vertical" />
                </Grid>
                <Grid
                  item
                  md={5}
                  lg={4}
                  xl={3}
                  sx={{
                    display: { xs: "none", md: "block" },
                    overflowX: "hidden",
                    overflowY: "auto",
                    mt: 0.75,
                  }}
                  role="region"
                  aria-label="info panel"
                >
                  <InfoPanelForLargeViewports
                    /*
                     * When the selection changes we want to unmount the current
                     * info panel and mount a new one, thereby resetting any
                     * modified state. If we didn't have this key and simply
                     * re-rendered then if there was and still is one file selected
                     * then the new name would not match the state held by the name
                     * field so the component would think that it is in a modified
                     * state and should open the Save and Cancel buttons. Same
                     * applies to the Description field.
                     */
                    key={selection
                      .asSet()
                      .reduce((acc, { id }) => `${acc},${idToString(id)}`, "")}
                  />
                </Grid>
                {selection
                  .asSet()
                  .only.map((file) => (
                    /*
                     * Same applies here, in that we want to unmount and mount a
                     * new info panel when the selection changes to reset any
                     * modified state.
                     */
                    <InfoPanelForSmallViewports
                      key={idToString(file.id)}
                      file={file}
                    />
                  ))
                  .orElse(null)}
              </Grid>
            </Grid>
            <DragCancelFab />
          </DndContext>
        </DialogContent>
      );
    },
  });
}

/**
 * This component constitues most of the Gallery page, including the section
 * title, the breadcrumbs, the menus and buttons for controlling the listing,
 * the listing of files itself, the info panel, and the dropzone for dragging
 * in files from outside the browser.
 */
export default (observer(
  GalleryMainPanel
): ComponentType<GalleryMainPanelArgs>);
