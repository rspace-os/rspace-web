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
  COLOR,
  SELECTED_OR_FOCUS_BORDER,
  SELECTED_OR_FOCUS_BLUE,
  type GallerySection,
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
import * as ArrayUtils from "../../../util/ArrayUtils";
import Link from "@mui/material/Link";
import { Link as ReactRouterLink } from "react-router-dom";
import useOneDimensionalRovingTabIndex from "../../../components/useOneDimensionalRovingTabIndex";
import Box from "@mui/material/Box";
import Fab from "@mui/material/Fab";
import Divider from "@mui/material/Divider";
import SwipeableDrawer from "@mui/material/SwipeableDrawer";
import CardContent from "@mui/material/CardContent";
import { grey } from "@mui/material/colors";
import { Optional } from "../../../util/optional";
import DescriptionList from "../../../components/DescriptionList";
import NoValue from "../../../components/NoValue";
import { formatFileSize } from "../../../util/files";
import Result from "../../../util/result";

const CLOSED_MOBILE_INFO_PANEL_HEIGHT = 80;

export const MobileInfoPanel: typeof SwipeableDrawer = styled(SwipeableDrawer)(
  () => ({
    "& .MuiPaper-root": {
      height: `calc(90% - ${CLOSED_MOBILE_INFO_PANEL_HEIGHT}px)`,
      overflow: "visible",
    },
  })
);

export const MobileInfoPanelHeader: ComponentType<{|
  children: Node,
|}> = styled(Box)(({ theme }) => ({
  position: "absolute",
  top: -CLOSED_MOBILE_INFO_PANEL_HEIGHT,
  height: CLOSED_MOBILE_INFO_PANEL_HEIGHT + 1,
  visibility: "visible",
  right: 0,
  left: 0,
  backgroundColor: "white",
  borderTopLeftRadius: theme.spacing(2),
  borderTopRightRadius: theme.spacing(2),
  boxShadow: "hsl(280deg 66% 10% / 5%) 0px -8px 8px 2px",
}));

export const Puller: ComponentType<{|
  onClick: () => void,
  tabIndex?: number,
  role?: string,
|}> = styled("div")(() => ({
  width: 30,
  height: 6,
  backgroundColor: grey[300],
  borderRadius: 3,
  position: "absolute",
  top: 8,
  left: "calc(50% - 15px)",
}));

export const InfoPanelContent = ({ file }: { file: GalleryFile }): Node => {
  return (
    <Stack>
      <DescriptionList
        rightAlignDds
        content={[
          {
            label: "Global ID",
            value: file.globalId,
          },
          {
            label: "Owner",
            value: file.ownerName,
          },
          {
            label: "Description",
            value: file.description.match({
              missing: () => "Unknown description",
              empty: () => <NoValue label="No description" />,
              present: (d) => d,
            }),
          },
        ]}
      />
      <Box component="section" sx={{ mt: 0.5 }}>
        <Typography variant="h6" component="h4">
          Details
        </Typography>
        <DescriptionList
          rightAlignDds
          content={[
            {
              label: "Type",
              value: file.type,
            },
            {
              label: "Size",
              value: formatFileSize(file.size),
            },
            {
              label: "Created",
              value: file.creationDate.toLocaleString(),
            },
            {
              label: "Modified",
              value: file.modificationDate.toLocaleString(),
            },
            {
              label: "Version",
              value: file.version,
            },
          ]}
        />
      </Box>
    </Stack>
  );
};

export const InfoPanelMultipleContent = (): Node => {
  const selection = useGallerySelection();
  const sortedByCreated = selection
    .asSet()
    .toArray(
      (fileA, fileB) =>
        fileA.creationDate.getTime() - fileB.creationDate.getTime()
    );
  const sortedByModified = selection
    .asSet()
    .toArray(
      (fileA, fileB) =>
        fileA.modificationDate.getTime() - fileB.modificationDate.getTime()
    );
  return (
    <DescriptionList
      rightAlignDds
      content={[
        {
          label: "Total size",
          value: formatFileSize(
            selection.asSet().reduce((sum, file) => sum + file.size, 0)
          ),
        },
        ...Result.lift2<GalleryFile, GalleryFile, _>(
          (oldestFile, newestFile) => [
            {
              label: "Created",
              value: (
                <>
                  {oldestFile.creationDate.toLocaleDateString()} &ndash;{" "}
                  {newestFile.creationDate.toLocaleDateString()}
                </>
              ),
            },
          ]
        )(
          ArrayUtils.head(sortedByCreated),
          ArrayUtils.last(sortedByCreated)
        ).orElse([]),
        ...Result.lift2<GalleryFile, GalleryFile, _>(
          (oldestFile, newestFile) => [
            {
              label: "Modified",
              value: (
                <>
                  {oldestFile.modificationDate.toLocaleDateString()} &ndash;{" "}
                  {newestFile.modificationDate.toLocaleDateString()}
                </>
              ),
            },
          ]
        )(
          ArrayUtils.head(sortedByModified),
          ArrayUtils.last(sortedByModified)
        ).orElse(
          ([]: Array<{|
            label: string,
            value: Node,
            below?: boolean,
            reducedPadding?: boolean,
          |}>)
        ),
      ]}
    />
  );
};
