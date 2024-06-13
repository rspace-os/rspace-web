//@flow

import React, { type Node, Children, type ElementConfig } from "react";
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
import {
  useGalleryListing,
  useGalleryActions,
  type GalleryFile,
  type FolderId,
} from "../useGallery";
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
import { observable, runInAction } from "mobx";
import { useLocalObservable, observer } from "mobx-react-lite";

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
  ({ className, folderId, path, refreshListing, onDrop }) => {
    const { uploadFiles } = useGalleryActions();
    const [over, setOver] = React.useState(0);
    return (
      <Card
        onDragOver={(e) => {
          e.preventDefault();
          e.stopPropagation();
        }}
        onDragEnter={(e) => {
          e.preventDefault();
          setOver((x) => x + 1);
        }}
        onDragLeave={(e) => {
          e.preventDefault();
          setOver((x) => x - 1);
        }}
        onDrop={doNotAwait(async (e) => {
          onDrop();
          setOver(0);
          e.preventDefault();
          e.stopPropagation();
          const files = [];

          if (e.dataTransfer.items) {
            // Use DataTransferItemList interface to access the file(s)
            [...e.dataTransfer.items].forEach((item) => {
              // If dropped items aren't files, reject them
              if (item.kind === "file") {
                files.push(item.getAsFile());
              }
            });
          } else {
            // Use DataTransfer interface to access the file(s)
            [...e.dataTransfer.files].forEach((file) => {
              files.push(file);
            });
          }

          const fId = FetchingData.getSuccessValue<FolderId>(
            folderId
          ).orElseGet(() => {
            throw new Error("Unknown folder id");
          });
          await uploadFiles(path, fId, files);
          refreshListing();
        })}
        className={className}
        sx={
          over > 0
            ? {
                borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
                backgroundColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%, 15%)`,
                color: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%) !important`,
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

const TreeItemContent = ({
  path,
  file,
  section,
  draggingIds,
}: {|
  file: GalleryFile,
  path: $ReadOnlyArray<GalleryFile>,
  section: string,
  draggingIds: $ReadOnlyArray<GalleryFile["id"]>,
|}): Node => {
  const { galleryListing } = useGalleryListing({
    section,
    searchTerm: "",
    path: [...path, file],
  });
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
              key={f.id}
              draggingIds={draggingIds}
            />
          ))
        : null,
  });
};

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
  folderName,
  path,
  selectedSection,
}: {|
  label: string,
  onClick: () => void,
  folderName: string,
  path: $ReadOnlyArray<GalleryFile>,
  selectedSection: string,
|}) => {
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: `/${[selectedSection, ...path.map(({ name }) => name), folderName].join(
      "/"
    )}/`,
    disabled: false,
    data: {
      path,
      name: folderName,
    },
  });
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: `2px solid hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
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

const CustomTreeItem = ({
  file,
  index,
  path,
  section,
  draggingIds,
}: {|
  file: GalleryFile,
  index: number,
  path: $ReadOnlyArray<GalleryFile>,
  section: string,
  draggingIds: $ReadOnlyArray<GalleryFile["id"]>,
|}) => {
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: file.id,
    disabled: !/Folder/.test(file.type),
    data: {
      path: file.path,
      name: file.name,
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
    data: { draggingIds },
  });
  const dndContext = useDndContext();

  const dragStyle: { [string]: string | number } = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
        zIndex: 1, // just needs to be rendered above Nodes later in the DOM
        position: "relative",
        boxShadow: `hsl(0deg, 100%, 20%, 20%) 0px 2px 8px 0px`,
        maxWidth: "max-content",
      }
    : {};
  const dropStyle: { [string]: string | number } = isOver
    ? {
        border: `2px solid hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
      }
    : {
        border: "2px solid transparent",
      };
  const inGroupBeingDraggedStyle: { [string]: string | number } =
    (dndContext.active?.data.current?.draggingIds ?? []).includes(
      `${file.id}`
    ) && dndContext.active?.id !== file.id
      ? {
          opacity: 0.2,
        }
      : {};

  return (
    <Box
      sx={{
        transitionDelay: `${(index + 1) * 0.04}s !important`,
      }}
    >
      <TreeItem
        itemId={`${file.id}`}
        label={
          <Box sx={{ display: "flex" }}>
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
          borderRadius: "4px",
        }}
      >
        {/Folder/.test(file.type) && (
          <TreeItemContent
            file={file}
            path={path}
            section={section}
            draggingIds={draggingIds}
          />
        )}
      </TreeItem>
    </Box>
  );
};

