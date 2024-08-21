//@flow

import React, { type Node, type ComponentType } from "react";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import { styled } from "@mui/material/styles";
import { type GalleryFile, Description } from "../useGalleryListing";
import { useGallerySelection } from "../useGallerySelection";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { COLORS as baseThemeColors } from "../../../theme";
import { COLOR } from "../common";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Box from "@mui/material/Box";
import TextField from "@mui/material/TextField";
import SwipeableDrawer from "@mui/material/SwipeableDrawer";
import CardContent from "@mui/material/CardContent";
import Collapse from "@mui/material/Collapse";
import { grey } from "@mui/material/colors";
import { Optional } from "../../../util/optional";
import DescriptionList from "../../../components/DescriptionList";
import { formatFileSize } from "../../../util/files";
import Result from "../../../util/result";
import { LinkedDocumentsPanel } from "./LinkedDocumentsPanel";
import { observer } from "mobx-react-lite";

const CLOSED_MOBILE_INFO_PANEL_HEIGHT = 80;

const CustomSwipeableDrawer: typeof SwipeableDrawer = styled(SwipeableDrawer)(
  () => ({
    "& .MuiPaper-root": {
      height: `calc(90% - ${CLOSED_MOBILE_INFO_PANEL_HEIGHT}px)`,
      overflow: "visible",
    },
  })
);

const MobileInfoPanelHeader: ComponentType<{|
  children: Node,
|}> = styled(Box)(({ theme }) => ({
  position: "absolute",
  top: -CLOSED_MOBILE_INFO_PANEL_HEIGHT,
  height: "100%",
  visibility: "visible",
  right: 0,
  left: 0,
  backgroundColor: "white",
  borderTopLeftRadius: theme.spacing(2),
  borderTopRightRadius: theme.spacing(2),
  boxShadow: "hsl(280deg 66% 10% / 5%) 0px -8px 8px 2px",
}));

const Puller: ComponentType<{|
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

const DescriptionField = styled(
  observer(
    ({ file, className }: {| file: GalleryFile, className: string |}) => {
      function getDescValue(f: GalleryFile) {
        return f.description.match({
          missing: () => "",
          empty: () => "",
          present: (d) => d,
        });
      }

      const [description, setDescription] = React.useState(getDescValue(file));
      React.useEffect(() => {
        setDescription(getDescValue(file));
      }, [file]);

      return (
        <Stack>
          <TextField
            value={description}
            placeholder="No description"
            fullWidth
            size="small"
            className={className}
            onChange={({ target: { value } }) => setDescription(value)}
          />
          <Collapse in={description !== getDescValue(file)}>
            <Stack direction="row" spacing={0.5} justifyContent="flex-end">
              <Button
                size="small"
                onClick={() => {
                  setDescription(getDescValue(file));
                }}
              >
                Cancel
              </Button>
              <Button
                size="small"
                variant="contained"
                onClick={() => {
                  void file.changeDescription(Description.Present(description));
                }}
              >
                Save
              </Button>
            </Stack>
          </Collapse>
        </Stack>
      );
    }
  )
)(({ theme }) => ({
  "& .MuiOutlinedInput-notchedOutline": {
    border: "none",
  },
  "& .MuiOutlinedInput-root": {
    borderRadius: "4px",
    fontSize: "0.9rem",
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    backgroundColor: `hsl(${COLOR.main.hue}deg, ${COLOR.main.saturation}%, 90%)`,
  },
}));

const InfoPanelContent = ({ file }: { file: GalleryFile }): Node => {
  return (
    <Stack sx={{ height: "100%" }}>
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
            value: <DescriptionField file={file} />,
            below: true,
          },
        ]}
        sx={{
          "& dd.below": {
            width: "100%",
          },
        }}
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
      <LinkedDocumentsPanel file={file} />
    </Stack>
  );
};

