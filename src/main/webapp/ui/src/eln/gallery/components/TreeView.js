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
import { useGallerySelection, GallerySelection } from "../useGallerySelection";
import { doNotAwait } from "../../../util/Util";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem, treeItemClasses } from "@mui/x-tree-view/TreeItem";
import { useDroppable, useDraggable, useDndContext } from "@dnd-kit/core";
import Box from "@mui/material/Box";
import { runInAction } from "mobx";
import { useLocalObservable, observer } from "mobx-react-lite";
import { useFileImportDropZone } from "../../../components/useFileImportDragAndDrop";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import PlaceholderLabel from "./PlaceholderLabel";
import { Optional } from "../../../util/optional";
import LoadMoreButton from "./LoadMoreButton";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";
import usePrimaryAction from "../primaryActionHooks";
import { useFolderOpen } from "./OpenFolderProvider";

const StyledTreeItem = styled(TreeItem)(({ theme }) => ({
  [`.${treeItemClasses.content}`]: {
    [`&.${treeItemClasses.selected}`]: {
      backgroundColor: window.matchMedia("(prefers-contrast: more)").matches
        ? "black"
        : theme.palette.callToAction.main,
      [`&.${treeItemClasses.focused}`]: {
        backgroundColor: window.matchMedia("(prefers-contrast: more)").matches
          ? "black"
          : theme.palette.callToAction.main,
      },
      [`& .${treeItemClasses.label}`]: {
        color: "white",
      },
    },
  },
}));

type TreeItemContentArgs = {|
  file: GalleryFile,
  path: $ReadOnlyArray<GalleryFile>,
  section: GallerySection,
  treeViewItemIdMap: Map<string, GalleryFile>,
  refreshListing: () => Promise<void>,
  filter: (GalleryFile) => "hide" | "enabled" | "disabled",
  disableDragAndDrop?: boolean,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
  foldersOnly: boolean,

  /**
   * If true, then the listing is being refreshed and so the tree view should
   * trigger a refresh of its listing.
   *
   * Note that after a refresh is comlete we do not always keep the user's
   * selection (e.g. after duplicating a file) for two reasons:
   *
   *   1. When the root listing refreshes, if there are any selected files that
   *      are not in the new listing then the selection is cleared. This is to
   *      ensure that the selection does not include any now deleted files.
   *      However, this means that if the selection includes a file that is
   *      within a folder then it too will trigger this selection clearing.
   *
   *   2. If the user has just duplicated a folder, then the new folder will
   *      fetch its contents by calling `useGalleryListing`'s `getGalleryFiles`
   *      which triggers a clearing of the selection. All of the other folders
   *      will call their `refreshListing` functions but we have to call
   *      `getGalleryFiles` to get the new folder's contents in the first
   *      instance.
   */
  refeshing: boolean,
|};

const TreeItemContent: ComponentType<TreeItemContentArgs> = observer(
  ({
    path,
    file,
    section,
    treeViewItemIdMap,
    refreshListing,
    filter,
    disableDragAndDrop,
    sortOrder,
    orderBy,
    foldersOnly,
    refeshing,
  }: TreeItemContentArgs): Node => {
    const initialLocation = React.useMemo(
      () => ({ tag: "section", section }),
      [section]
    );
    const { galleryListing, refreshListing: refreshingThisListing } =
      useGalleryListing({
        initialLocation,
        searchTerm: "",
        path: [...path, file],
        orderBy,
        sortOrder,
        foldersOnly,
      });

    React.useEffect(() => {
      /*
       * Note that if the user has just deleted this folder, then refreshing
       * the listing will fail. The logic in `useGalleryListing` will ignore
       * the error and then this tree node will be removed when the parent
       * folder is done refreshing.
       */
      if (refeshing) void refreshingThisListing();
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - refreshingThisListing will not meaningfully change
       */
    }, [refeshing]);

    React.useEffect(() => {
      FetchingData.getSuccessValue(galleryListing).do((listing) => {
        if (listing.tag === "empty") return;
        runInAction(() => {
          for (const f of listing.list)
            treeViewItemIdMap.set(f.treeViewItemId, f);
        });
      });
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - treeViewItemId will not change as it is mobx observable that gets mutated
       */
    }, [galleryListing]);

    return FetchingData.match(galleryListing, {
      /*
       * nothing is shown whilst loading otherwise the UI ends up being quite
       * janky when there ends up being nothing in the folder
       */
      loading: () => null,
      error: (error) => <>{error}</>,
      success: (listing) =>
        listing.tag !== "empty" ? (
          <>
            {listing.list.map((f, i) =>
              filter(f) !== "hide" ? (
                // eslint-disable-next-line no-use-before-define -- CustomTreeItem and TreeItemContent are mutually recursive
                <CustomTreeItem
                  file={f}
                  index={i}
                  path={[...path, file]}
                  section={section}
                  key={idToString(f.id)}
                  treeViewItemIdMap={treeViewItemIdMap}
                  refreshListing={refreshListing}
                  filter={filter}
                  disableDragAndDrop={disableDragAndDrop}
                  sortOrder={sortOrder}
                  orderBy={orderBy}
                  foldersOnly={foldersOnly}
                  disabled={filter(f) === "disabled"}
                  refeshing={refeshing}
                />
              ) : null
            )}
            {listing.loadMore
              .map((loadMore) => (
                <LoadMoreButton key={null} onClick={loadMore} />
              ))
              .orElse(null)}
          </>
        ) : null,
    });
  }
);

