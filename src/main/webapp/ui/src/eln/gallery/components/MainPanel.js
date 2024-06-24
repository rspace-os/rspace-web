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
import { gallerySectionLabel, COLOR } from "../common";
import { styled } from "@mui/material/styles";
import useViewportDimensions from "../../../util/useViewportDimensions";
import Card from "@mui/material/Card";
import CardActionArea from "@mui/material/CardActionArea";
import Avatar from "@mui/material/Avatar";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import { COLORS as baseThemeColors } from "../../../theme";
import * as FetchingData from "../../../util/fetchingData";
import * as MapUtils from "../../../util/MapUtils";
import {
  useGalleryListing,
  type GalleryFile,
  type Id,
  idToString,
} from "../useGalleryListing";
import {
  useGalleryActions,
  useGallerySelection,
  mkSelection,
} from "../useGalleryActions";
import { doNotAwait } from "../../../util/Util";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
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
import Box from "@mui/material/Box";
import NewMenuItem from "./NewMenuItem";
import Collapse from "@mui/material/Collapse";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Slide from "@mui/material/Slide";
import Stack from "@mui/material/Stack";
import { runInAction } from "mobx";
import { useLocalObservable, observer } from "mobx-react-lite";
import { useFileImportDropZone } from "../../../components/useFileImportDragAndDrop";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import ActionsMenu from "./ActionsMenu";

const SELECTED_OR_FOCUS_BLUE = `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`;
const SELECTED_OR_FOCUS_BORDER = `2px solid ${SELECTED_OR_FOCUS_BLUE}`;

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

type TreeItemContentArgs = {|
  file: GalleryFile,
  path: $ReadOnlyArray<GalleryFile>,
  section: string,
  idMap: Map<string, GalleryFile>,
  refreshListing: () => void,
|};

const TreeItemContent: ComponentType<TreeItemContentArgs> = observer(
  ({
    path,
    file,
    section,
    idMap,
    refreshListing,
  }: TreeItemContentArgs): Node => {
    const { galleryListing } = useGalleryListing({
      section,
      searchTerm: "",
      path: [...path, file],
    });

    React.useEffect(() => {
      FetchingData.getSuccessValue(galleryListing).do((listing) => {
        if (listing.tag === "empty") return;
        runInAction(() => {
          for (const f of listing.list) idMap.set(idToString(f.id), f);
        });
      });
    }, [galleryListing]);

    return FetchingData.match(galleryListing, {
      /*
       * nothing is shown whilst loading otherwise the UI ends up being quite
       * janky when there ends up being nothing in the folder
       */
      loading: () => null,
      error: (error) => <>{error}</>,
      success: (listing) =>
        listing.tag === "list"
          ? listing.list.map((f, i) => (
              <CustomTreeItem
                file={f}
                index={i}
                path={[...path, file]}
                section={section}
                key={idToString(f.id)}
                idMap={idMap}
                refreshListing={refreshListing}
              />
            ))
          : null,
    });
  }
);

