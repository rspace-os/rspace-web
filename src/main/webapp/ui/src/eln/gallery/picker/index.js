//@flow

import Dialog from "@mui/material/Dialog";
import React, { type Node, type ElementConfig, type Ref } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import DialogContent from "@mui/material/DialogContent";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";
import Typography from "@mui/material/Typography";
import Card from "@mui/material/Card";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import SearchIcon from "@mui/icons-material/Search";
import InputAdornment from "@mui/material/InputAdornment";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Drawer from "@mui/material/Drawer";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { FontAwesomeIcon as FaIcon } from "@fortawesome/react-fontawesome";
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
import Divider from "@mui/material/Divider";
import Chip from "@mui/material/Chip";
import createAccentedTheme from "../../../accentedTheme";
import { COLORS as baseThemeColors } from "../../../theme";
import ChemistryIcon from "./chemistryIcon";
import Avatar from "@mui/material/Avatar";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Grow from "@mui/material/Grow";
import useViewportDimensions from "../../../util/useViewportDimensions";
import { darken } from "@mui/system";
import useGalleryListing, { type GalleryFile } from "./useGalleryListing";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import Fade from "@mui/material/Fade";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import CloseIcon from "@mui/icons-material/Close";
import CardActionArea from "@mui/material/CardActionArea";
library.add(faImage);
library.add(faFilm);
library.add(faFile);
library.add(faFileInvoice);
library.add(faDatabase);
library.add(faShapes);
library.add(faNoteSticky);
library.add(faCircleDown);
library.add(faVolumeLow);

const COLOR = {
  main: {
    hue: 190,
    saturation: 30,
    lightness: 80,
  },
  darker: {
    hue: 190,
    saturation: 30,
    lightness: 32,
  },
  contrastText: {
    hue: 190,
    saturation: 20,
    lightness: 29,
  },
  background: {
    hue: 190,
    saturation: 30,
    lightness: 80,
  },
  backgroundContrastText: {
    hue: 190,
    saturation: 20,
    lightness: 29,
  },
};

const gallerySectionLabel = {
  Images: "Images",
  Audios: "Audio",
  Videos: "Videos",
  Documents: "Documents",
  Chemistry: "Chemistry",
  DMPs: "DMPs",
  NetworkFiles: "Filestores",
  Snippets: "Snippets",
  Miscellaneous: "Miscellaneous",
  PdfDocuments: "Exports",
};

