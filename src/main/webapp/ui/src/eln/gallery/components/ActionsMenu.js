//@flow

import React, { type Node, type ComponentType } from "react";
import ChecklistIcon from "@mui/icons-material/Checklist";
import Button from "@mui/material/Button";
import { COLOR } from "../common";
import AddToPhotosIcon from "@mui/icons-material/AddToPhotos";
import { styled } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "./NewMenuItem";
import OpenWithIcon from "@mui/icons-material/OpenWith";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import FileDownloadIcon from "@mui/icons-material/FileDownload";
import AcUnitIcon from "@mui/icons-material/AcUnit";
import ShareIcon from "@mui/icons-material/Share";
import GroupIcon from "@mui/icons-material/Group";
import CropIcon from "@mui/icons-material/Crop";
import { observer } from "mobx-react-lite";
import { useGalleryActions } from "../useGalleryActions";
import { useGallerySelection } from "../useGallerySelection";

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

type ActionsMenuArgs = {|
  refreshListing: () => void,
|};

function ActionsMenu({ refreshListing }: ActionsMenuArgs): Node {
  const [actionsMenuAnchorEl, setActionsMenuAnchorEl] = React.useState(null);
  const { deleteFiles } = useGalleryActions();
  const selection = useGallerySelection();

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        aria-haspopup="menu"
        startIcon={<ChecklistIcon />}
        onClick={(e) => {
          setActionsMenuAnchorEl(e.target);
        }}
      >
        Actions
      </Button>
      <StyledMenu
        open={Boolean(actionsMenuAnchorEl)}
        anchorEl={actionsMenuAnchorEl}
        onClose={() => setActionsMenuAnchorEl(null)}
        MenuListProps={{
          disablePadding: true,
        }}
      >
        <NewMenuItem
          title="Duplicate"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<AddToPhotosIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Move"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<OpenWithIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Rename"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DriveFileRenameOutlineIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Delete"
          subheader={selection.isEmpty() ? "Nothing selected." : ""}
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<DeleteOutlineOutlinedIcon />}
          onClick={() => {
            void deleteFiles(selection.asSet()).then(() => {
              refreshListing();
              setActionsMenuAnchorEl(null);
            });
          }}
          compact
          disabled={selection.isEmpty()}
        />
        <NewMenuItem
          title="Export"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<FileDownloadIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Move to iRODS"
          subheader=""
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          // TODO: iRODS logo
          avatar={<AcUnitIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
        />
        <NewMenuItem
          title="Edit"
          subheader="Only images can be edited in place."
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<CropIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled
        />
        <NewMenuItem
          title="Publish"
          subheader="Only PDFs can be published."
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<ShareIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled
        />
        <NewMenuItem
          title="Share"
          subheader="Only snippets can be shared."
          backgroundColor={COLOR.background}
          foregroundColor={COLOR.contrastText}
          avatar={<GroupIcon />}
          onClick={() => {
            setActionsMenuAnchorEl(null);
          }}
          compact
          disabled
        />
      </StyledMenu>
    </>
  );
}

export default (observer(ActionsMenu): ComponentType<ActionsMenuArgs>);
