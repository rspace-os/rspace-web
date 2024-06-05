//@flow

import React, { type Node } from "react";
import Box from "@mui/material/Box";
import Drawer from "@mui/material/Drawer";
import { styled } from "@mui/material/styles";
import { COLOR, gallerySectionLabel } from "../common";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { darken } from "@mui/system";
import { FontAwesomeIcon as FaIcon } from "@fortawesome/react-fontawesome";
import ChemistryIcon from "../chemistryIcon";
import Divider from "@mui/material/Divider";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faImage,
  faFilm,
  faFile,
  faFileInvoice,
  faDatabase,
  faShapes,
  faCircleDown,
  faVolumeLow,
} from "@fortawesome/free-solid-svg-icons";
import { faNoteSticky } from "@fortawesome/free-regular-svg-icons";
import CreateNewFolderIcon from "@mui/icons-material/CreateNewFolder";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import AddIcon from "@mui/icons-material/Add";
import ArgosNewMenuItem from "../../../eln-dmp-integration/Argos/ArgosNewMenuItem";
import DMPOnlineNewMenuItem from "../../../eln-dmp-integration/DMPOnline/DMPOnlineNewMenuItem";
import DMPToolNewMenuItem from "../../../eln-dmp-integration/DMPTool/DMPToolNewMenuItem";
import NewMenuItem from "./NewMenuItem";
import useGalleryActions from "../useGalleryActions";
import { type GalleryFile } from "../useGalleryListing";
import * as FetchingData from "../../../util/fetchingData";
library.add(faImage);
library.add(faFilm);
library.add(faFile);
library.add(faFileInvoice);
library.add(faDatabase);
library.add(faShapes);
library.add(faNoteSticky);
library.add(faCircleDown);
library.add(faVolumeLow);

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(-4px, 4px) !important",
        }
      : {}),
  },
}));

const AddButton = styled(({ drawerOpen, ...props }) => (
  <Button
    {...props}
    fullWidth
    style={{ minWidth: "unset" }}
    startIcon={
      <AddIcon
        style={{
          transition: window.matchMedia("(prefers-reduced-motion: reduce)")
            .matches
            ? "none"
            : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
          transform: drawerOpen ? "translateX(0px)" : "translateX(22px)",
        }}
      />
    }
  >
    <div
      style={{
        transition: window.matchMedia("(prefers-reduced-motion: reduce)")
          .matches
          ? "none"
          : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
        opacity: drawerOpen ? 1 : 0,
        transform: drawerOpen ? "unset" : "translateX(20px)",
      }}
    >
      New
    </div>
  </Button>
))(() => ({
  color: `hsl(${COLOR.contrastText.hue}deg, ${COLOR.contrastText.saturation}%, ${COLOR.contrastText.lightness}%, 100%)`,
}));

const CustomDrawer = styled(Drawer)(({ open }) => ({
  width: open ? "200px" : "64px",
  transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? "none"
    : "width .25s cubic-bezier(0.4, 0, 0.2, 1)",
  "& .MuiPaper-root": {
    position: "relative",
    overflowX: "hidden",
  },
}));

const UploadMenuItem = ({
  path,
  parentId,
  onUploadComplete,
}: {|
  path: $ReadOnlyArray<GalleryFile>,
  parentId: number,
  onUploadComplete: () => void,
|}) => {
  const { uploadFiles } = useGalleryActions({ path, parentId });
  return (
    <label>
      <NewMenuItem
        title="Upload Files"
        avatar={<UploadFileIcon />}
        subheader="Choose one or more files to upload"
        backgroundColor={COLOR.background}
        foregroundColor={COLOR.contrastText}
      />
      <input
        accept="*"
        hidden
        multiple
        onChange={({ target: { files } }) => {
          void uploadFiles([...files]).then(() => {
            onUploadComplete();
          });
        }}
        type="file"
      />
    </label>
  );
};

const SelectedDrawerTabIndicator = styled(({ className }) => (
  <div className={className}></div>
))(({ verticalPosition }) => ({
  width: "198px",
  height: "43px",
  backgroundColor: window.matchMedia("(prefers-contrast: more)").matches
    ? "black"
    : `hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, ${COLOR.background.lightness}%)`,
  position: "absolute",
  top: verticalPosition,
  transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ? "none"
    : "top 400ms cubic-bezier(0.4, 0, 0.2, 1) 0ms",
}));

const DrawerTab = styled(
  ({ icon, label, index, className, selected, onClick }) => (
    <ListItem disablePadding className={className}>
      <ListItemButton selected={selected} onClick={onClick}>
        <ListItemIcon>{icon}</ListItemIcon>
        <ListItemText
          primary={label}
          sx={{ transitionDelay: `${(index + 1) * 0.02}s !important` }}
        />
      </ListItemButton>
    </ListItem>
  )
)(({ drawerOpen }) => ({
  position: "static",
  "& .MuiListItemText-root": {
    transition: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "none"
      : "all .2s cubic-bezier(0.4, 0, 0.2, 1)",
    opacity: drawerOpen ? 1 : 0,
    transform: drawerOpen ? "unset" : "translateX(-20px)",
    textTransform: "uppercase",
  },
  "& .MuiListItemButton-root": {
    "&:hover": {
      backgroundColor: darken(
        `hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, 100%)`,
        0.05
      ),
    },
    "&.Mui-selected": {
      backgroundColor: "unset",
      "&:hover": {
        backgroundColor: "unset",
      },
    },
  },
}));