const InfoPanelMultipleContent = (): Node => {
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

export const InfoPanelForLargeViewports: ComponentType<{||}> = () => {
  const selection = useGallerySelection();
  return (
    <>
      <Grid
        container
        direction="row"
        spacing={2}
        alignItems="flex-start"
        flexWrap="nowrap"
      >
        <Grid item sx={{ flexShrink: 1, flexGrow: 1 }}>
          <Typography
            variant="h3"
            sx={{
              border: "none",
              m: 0.75,
              ml: 1,
              lineBreak: "anywhere",
              textTransform: "none",
            }}
          >
            {selection.size === 0 && "Nothing selected"}
            {selection.size === 1 &&
              selection
                .asSet()
                .only.map((f) => f.name)
                .orElse(null)}
            {selection.size > 1 && selection.label}
          </Typography>
        </Grid>
        {selection
          .asSet()
          .only.flatMap((file) =>
            file.open ? Optional.present(file.open) : Optional.empty()
          )
          .map((open) => (
            <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
              <Button
                color="primary"
                variant="contained"
                sx={{
                  backgroundColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
                  borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
                  color: "white",
                  borderRadius: 3,
                  px: 1.125,
                  py: 0.25,
                }}
                onClick={open}
              >
                Open
              </Button>
            </Grid>
          ))
          .orElse(null)}
      </Grid>
      {selection
        .asSet()
        .only.map((f) => (
          <CardContent sx={{ p: 1, pr: 0.5, height: "100%" }} key={null}>
            <InfoPanelContent file={f} />
          </CardContent>
        ))
        .orElse(null)}
      {selection.size > 1 && (
        <CardContent sx={{ p: 1, pr: 0.5 }} key={null}>
          <InfoPanelMultipleContent />
        </CardContent>
      )}
    </>
  );
};

export const InfoPanelForSmallViewports: ComponentType<{|
  file: GalleryFile,
|}> = ({ file }) => {
  const [mobileInfoPanelOpen, setMobileInfoPanelOpen] = React.useState(false);
  const selection = useGallerySelection();

  return (
    <CustomSwipeableDrawer
      key={null}
      anchor="bottom"
      open={mobileInfoPanelOpen}
      sx={{
        display: { xs: "block", md: "none" },
        touchAction: "none",
      }}
      onClose={() => {
        setMobileInfoPanelOpen(false);
      }}
      onOpen={() => {
        setMobileInfoPanelOpen(true);
      }}
      swipeAreaWidth={CLOSED_MOBILE_INFO_PANEL_HEIGHT}
      disableSwipeToOpen={false}
      ModalProps={{
        keepMounted: true,
      }}
      allowSwipeInChildren={(event) => {
        if (event.target.id === "open") return false;
        return true;
      }}
    >
      <MobileInfoPanelHeader>
        <Stack spacing={1} height="100%">
          <Puller
            onClick={() => setMobileInfoPanelOpen(!mobileInfoPanelOpen)}
            role="button"
            tabIndex={-1}
          />
          <CardContent>
            <Grid
              container
              direction="row"
              spacing={2}
              flexWrap="nowrap"
              sx={{ mb: 2, minHeight: "54px" }}
            >
              <Grid item sx={{ flexShrink: 1, flexGrow: 1, mt: 0.5 }}>
                <Typography
                  variant="h3"
                  sx={{ border: "none", textTransform: "none" }}
                >
                  {file.name}
                </Typography>
              </Grid>
              {file.open && (
                <Grid item>
                  <Button
                    color="primary"
                    variant="contained"
                    sx={{
                      backgroundColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
                      borderColor: `hsl(${baseThemeColors.primary.hue}deg, ${baseThemeColors.primary.saturation}%, ${baseThemeColors.primary.lightness}%)`,
                      color: "white",
                      borderRadius: 3,
                      px: 2.5,
                      py: 0.5,
                    }}
                    id="open"
                    onClick={(e) => {
                      e.stopPropagation();
                      file.open?.();
                      setMobileInfoPanelOpen(false);
                    }}
                  >
                    Open
                  </Button>
                </Grid>
              )}
            </Grid>
            {selection
              .asSet()
              .only.map((f) => <InfoPanelContent key={null} file={f} />)
              .orElse(null)}
          </CardContent>
        </Stack>
      </MobileInfoPanelHeader>
    </CustomSwipeableDrawer>
  );
};