const CustomTreeItem = observer(
  ({
    file,
    index,
    path,
    section,
    treeViewItemIdMap,
    refreshListing,
    filter,
    disableDragAndDrop,
    orderBy,
    sortOrder,
    foldersOnly,
    disabled,
    refeshing,
  }: {|
    file: GalleryFile,
    index: number,
    path: $ReadOnlyArray<GalleryFile>,
    section: GallerySection,
    treeViewItemIdMap: Map<string, GalleryFile>,
    refreshListing: () => Promise<void>,
    filter: (GalleryFile) => "hide" | "enabled" | "disabled",
    disableDragAndDrop?: boolean,
    orderBy: "name" | "modificationDate",
    sortOrder: "DESC" | "ASC",
    foldersOnly: boolean,
    disabled: boolean,
    refeshing: boolean,
  |}) => {
    const { uploadFiles } = useGalleryActions();
    const { onDragEnter, onDragOver, onDragLeave, onDrop, over } =
      useFileImportDropZone({
        onDrop: doNotAwait(async (files) => {
          await uploadFiles(file.id, files);
          void refreshListing();
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
    } = useDraggable({
      disabled: disableDragAndDrop,
      id: file.id,
      data: {
        fileBeingMoved: file,
      },
    });
    const dndContext = useDndContext();
    const [dndDebounce, setDndDebounce] = React.useState<null | TimeoutID>(
      null
    );
    const dndInProgress = Boolean(dndContext.active);

    const normalBorder = `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 99%)`;
    const dropStyle: { [string]: string | number } = {};
    if (dndInProgress && file.isFolder) {
      if (isOver) {
        dropStyle.borderColor = SELECTED_OR_FOCUS_BORDER;
      } else {
        dropStyle.border = "2px solid white";
        dropStyle.animation = "drop 2s linear infinite";
      }
    } else {
      dropStyle.border = normalBorder;
    }
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
        <StyledTreeItem
          itemId={file.treeViewItemId}
          disabled={disabled}
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
          onKeyDown={listeners?.onKeyDown}
          {...attributes}
          style={{
            ...dropStyle,
            ...fileUploadDropping,
            borderRadius: "4px",
          }}
        >
          {file.isFolder && (
            <TreeItemContent
              file={file}
              path={path}
              section={section}
              treeViewItemIdMap={treeViewItemIdMap}
              refreshListing={refreshListing}
              filter={filter}
              disableDragAndDrop={disableDragAndDrop}
              sortOrder={sortOrder}
              orderBy={orderBy}
              foldersOnly={foldersOnly}
              refeshing={refeshing}
            />
          )}
        </StyledTreeItem>
      </Box>
    );
  }
);

type TreeViewArgs = {|
  /**
   * The listing of files to display in the tree view. This component takes the
   * whole FetchingData object so that it can display the loading and error
   * states and doesn't lose the expanded state when the listing is refreshed.
   */
  listing:
    | {| tag: "empty", reason: string, refreshing: boolean |}
    | {|
        tag: "list",
        list: $ReadOnlyArray<GalleryFile>,
        totalHits: number,
        loadMore: Optional<() => Promise<void>>,
        refreshing: boolean,
      |},
  path: $ReadOnlyArray<GalleryFile>,
  selectedSection: GallerySection,
  refreshListing: () => Promise<void>,
  filter?: (GalleryFile) => "hide" | "enabled" | "disabled",
  disableDragAndDrop?: boolean,
  sortOrder: "DESC" | "ASC",
  orderBy: "name" | "modificationDate",
  foldersOnly?: boolean,
|};

const TreeView = ({
  listing,
  path,
  selectedSection,
  refreshListing,
  filter = () => "enabled",
  disableDragAndDrop,
  sortOrder,
  orderBy,
  foldersOnly = false,
}: TreeViewArgs) => {
  const { addAlert } = React.useContext(AlertContext);
  const selection = useGallerySelection();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreview } = useAsposePreview();
  const primaryAction = usePrimaryAction();
  const { openFolder } = useFolderOpen();

  const [expandedItems, setExpandedItems] = React.useState<
    $ReadOnlyArray<GalleryFile["id"]>
  >([]);

  /*
   * Maps the item id used by the tree nodes to the actual file object. This is
   * used to determine the file object that the user has selected in the tree.
   */
  const treeViewItemIdMap = useLocalObservable(() => {
    const map = new Map<string, GalleryFile>();
    if (listing.tag === "empty") return map;
    for (const file of listing.list) map.set(file.treeViewItemId, file);
    return map;
  });

  React.useEffect(() => {
    if (listing.tag === "empty") return;
    runInAction(() => {
      for (const f of listing.list) treeViewItemIdMap.set(f.treeViewItemId, f);
    });
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - treeViewItemId will not change as it is mobx observable that gets mutated
     */
  }, [listing]);

  /*
   * TreeView does not like focussed nodes being removed, which is what happens
   * when a drag-and-drop operation concludes. As such, we completely unmount
   * the TreeView component when the listing is refreshed, and remount it when
   * the listing is done refreshing. This is a bit of a hack, but it works.
   */
  if (listing.refreshing) return null;

  if (
    listing.tag === "empty" ||
    listing.list.every((file) => filter(file) === "hide")
  )
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
    <SimpleTreeView
      role="region"
      aria-label="tree view of files"
      expandedItems={expandedItems}
      onExpandedItemsChange={(_event, nodeIds) => {
        setExpandedItems(nodeIds);
      }}
      selectedItems={selection
        .asSet()
        .map((file) => file.treeViewItemId)
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
          MapUtils.get(treeViewItemIdMap, itemId).do((file) => {
            if (selection.includes(file)) {
              selection.remove(file);
            } else {
              selection.append(file);
            }
          });
        } else {
          // on double click, try and figure out what the user would want
          // to do with a file of this type based on what services are
          // configured
          if (event.detail > 1) {
            MapUtils.get(treeViewItemIdMap, itemId).do((file) => {
              primaryAction(file).do((action) => {
                if (action.tag === "open") {
                  openFolder(file);
                  return;
                }
                if (action.tag === "image") {
                  void action.downloadHref().then((url) => {
                    openImagePreview(url);
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
                  void action.downloadHref().then((url) => {
                    openPdfPreview(url);
                  });
                  return;
                }
                if (action.tag === "aspose") {
                  void openAsposePreview(file);
                }
              });
            });
          }
          if (selected) {
            MapUtils.get(treeViewItemIdMap, itemId).do((file) => {
              /*
               * If the user is just opening or closing the node that is
               * already selected, then this event handler will have been
               * called twice with the `itemId` of that file: first with
               * `selected` set to false (which will have been dropped above)
               * and secondly with `selected` set to true. If the selection is
               * already that one file then there is no need to clear and reset
               * that selection as it will only result in unnecessary
               * re-renders and a slower response time to actually
               * opening/closing the tree node.
               */
              if (selection.size === 1 && selection.includes(file)) return;
              selection.clear();
              selection.append(file);
            });
          }
        }
      }}
    >
      {/*
       * We wrap the tree in a GallerySelection so that changes to the listings
       * of each folder do not change the selection of the whole tree which is
       * managed by the `selectedItems` prop above.
       */}
      <GallerySelection>
        {listing.list.map((file, index) =>
          filter(file) ? (
            <CustomTreeItem
              index={index}
              file={file}
              path={path}
              key={idToString(file.id)}
              section={selectedSection}
              treeViewItemIdMap={treeViewItemIdMap}
              refreshListing={refreshListing}
              filter={filter}
              disableDragAndDrop={disableDragAndDrop}
              sortOrder={sortOrder}
              orderBy={orderBy}
              foldersOnly={foldersOnly}
              disabled={filter(file) === "disabled"}
              refeshing={listing.refreshing}
            />
          ) : null
        )}
      </GallerySelection>
      {listing.loadMore
        .map((loadMore) => <LoadMoreButton key={null} onClick={loadMore} />)
        .orElse(null)}
    </SimpleTreeView>
  );
};

/**
 * The hierarchical listing of files and folders can be viewed as tree, making
 * it easy to drag-and-drop files between folders and to operate on files in
 * different folders.
 */
export default (observer(TreeView): ComponentType<TreeViewArgs>);