export default function GallerySidebar({
  selectedSection,
  setSelectedSection,
  drawerOpen,
  path,
  parentId,
  refreshListing,
}: {|
  selectedSection: string,
  setSelectedSection: (string) => void,
  drawerOpen: boolean,
  path: $ReadOnlyArray<GalleryFile>,
  parentId: FetchingData.Fetched<number>,
  refreshListing: () => void,
|}): Node {
  const [selectedIndicatorOffset, setSelectedIndicatorOffset] =
    React.useState(8);
  const [newMenuAnchorEl, setNewMenuAnchorEl] = React.useState(null);

  return (
    <CustomDrawer
      open={drawerOpen}
      anchor="left"
      variant="permanent"
      aria-label="gallery sections drawer"
    >
      <Box width="100%" p={1.5}>
        <AddButton
          onClick={(e) => setNewMenuAnchorEl(e.currentTarget)}
          drawerOpen={drawerOpen}
        />
        <StyledMenu
          open={Boolean(newMenuAnchorEl)}
          anchorEl={newMenuAnchorEl}
          onClose={() => setNewMenuAnchorEl(null)}
          MenuListProps={{
            disablePadding: true,
          }}
        >
          {FetchingData.getSuccessValue(parentId)
            .map((pId) => (
              <UploadMenuItem
                key={null}
                path={path}
                parentId={pId}
                onUploadComplete={() => {
                  refreshListing();
                  setNewMenuAnchorEl(null);
                }}
              />
            ))
            .orElse(null)}
          <NewMenuItem
            title="New Folder"
            avatar={<CreateNewFolderIcon />}
            subheader="Create an empty folder"
            backgroundColor={COLOR.background}
            foregroundColor={COLOR.contrastText}
          />
          <Divider variant="middle" textAlign="left">
            DMPs
          </Divider>
          <ArgosNewMenuItem />
          <DMPOnlineNewMenuItem />
          <DMPToolNewMenuItem />
        </StyledMenu>
      </Box>
      <Divider />
      <Box
        sx={{
          overflowY: "auto",
          overflowX: "hidden",
          position: "relative",
        }}
      >
        <SelectedDrawerTabIndicator
          verticalPosition={selectedIndicatorOffset}
        />
        <List sx={{ position: "static" }} role="navigation">
          <DrawerTab
            label={gallerySectionLabel.Images}
            icon={<FaIcon icon="image" />}
            index={0}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Images"}
            onClick={(event) => {
              setSelectedSection("Images");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Audios}
            icon={<FaIcon icon="volume-low" />}
            index={1}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Audios"}
            onClick={(event) => {
              setSelectedSection("Audios");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Videos}
            icon={<FaIcon icon="film" />}
            index={2}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Videos"}
            onClick={(event) => {
              setSelectedSection("Videos");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Documents}
            icon={<FaIcon icon="file" />}
            index={3}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Documents"}
            onClick={(event) => {
              setSelectedSection("Documents");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Chemistry}
            icon={<ChemistryIcon />}
            index={4}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Chemistry"}
            onClick={(event) => {
              setSelectedSection("Chemistry");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.DMPs}
            icon={<FaIcon icon="file-invoice" />}
            index={5}
            drawerOpen={drawerOpen}
            selected={selectedSection === "DMPs"}
            onClick={(event) => {
              setSelectedSection("DMPs");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Snippets}
            icon={<FaIcon icon="fa-regular fa-note-sticky" />}
            index={7}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Snippets"}
            onClick={(event) => {
              setSelectedSection("Snippets");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
          <DrawerTab
            label={gallerySectionLabel.Miscellaneous}
            icon={<FaIcon icon="shapes" />}
            index={8}
            drawerOpen={drawerOpen}
            selected={selectedSection === "Miscellaneous"}
            onClick={(event) => {
              setSelectedSection("Miscellaneous");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
        </List>
        <Divider />
        <List sx={{ position: "static" }} role="navigation">
          <DrawerTab
            label={gallerySectionLabel.PdfDocuments}
            icon={<FaIcon icon="fa-circle-down" />}
            index={9}
            drawerOpen={drawerOpen}
            selected={selectedSection === "PdfDocuments"}
            onClick={(event) => {
              setSelectedSection("PdfDocuments");
              setSelectedIndicatorOffset(event.currentTarget.offsetTop);
            }}
          />
        </List>
      </Box>
    </CustomDrawer>
  );
}