const GridView = observer(
  ({
    listing,
  }: {|
    listing:
      | {| tag: "empty", reason: string |}
      | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |},
  |}) => {
    // $FlowExpectedError[prop-missing] Difficult to get this library type right
    const selectedFiles = useLocalObservable(() => observable.set([]));

    /*
     * When shift-clicking, all of the items in the grid between the tapped
     * item and the last item to be tapped without shift being held should be
     * selected. This coordinate is that last item to be tapped without shift.
     */
    const [shiftSelectCoord, setShiftSelectCoord] = React.useState<null | {|
      x: number,
      y: number,
    |}>(null);
    const viewportDimensions = useViewportDimensions();
    const cardWidth = {
      xs: 6,
      sm: 4,
      md: 3,
      lg: 2,
      xl: 2,
    };
    const cols = 12 / cardWidth[viewportDimensions.viewportSize];

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
      <Grid container spacing={2}>
        {listing.list.map((file, index) => (
          <FileCard
            selected={selectedFiles.has(file.id)}
            file={file}
            key={file.id}
            index={index}
            onClick={(e) => {
              if (e.shiftKey) {
                if (!shiftSelectCoord) return;
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
                    coord.x >= Math.min(tappedCoord.x, shiftSelectCoord.x) &&
                    coord.x <= Math.max(tappedCoord.x, shiftSelectCoord.x) &&
                    coord.y >= Math.min(tappedCoord.y, shiftSelectCoord.y) &&
                    coord.y <= Math.max(tappedCoord.y, shiftSelectCoord.y)
                  );
                });
                runInAction(() => {
                  selectedFiles.clear();
                  toSelect.forEach(({ id }) => {
                    selectedFiles.add(id);
                  });
                });
              } else if (e.ctrlKey || e.metaKey) {
                if (selectedFiles.has(file.id)) {
                  runInAction(() => {
                    selectedFiles.delete(file.id);
                  });
                } else {
                  runInAction(() => {
                    selectedFiles.add(file.id);
                  });
                }
              } else {
                runInAction(() => {
                  selectedFiles.clear();
                  selectedFiles.add(file.id);
                });
                setShiftSelectCoord({
                  x: index % cols,
                  y: Math.floor(index / cols),
                });
              }
            }}
            draggingIds={[...selectedFiles]}
          />
        ))}
      </Grid>
    );
  }
);