const CustomDialog = styled(Dialog)(({ theme }) => ({
  "& .MuiDialog-container > .MuiPaper-root": {
    width: "1000px",
    maxWidth: "1000px",
    height: "calc(100% - 32px)", // 16px margin above and below dialog
    [theme.breakpoints.only("xs")]: {
      height: "100%",
      borderRadius: 0,
    },
  },
  "& .MuiDialogContent-root": {
    width: "100%",
    height: "calc(100% - 52px)", // 52px being the height of DialogActions
    overflowY: "unset",
    padding: theme.spacing(1.5, 2),
  },
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

const FileCard = styled(
  ({ file, className, selected, index, setSelectedFile }) => {
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
          <Card elevation={0} className={className}>
            <CardActionArea
              role={file.open ? "button" : "radio"}
              aria-checked={selected}
              onClick={() => (file.open ?? setSelectedFile)()}
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
                        "-webkit-line-clamp": "2",
                        "-webkit-box-orient": "vertical",
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

export default function Wrapper({
  open,
  onClose,
}: {|
  open: boolean,
  onClose: () => void,
|}): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <Picker open={open} onClose={onClose} />
    </ThemeProvider>
  );
}

const CustomGrow = React.forwardRef<ElementConfig<typeof Grow>, {||}>(
  (props: ElementConfig<typeof Grow>, ref: Ref<typeof Grow>) => (
    <Grow
      {...props}
      ref={ref}
      timeout={
        window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 300
      }
      easing="ease-in-out"
      style={{
        transformOrigin: "center 70%",
      }}
    />
  )
);
CustomGrow.displayName = "CustomGrow";

const StyledCloseIcon = styled(CloseIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

function Picker({
  open,
  onClose,
}: {|
  open: boolean,
  onClose: () => void,
|}): Node {
  const { isViewportSmall } = useViewportDimensions();
  const [drawerOpen, setDrawerOpen] = React.useState(!isViewportSmall);
  const [selectedIndicatorOffset, setSelectedIndicatorOffset] =
    React.useState(181);
  const [selectedSection, setSelectedSection] = React.useState("Chemistry");
  const [selectedFile, setSelectedFile] = React.useState<GalleryFile | null>(
    null
  );
  const [searchTerm, setSearchTerm] = React.useState("");
  const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
  const { galleryListing, path, clearPath } = useGalleryListing({
    section: selectedSection,
    searchTerm: appliedSearchTerm,
  });

  return (
    <CustomDialog
      open={open}
      TransitionComponent={CustomGrow}
      onClose={onClose}
      fullScreen={isViewportSmall}
    >
      <AppBar position="relative" open={true}>
        <Toolbar variant="dense">
          <IconButton
            aria-label={drawerOpen ? "close drawer" : "open drawer"}
            onClick={() => {
              setDrawerOpen(!drawerOpen);
            }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" noWrap component="h2">
            Gallery
          </Typography>
          <Box flexGrow={1}></Box>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              setAppliedSearchTerm(searchTerm);
            }}
          >
            <TextField
              placeholder="Search"
              sx={{
                /*
                 * This is so that it doesn't obscure the "Gallery" heading on
                 * very small mobile viewports
                 */
                maxWidth: isViewportSmall ? 184 : 300,
                width: isViewportSmall ? 184 : 300,
              }}
              value={searchTerm}
              onChange={({ currentTarget: { value } }) => setSearchTerm(value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
                endAdornment:
                  searchTerm !== "" ? (
                    <IconButtonWithTooltip
                      title="Clear"
                      icon={<StyledCloseIcon />}
                      size="small"
                      onClick={() => {
                        setSearchTerm("");
                        setAppliedSearchTerm("");
                      }}
                    />
                  ) : null,
              }}
            />
          </form>
          <Box ml={1} sx={{ transform: "translateY(2px)" }}>
            <HelpLinkIcon title="Importing from Gallery help" link="#" />
          </Box>
        </Toolbar>
      </AppBar>
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <CustomDrawer
          open={drawerOpen}
          anchor="left"
          variant="permanent"
          aria-label="gallery sections drawer"
        >
          <Box sx={{ overflowY: "auto", overflowX: "hidden" }}>
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
        <Box
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <DialogContent aria-live="polite">
            <Grid
              container
              direction="column"
              spacing={3}
              sx={{ height: "100%", flexWrap: "nowrap" }}
            >
              <Grid item>
                <Typography variant="h3" key={selectedSection}>
                  <Fade
                    in={true}
                    timeout={
                      window.matchMedia("(prefers-reduced-motion: reduce)")
                        .matches
                        ? 0
                        : 1000
                    }
                  >
                    <div>{gallerySectionLabel[selectedSection]}</div>
                  </Fade>
                </Typography>
                <Breadcrumbs
                  separator="›"
                  aria-label="breadcrumb"
                  sx={{ mt: 1 }}
                >
                  <Chip
                    size="small"
                    clickable
                    label={gallerySectionLabel[selectedSection]}
                    onClick={() => clearPath()}
                  />
                  {path.map((folder) => (
                    <Chip
                      size="small"
                      clickable
                      label={folder.name}
                      key={folder.id}
                      disabled={!folder.open}
                      onClick={() => folder.open?.()}
                    />
                  ))}
                </Breadcrumbs>
              </Grid>
              <Grid item sx={{ overflowY: "auto" }} flexGrow={1}>
                <Grid container spacing={2}>
                  {galleryListing.map((file, index) => (
                    <FileCard
                      selected={file === selectedFile}
                      file={file}
                      key={file.id}
                      index={index}
                      setSelectedFile={() => setSelectedFile(file)}
                    />
                  ))}
                </Grid>
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => onClose()}>Cancel</Button>
            <Button
              disabled={!selectedFile}
              variant="contained"
              onClick={() => {
                alert("Yet to be implemented!");
              }}
            >
              Add
            </Button>
          </DialogActions>
        </Box>
      </Box>
    </CustomDialog>
  );
}
