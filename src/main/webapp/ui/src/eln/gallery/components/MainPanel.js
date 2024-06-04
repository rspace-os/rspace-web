//@flow

import React, { type Node } from "react";
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
import { type GalleryFile } from "../useGalleryListing";

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
  selectedFile,
  setSelectedFile,
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
|}): Node {
  return (
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
                window.matchMedia("(prefers-reduced-motion: reduce)").matches
                  ? 0
                  : 1000
              }
            >
              <div>{gallerySectionLabel[selectedSection]}</div>
            </Fade>
          </Typography>
          <Breadcrumbs separator="›" aria-label="breadcrumb" sx={{ mt: 0.5 }}>
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
                      window.matchMedia("(prefers-reduced-motion: reduce)")
                        .matches
                        ? 0
                        : 300
                    }
                  >
                    <div>
                      <PlaceholderLabel>{listing.reason}</PlaceholderLabel>
                    </div>
                  </Fade>
                </div>
              ),
          })}
        </Grid>
      </Grid>
    </DialogContent>
  );
}