const FileCard = styled(
  ({ file, className, selected, index, onClick, draggingIds }) => {
    const { uploadFiles } = useGalleryActions();
    const [over, setOver] = React.useState(0);
    const { setNodeRef: setDropRef, isOver } = useDroppable({
      id: file.id,
      disabled: !/Folder/.test(file.type),
      data: {
        path: file.path,
        name: file.name,
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
        draggingIds,
      },
    });
    const dndContext = useDndContext();

    const dragStyle: { [string]: string | number } = transform
      ? {
          transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
          zIndex: 1, // just needs to be rendered above Nodes later in the DOM
          position: "relative",
          boxShadow: `hsl(0deg, 100%, 20%, 20%) 0px 2px 8px 0px`,
        }
      : {};
    const dropStyle: { [string]: string | number } = isOver
      ? {
          borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
        }
      : {};
    const inGroupBeingDraggedStyle: { [string]: string | number } =
      (dndContext.active?.data.current?.draggingIds ?? []).includes(file.id) &&
      dndContext.active?.id !== file.id
        ? {
            opacity: 0,
          }
        : {};
    const fileUploadDropping: { [string]: string | number } = over
      ? {
          borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
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
            onDrop={doNotAwait(async (e) => {
              setOver(0);
              e.preventDefault();
              e.stopPropagation();
              const files = [];

              if (e.dataTransfer.items) {
                // Use DataTransferItemList interface to access the file(s)
                [...e.dataTransfer.items].forEach((item) => {
                  // If dropped items aren't files, reject them
                  if (item.kind === "file") {
                    files.push(item.getAsFile());
                  }
                });
              } else {
                // Use DataTransfer interface to access the file(s)
                [...e.dataTransfer.files].forEach((f) => {
                  files.push(f);
                });
              }

              await uploadFiles(file.path, file.id, files);
              /*
               * No need to refresh the listing as the uploaded file has been
               * placed inside a folder into which the user cannot currently
               * see
               */
            })}
            onDragOver={(e) => {
              e.preventDefault();
              e.stopPropagation();
            }}
            onDragEnter={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (/Folder/.test(file.type)) setOver((x) => x + 1);
            }}
            onDragLeave={(e) => {
              e.preventDefault();
              e.stopPropagation();
              if (/Folder/.test(file.type)) setOver((x) => x - 1);
            }}
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
            }}
          >
            <CardActionArea
              role={file.open ? "button" : "radio"}
              aria-checked={selected}
              onClick={(e) => {
                if (file.open) file.open();
                else onClick(e);
              }}
              onDragStart={(e) => {
                /*
                 * This prevents the user from accidentally dragging the
                 * thumbnail image and uploading it by triggering the upload
                 * file drag-and-drop when they mean to drag the FileCard as
                 * part of the within-webpage drag-and-drop to move gallery
                 * files into folders.
                 */
                e.preventDefault();
                e.stopPropagation();
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
)(({ selected }) => ({
  height: "150px",
  ...(selected
    ? {
        border: window.matchMedia("(prefers-contrast: more)").matches
          ? "2px solid black"
          : `2px solid hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
        "&:hover": {
          border: window.matchMedia("(prefers-contrast: more)").matches
            ? "2px solid black !important"
            : `2px solid hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%) !important`,
        },
      }
    : {}),
  borderRadius: "8px",
  boxShadow: selected
    ? "none"
    : `hsl(${COLOR.main.hue} 66% 20% / 20%) 0px 2px 8px 0px`,
}));

const TreeView = ({
  listing,
  path,
  selectedSection,
}: {|
  listing:
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |},
  path: $ReadOnlyArray<GalleryFile>,
  selectedSection: string,
|}) => {
  const [selectedNodes, setSelectedNodes] = React.useState<
    $ReadOnlyArray<GalleryFile["id"]>
  >([]);
  const [expandedItems, setExpandedItems] = React.useState<
    $ReadOnlyArray<GalleryFile["id"]>
  >([]);

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
      selectedItems={selectedNodes}
      onItemSelectionToggle={(event, itemId, selected) => {
        /*
         * It's not possible for us to support shift-clicking in tree view
         * because there's no data structure we can query to find a range of
         * adjacent nodes. There's simply no way for us to get the itemIds of
         * the nodes between two selected nodes; even more so if the user were
         * to attempt to select nodes are different levels of the hierarchy.
         * SimpleTreeView does have a multiSelect mode but attempting to use it
         * just results in console errors.
         */
        if (event.shiftKey) return;
        if (event.ctrlKey || event.metaKey) {
          if (selected) {
            setSelectedNodes([...selectedNodes, itemId]);
          } else {
            setSelectedNodes(selectedNodes.filter((x) => x !== itemId));
          }
        } else if (selected) {
          setSelectedNodes([itemId]);
        } else {
          setSelectedNodes([]);
        }
      }}
    >
      {listing.list.map((file, index) => (
        <CustomTreeItem
          index={index}
          file={file}
          path={path}
          key={file.id}
          section={selectedSection}
          draggingIds={selectedNodes}
        />
      ))}
    </SimpleTreeView>
  );
};

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

export default function GalleryMainPanel({
  selectedSection,
  path,
  clearPath,
  galleryListing,
  folderId,
  refreshListing,
}: {|
  selectedSection: string,
  path: $ReadOnlyArray<GalleryFile>,
  clearPath: () => void,
  galleryListing: FetchingData.Fetched<
    | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |}
  >,
  selectedFile: null | GalleryFile,
  setSelectedFile: (null | GalleryFile) => void,
  folderId: FetchingData.Fetched<FolderId>,
  refreshListing: () => void,
|}): Node {
  const [fileDragAndDrop, setFileDragAndDrop] = React.useState(0);
  const [viewMenuAnchorEl, setViewMenuAnchorEl] = React.useState(null);
  const [viewMode, setViewMode] = React.useState("grid");
  const { moveFilesWithIds } = useGalleryActions();

  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint: {
      delay: 0,
      tolerance: 5,
    },
  });

  return (
    <DialogContent
      aria-live="polite"
      sx={{
        position: "relative",
        ...(fileDragAndDrop > 0
          ? {
              borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
            }
          : {}),
      }}
      onDragOver={(e) => {
        e.preventDefault();
        e.stopPropagation();
      }}
      onDragEnter={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setFileDragAndDrop((x) => x + 1);
      }}
      onDragLeave={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setFileDragAndDrop((x) => x - 1);
      }}
    >
      <DndContext
        sensors={[mouseSensor]}
        onDragEnd={(event) => {
          if (!event.over?.data.current) return;
          void moveFilesWithIds(
            event.active?.data.current?.draggingIds ?? [event.active.id]
          )
            .to({
              target: `/${[
                selectedSection,
                ...event.over.data.current.path.map(({ name }) => name),
                ...(event.over.data.current.name === ""
                  ? []
                  : [event.over.data.current.name]),
              ].join("/")}/`,
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
                  folderName=""
                  selectedSection={selectedSection}
                />
                {path.map((folder) => (
                  <Breadcrumb
                    label={folder.name}
                    key={folder.id}
                    onClick={() => folder.open?.()}
                    folderName={folder.name}
                    path={folder.path}
                    selectedSection={selectedSection}
                  />
                ))}
              </CustomBreadcrumbs>
            </Grid>
            <Grid item sx={{ mt: 0.5 }}>
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
                  }}
                />
                <NewMenuItem
                  title="Tree"
                  subheader="Drag-and-drop files between folders"
                  backgroundColor={COLOR.background}
                  foregroundColor={COLOR.contrastText}
                  avatar={<TreeIcon />}
                  onClick={() => {
                    setViewMode("tree");
                    setViewMenuAnchorEl(null);
                  }}
                />
              </StyledMenu>
            </Grid>
          </Grid>
          <Grid item sx={{ overflowY: "auto", mt: 1 }} flexGrow={1}>
            {viewMode === "tree" &&
              FetchingData.match(galleryListing, {
                loading: () => <></>,
                error: (error) => <>{error}</>,
                success: (listing) => (
                  <TreeView
                    listing={listing}
                    path={path}
                    selectedSection={selectedSection}
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
        <Collapse in={fileDragAndDrop > 0}>
          <ImportDropzone
            folderId={folderId}
            path={path}
            refreshListing={refreshListing}
            onDrop={() => {
              setFileDragAndDrop(0);
            }}
          />
        </Collapse>
      </DndContext>
    </DialogContent>
  );
}