const CustomTransition = styled(({ children, in: open, className }) => (
  <div className={className}>
    <Collapse
      in={open}
      timeout={
        window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200
      }
    >
      {children}
    </Collapse>
  </div>
))(({ in: open }) => ({
  "& .MuiCollapse-wrapperInner > .MuiBox-root": {
    transform:
      window.matchMedia("(prefers-reduced-motion: reduce)").matches || open
        ? "none"
        : "translateX(-10px) !important",
    opacity:
      window.matchMedia("(prefers-reduced-motion: reduce)").matches || open
        ? 1
        : "0 !important",
    transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "none"
      : "all .2s ease",
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
    id: `/${[selectedSection, ...path.map(({ name }) => name), folder?.name ?? ""].join(
      "/"
    )}/`,
    disabled: false,
    data: {
      path,
      destination: folder ? { key: "folder", folder } : { key: "root" },
    },
  });
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: SELECTED_OR_FOCUS_BORDER,
      }
    : {
        border: "2px solid white",
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

const CustomTreeItem = observer(
  ({
    file,
    index,
    path,
    section,
    idMap,
    refreshListing,
  }: {|
    file: GalleryFile,
    index: number,
    path: $ReadOnlyArray<GalleryFile>,
    section: string,
    idMap: Map<string, GalleryFile>,
    refreshListing: () => void,
  |}) => {
    const { uploadFiles } = useGalleryActions();
    const selection = useGallerySelection();
    const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
      useFileImportDropZone({
        onDrop: doNotAwait(async (files) => {
          await uploadFiles([...file.path, file], file.id, files);
          refreshListing();
        }),
        disabled: !/Folder/.test(file.type),
      });
    const { setNodeRef: setDropRef, isOver } = useDroppable({
      id: file.id,
      disabled: !/Folder/.test(file.type),
      data: {
        path: file.path,
        destination: { key: "folder", folder: file },
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
         * If this `file` is one of the selected files then all of the selected
         * files are to be moved by the drag operation. If it is not included
         * then just move this file.
         */
        selectedFiles: selection.isSelected(file)
          ? selection.asSetOfIds()
          : mkSelection(),
        currentFile: file,
      },
    });
    const dndContext = useDndContext();

    const dragStyle: { [string]: string | number } = transform
      ? {
          transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.1)`,
          zIndex: 1, // just needs to be rendered above Nodes later in the DOM
          position: "relative",
          boxShadow: `hsl(${COLOR.main.hue}deg 66% 10% / 20%) 0px 2px 16px 8px`,
          maxWidth: "max-content",
        }
      : {};
    const dropStyle: { [string]: string | number } = isOver
      ? {
          border: SELECTED_OR_FOCUS_BORDER,
        }
      : {
          border: `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 99%)`,
        };
    const inGroupBeingDraggedStyle: { [string]: string | number } =
      (dndContext.active?.data.current?.selectedFiles ?? new Set()).has(
        file.id
      ) && dndContext.active?.id !== file.id
        ? {
            opacity: 0.2,
          }
        : {};
    const fileUploadDropping: { [string]: string | number } = over
      ? {
          border: SELECTED_OR_FOCUS_BORDER,
        }
      : {};

    return (
      <Box
        sx={{
          transitionDelay: `${(index + 1) * 0.04}s !important`,
        }}
      >
        <TreeItem
          itemId={idToString(file.id)}
          label={
            <Box
              sx={{
                display: "flex",
                pointerEvents: "none",
                userSelect: "none",
              }}
            >
              <Avatar
                src={file.thumbnailUrl}
                imgProps={{
                  role: "presentation",
                }}
                variant="rounded"
                sx={{
                  width: "24px",
                  height: "24px",
                  aspectRatio: "1 / 1",
                  fontSize: "5em",
                  margin: "2px 12px 2px 8px",
                  background: "white",
                }}
              >
                <FileIcon fontSize="inherit" />
              </Avatar>
              {file.name}
            </Box>
          }
          slots={{ groupTransition: CustomTransition }}
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
          }}
          {...listeners}
          {...attributes}
          style={{
            ...dragStyle,
            ...dropStyle,
            ...inGroupBeingDraggedStyle,
            ...fileUploadDropping,
            borderRadius: "4px",
          }}
        >
          {/Folder/.test(file.type) && (
            <TreeItemContent
              file={file}
              path={path}
              section={section}
              idMap={idMap}
              refreshListing={refreshListing}
            />
          )}
        </TreeItem>
      </Box>
    );
  }
);

const GridView = observer(
  ({
    listing,
  }: {|
    listing:
      | {| tag: "empty", reason: string |}
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
          if (!selection.someFilesAreSelected())
            selection.append(listing.list[y * cols + x]);
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
            selected={selection.isSelected(file)}
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
                if (selection.isSelected(file)) {
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
          disabled: !/Folder/.test(file.type),
        });
      const { setNodeRef: setDropRef, isOver } = useDroppable({
        id: file.id,
        disabled: !/Folder/.test(file.type),
        data: {
          path: file.path,
          destination: { key: "folder", folder: file },
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
           * If this `file` is one of the selected files  then all of the
           * selected files are to be moved by the drag operation. If it is not
           * included then just move this file.
           */
          selectedFiles: selection.isSelected(file)
            ? selection.asSetOfIds()
            : mkSelection(),
          currentFile: file,
        },
      });
      /*
       * DndKit wants to set the role to "button" but if we do that then the
       * keyboard controls of MUI's SimpleTreeView stop working. Keeping the
       * correct role for tree items doesn't prevent DndKit from working.
       */
      delete attributes.role;

      const dndContext = useDndContext();

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
        : {};
      const inGroupBeingDraggedStyle: { [string]: string | number } =
        (dndContext.active?.data.current?.selectedFiles ?? new Set()).has(
          file.id
        ) && dndContext.active?.id !== file.id
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

const TreeView = observer(
  ({
    listing,
    path,
    selectedSection,
    refreshListing,
  }: {|
    listing:
      | {| tag: "empty", reason: string |}
      | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |},
    path: $ReadOnlyArray<GalleryFile>,
    selectedSection: string,
    refreshListing: () => void,
  |}) => {
    const { addAlert } = React.useContext(AlertContext);
    const selection = useGallerySelection();
    const [expandedItems, setExpandedItems] = React.useState<
      $ReadOnlyArray<GalleryFile["id"]>
    >([]);

    /*
     * Problem is, this map only contains the root level files/folders
     */
    const idMap = useLocalObservable(() => {
      const map = new Map<string, GalleryFile>();
      if (listing.tag === "empty") return map;
      for (const file of listing.list) map.set(idToString(file.id), file);
      return map;
    });

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
      <SimpleTreeView
        expandedItems={expandedItems}
        onExpandedItemsChange={(_event, nodeIds) => {
          setExpandedItems(nodeIds);
        }}
        selectedItems={selection.asTreeViewModel()}
        onItemSelectionToggle={(
          event,
          itemId: string | $ReadOnlyArray<string>,
          selected
        ) => {
          /*
           * If there are multiple files selected and the user taps any file
           * (already selected or not) then this function is called twice: first
           * with `itemId` as an array of all of the selected nodes with
           * `selected` set to false, followed by `itemId` as a string value and
           * `selected` set to true. The idea being, that you can first clear the
           * data structure being passed as `selectedItems` and then add back the
           * singular selected item. However, because we wish to handle ctrl/meta
           * keys, we ignore this first invocation and instead just consider
           * whether the tapped file is already selected or not to toggle its
           * state. As such, we can ignore the first invocation where `itemId` is
           * an array.
           */
          if (Array.isArray(itemId)) return;
          /*
           * It's not possible for us to support shift-clicking in tree view
           * because there's no data structure we can query to find a range of
           * adjacent nodes. There's simply no way for us to get the itemIds of
           * the nodes between two selected nodes; even more so if the user were
           * to attempt to select nodes are different levels of the hierarchy.
           * SimpleTreeView does have a multiSelect mode but attempting to use it
           * just results in console errors.
           */
          if (event.shiftKey) {
            if (selected) {
              addAlert(
                mkAlert({
                  title: "Shift selection is not supported in tree view.",
                  message:
                    "Either use command/ctrl to select each in turn, or use grid view.",
                  variant: "warning",
                })
              );
            }
            return;
          }
          if (event.ctrlKey || event.metaKey) {
            MapUtils.get(idMap, itemId).do((file) => {
              if (selection.isSelected(file)) {
                selection.remove(file);
              } else {
                selection.append(file);
              }
            });
          } else {
            selection.clear();
            if (selected) {
              MapUtils.get(idMap, itemId).do((file) => {
                selection.append(file);
              });
            }
          }
        }}
      >
        {listing.list.map((file, index) => (
          <CustomTreeItem
            index={index}
            file={file}
            path={path}
            key={idToString(file.id)}
            section={selectedSection}
            idMap={idMap}
            refreshListing={refreshListing}
          />
        ))}
      </SimpleTreeView>
    );
  }
);

const PlaceholderLabel = styled(({ children, className }) => (
  <Grid container className={className}>
    <Grid
      item
      sx={{
        p: 1,
        pt: 2,
        pr: 5,
      }}
    >
      {children}
    </Grid>
  </Grid>
))(() => ({
  justifyContent: "stretch",
  alignItems: "stretch",
  height: "100%",
  "& > *": {
    fontSize: "2rem",
    fontWeight: 700,
    color: window.matchMedia("(prefers-contrast: more)").matches
      ? "black"
      : "hsl(190deg, 20%, 29%, 37%)",
    flexGrow: 1,
    textAlign: "center",

    overflowWrap: "anywhere",
    overflow: "hidden",
  },
}));

type GalleryMainPanelArgs = {|
  selectedSection: string,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |}
  >,
  selectedFile: null | GalleryFile,
  setSelectedFile: (null | GalleryFile) => void,
  folderId: FetchingData.Fetched<Id>,
  refreshListing: () => void,
|};

function GalleryMainPanel({
  selectedSection,
  path,
  clearPath,
  galleryListing,
  folderId,
  refreshListing,
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
          void moveFiles(
            selection.isSelected(event.active.data.current.currentFile)
              ? selection.asSet()
              : new Set([event.active.data.current.currentFile])
          )
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
            justifyContent="space-between"
            alignItems="flex-start"
            flexWrap="nowrap"
          >
            <Grid item>
              <CustomBreadcrumbs
                separator="›"
                aria-label="breadcrumb"
                sx={{ mt: 0.5 }}
              >
                <Breadcrumb
                  label={gallerySectionLabel[selectedSection]}
                  onClick={() => clearPath()}
                  path={[]}
                  selectedSection={selectedSection}
                />
                {path.map((folder) => (
                  <Breadcrumb
                    folder={folder}
                    label={folder.name}
                    key={idToString(folder.id)}
                    onClick={() => folder.open?.()}
                    path={folder.path}
                    selectedSection={selectedSection}
                  />
                ))}
              </CustomBreadcrumbs>
            </Grid>
            <Grid item sx={{ mt: 0.5 }}>
              <Stack direction="row" spacing={1}>
                <ActionsMenu refreshListing={refreshListing} />
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<TreeIcon />}
                  onClick={(e) => {
                    setViewMenuAnchorEl(e.target);
                  }}
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
              </Stack>
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
