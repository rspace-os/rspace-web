//@flow

import React, { type Node, type ComponentType } from "react";
import Fade from "@mui/material/Fade";
import {
  COLOR,
  SELECTED_OR_FOCUS_BORDER,
  type GallerySection,
} from "../common";
import { styled } from "@mui/material/styles";
import Avatar from "@mui/material/Avatar";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import * as FetchingData from "../../../util/fetchingData";
import * as MapUtils from "../../../util/MapUtils";
import {
  useGalleryListing,
  type GalleryFile,
  idToString,
} from "../useGalleryListing";
import { useGalleryActions, folderDestination } from "../useGalleryActions";
import { useGallerySelection } from "../useGallerySelection";
import { doNotAwait } from "../../../util/Util";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import { useDroppable, useDraggable, useDndContext } from "@dnd-kit/core";
import Box from "@mui/material/Box";
import Collapse from "@mui/material/Collapse";
import { runInAction } from "mobx";
import { useLocalObservable, observer } from "mobx-react-lite";
import { useFileImportDropZone } from "../../../components/useFileImportDragAndDrop";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import RsSet from "../../../util/set";
import PlaceholderLabel from "./PlaceholderLabel";

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

type TreeItemContentArgs = {|
  file: GalleryFile,
  path: $ReadOnlyArray<GalleryFile>,
  section: GallerySection,
  idMap: Map<string, GalleryFile>,
  refreshListing: () => void,
  filter: (GalleryFile) => boolean,
  disableDragAndDrop?: boolean,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
|};

const TreeItemContent: ComponentType<TreeItemContentArgs> = observer(
  ({
    path,
    file,
    section,
    idMap,
    refreshListing,
    filter,
    disableDragAndDrop,
    sortOrder,
    orderBy,
  }: TreeItemContentArgs): Node => {
    const { galleryListing } = useGalleryListing({
      section,
      searchTerm: "",
      path: [...path, file],
      orderBy,
      sortOrder,
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
          ? listing.list.map((f, i) =>
              filter(f) ? (
                <CustomTreeItem
                  file={f}
                  index={i}
                  path={[...path, file]}
                  section={section}
                  key={idToString(f.id)}
                  idMap={idMap}
                  refreshListing={refreshListing}
                  filter={filter}
                  disableDragAndDrop={disableDragAndDrop}
                  sortOrder={sortOrder}
                  orderBy={orderBy}
                />
              ) : null
            )
          : null,
    });
  }
);

const CustomTreeItem = observer(
  ({
    file,
    index,
    path,
    section,
    idMap,
    refreshListing,
    filter,
    disableDragAndDrop,
    orderBy,
    sortOrder,
  }: {|
    file: GalleryFile,
    index: number,
    path: $ReadOnlyArray<GalleryFile>,
    section: GallerySection,
    idMap: Map<string, GalleryFile>,
    refreshListing: () => void,
    filter: (GalleryFile) => boolean,
    disableDragAndDrop?: boolean,
    orderBy: "name" | "modificationDate",
    sortOrder: "DESC" | "ASC",
  |}) => {
    const { uploadFiles } = useGalleryActions();
    const selection = useGallerySelection();
    const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
      useFileImportDropZone({
        onDrop: doNotAwait(async (files) => {
          await uploadFiles([...file.path, file], file.id, files);
          refreshListing();
        }),
        disabled: !file.isFolder,
      });
    const { setNodeRef: setDropRef, isOver } = useDroppable({
      id: file.id,
      disabled: disableDragAndDrop || !file.isFolder,
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
      disabled: disableDragAndDrop,
      id: file.id,
      data: {
        /*
         * If this `file` is one of the selected files then all of the selected
         * files are to be moved by the drag operation. If it is not included
         * then just move this file.
         */
        filesBeingMoved: selection.includes(file)
          ? selection.asSet()
          : new RsSet([file]),
      },
    });
    const dndContext = useDndContext();
    const dndInProgress = Boolean(dndContext.active);

    const dragStyle: { [string]: string | number } = transform
      ? {
          transform: `translate3d(${transform.x}px, ${transform.y}px, 0) scale(1.1)`,
          zIndex: 1400, // Above the sidebar
          position: "fixed",
          boxShadow: `hsl(${COLOR.main.hue}deg 66% 10% / 20%) 0px 2px 16px 8px`,
          maxWidth: "max-content",
        }
      : {};
    const dropStyle: { [string]: string | number } = isOver
      ? {
          border: SELECTED_OR_FOCUS_BORDER,
        }
      : dndInProgress && file.isFolder
      ? {
          border: "2px solid white",
          animation: "drop 2s linear infinite",
        }
      : {
          border: `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 99%)`,
        };
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
          {file.isFolder && (
            <TreeItemContent
              file={file}
              path={path}
              section={section}
              idMap={idMap}
              refreshListing={refreshListing}
              filter={filter}
              disableDragAndDrop={disableDragAndDrop}
              sortOrder={sortOrder}
              orderBy={orderBy}
            />
          )}
        </TreeItem>
      </Box>
    );
  }
);

type TreeViewArgs = {|
  listing: | {| tag: "empty", reason: string |}
    | {| tag: "list", list: $ReadOnlyArray<GalleryFile> |},
  path: $ReadOnlyArray<GalleryFile>,
  selectedSection: GallerySection,
  refreshListing: () => void,
  filter?: (GalleryFile) => boolean,
  disableDragAndDrop?: boolean,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
|};

const TreeView = ({
  listing,
  path,
  selectedSection,
  refreshListing,
  filter = () => true,
  disableDragAndDrop,
  sortOrder,
  orderBy,
}: TreeViewArgs) => {
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

  if (listing.tag === "empty" || listing.list.filter(filter).length === 0)
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
              {listing.reason ?? "There are no folders."}
            </PlaceholderLabel>
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
      selectedItems={selection
        .asSet()
        .map(({ id }) => idToString(id))
        .toArray()}
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
            if (selection.includes(file)) {
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
      {listing.list.map((file, index) =>
        filter(file) ? (
          <CustomTreeItem
            index={index}
            file={file}
            path={path}
            key={idToString(file.id)}
            section={selectedSection}
            idMap={idMap}
            refreshListing={refreshListing}
            filter={filter}
            disableDragAndDrop={disableDragAndDrop}
            sortOrder={sortOrder}
            orderBy={orderBy}
          />
        ) : null
      )}
    </SimpleTreeView>
  );
};

export default (observer(TreeView): ComponentType<TreeViewArgs>);
