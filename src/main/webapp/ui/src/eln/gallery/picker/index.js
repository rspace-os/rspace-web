//@flow

import Dialog from "@mui/material/Dialog";
import React, { type Node, type ElementConfig, type Ref } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import DialogContent from "@mui/material/DialogContent";
import AppBar from "../components/AppBar";
import Typography from "@mui/material/Typography";
import Card from "@mui/material/Card";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import Box from "@mui/material/Box";
import Breadcrumbs from "@mui/material/Breadcrumbs";
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
import Chip from "@mui/material/Chip";
import createAccentedTheme from "../../../accentedTheme";
import { COLORS as baseThemeColors } from "../../../theme";
import Avatar from "@mui/material/Avatar";
import Grow from "@mui/material/Grow";
import useViewportDimensions from "../../../util/useViewportDimensions";
import useGalleryListing, { type GalleryFile } from "./useGalleryListing";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import Fade from "@mui/material/Fade";
import CardActionArea from "@mui/material/CardActionArea";
import * as FetchingData from "../../../util/fetchingData";
import { gallerySectionLabel } from "./common";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import { Result } from "../../../util/result";
import { observer } from "mobx-react-lite";
import Sidebar from "../components/Sidebar";
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

const Picker = observer(
  ({ open, onClose }: {| open: boolean, onClose: () => void |}) => {
    const viewport = useViewportDimensions();
    const [selectedFile, setSelectedFile] = React.useState<GalleryFile | null>(
      null
    );
    const [appliedSearchTerm, setAppliedSearchTerm] = React.useState("");
    const [selectedSection, setSelectedSection] = React.useState("Chemistry");
    const { galleryListing, path, clearPath } = useGalleryListing({
      section: selectedSection,
      searchTerm: appliedSearchTerm,
    });

    React.useEffect(() => {
      setSelectedFile(null);
    }, [selectedSection, appliedSearchTerm, path]);

    return (
      <CustomDialog
        open={open}
        TransitionComponent={CustomGrow}
        onClose={onClose}
        fullScreen={viewport.isViewportSmall}
      >
        <AppBar
          appliedSearchTerm={appliedSearchTerm}
          setAppliedSearchTerm={setAppliedSearchTerm}
        />
        <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
          <Sidebar
            selectedSection={selectedSection}
            setSelectedSection={setSelectedSection}
          />
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
                    sx={{ mt: 0.5 }}
                  >
                    <Chip
                      size="small"
                      clickable
                      label={gallerySectionLabel[selectedSection]}
                      onClick={() => clearPath()}
                      sx={{ mt: 0.5 }}
                    />
                    {path.map((folder) => (
                      <Chip
                        size="small"
                        clickable
                        label={folder.name}
                        key={folder.id}
                        disabled={!folder.open}
                        onClick={() => folder.open?.()}
                        sx={{ mt: 0.5 }}
                      />
                    ))}
                  </Breadcrumbs>
                </Grid>
                <Grid item sx={{ overflowY: "auto" }} flexGrow={1}>
                  {FetchingData.match(galleryListing, {
                    loading: () => <></>,
                    error: (error) => <>{error}</>,
                    success: (listing) =>
                      listing.tag === "list" ? (
                        <Grid container spacing={2}>
                          {listing.list.map((file, index) => (
                            <FileCard
                              selected={file === selectedFile}
                              file={file}
                              key={file.id}
                              index={index}
                              setSelectedFile={() => setSelectedFile(file)}
                            />
                          ))}
                        </Grid>
                      ) : (
                        <div key={listing.reason}>
                          <Fade
                            in={true}
                            timeout={
                              window.matchMedia(
                                "(prefers-reduced-motion: reduce)"
                              ).matches
                                ? 0
                                : 300
                            }
                          >
                            <div>
                              <PlaceholderLabel>
                                {listing.reason}
                              </PlaceholderLabel>
                            </div>
                          </Fade>
                        </div>
                      ),
                  })}
                </Grid>
              </Grid>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => onClose()}>Cancel</Button>
              <ValidatingSubmitButton
                validationResult={
                  selectedFile
                    ? Result.Ok(null)
                    : Result.Error([new Error("No file selected.")])
                }
                loading={false}
                onClick={() => {
                  alert("Yet to be implemented!");
                }}
              >
                Add
              </ValidatingSubmitButton>
            </DialogActions>
          </Box>
        </Box>
      </CustomDialog>
    );
  }
);
