//@flow

import React, { type Node, type ComponentType } from "react";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import { styled } from "@mui/material/styles";
import { type GalleryFile, Description } from "../useGalleryListing";
import { useGallerySelection } from "../useGallerySelection";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { COLOR } from "../common";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Box from "@mui/material/Box";
import TextField from "@mui/material/TextField";
import SwipeableDrawer from "@mui/material/SwipeableDrawer";
import CardContent from "@mui/material/CardContent";
import Collapse from "@mui/material/Collapse";
import { grey } from "@mui/material/colors";
import DescriptionList from "../../../components/DescriptionList";
import { formatFileSize, filenameExceptExtension } from "../../../util/files";
import Result from "../../../util/result";
import { LinkedDocumentsPanel } from "./LinkedDocumentsPanel";
import { observer } from "mobx-react-lite";
import { useGalleryActions } from "../useGalleryActions";
import ImagePreview, {
  type PreviewSize,
} from "../../../Inventory/components/ImagePreview";
import * as FetchingData from "../../../util/fetchingData";
import { useDeploymentProperty } from "../../useDeploymentProperty";
import * as Parsers from "../../../util/parsers";
import useCollabora from "../useCollabora";
import useOfficeOnline from "../useOfficeOnline";
import clsx from "clsx";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { paperClasses } from "@mui/material/Paper";

const CLOSED_MOBILE_INFO_PANEL_HEIGHT = 80;

const ActionButton = ({
  onClick,
  label,
  sx,
}: {|
  onClick: (Event) => void,
  label: string,
  sx: {| borderRadius: number, px: number, py: number |},
|}): Node => {
  return (
    <Button color="selection" variant="contained" sx={sx} onClick={onClick}>
      {label}
    </Button>
  );
};

const CustomSwipeableDrawer: typeof SwipeableDrawer = styled(SwipeableDrawer)(
  () => ({
    [`& .${paperClasses.root}`]: {
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

const NameFieldForLargeViewports = styled(
  observer(
    ({ file, className }: {| file: GalleryFile, className: string |}) => {
      const { rename } = useGalleryActions();

      const [name, setName] = React.useState(file.name);
      React.useEffect(() => {
        setName(file.name);
      }, [file.name]);

      const textField = React.useRef(null);

      return (
        <Stack sx={{ pr: 0.25, pl: 0.75 }}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void rename(file, name);
              textField.current?.blur();
            }}
            onKeyDown={(e) => {
              if (e.key === "Escape") setName(file.name);
            }}
          >
            <TextField
              value={name}
              placeholder="No Name"
              fullWidth
              size="small"
              className={clsx(className, name !== file.name && "modified")}
              onChange={({ target: { value } }) => setName(value)}
              inputProps={{
                "aria-label": "Name",
                ref: textField,
              }}
              onFocus={() => {
                if (name === file.name)
                  setName(filenameExceptExtension(file.name));
              }}
              onBlur={() => {
                if (name === filenameExceptExtension(file.name))
                  setName(file.name);
              }}
            />
            <Collapse in={name !== file.name}>
              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                <Button
                  size="small"
                  onClick={() => {
                    setName(file.name);
                  }}
                >
                  Cancel
                </Button>
                <Button
                  size="small"
                  variant="contained"
                  color="primary"
                  type="submit"
                >
                  Save
                </Button>
              </Stack>
            </Collapse>
          </form>
        </Stack>
      );
    }
  )
)(({ theme }) => ({
  "&.modified": {
    [`& .${outlinedInputClasses.root}`]: {
      backgroundColor: `hsl(${COLOR.main.hue}deg, ${COLOR.main.saturation}%, 90%)`,
      [`& .${outlinedInputClasses.notchedOutline}`]: {
        border: "none",
      },
    },
  },
  "&:not(.modified)": {
    [`& .${outlinedInputClasses.notchedOutline}`]: {
      border: "none",
    },
  },
  [`& .${outlinedInputClasses.root}`]: {
    border: "none",
    borderRadius: "4px",
    fontSize: "1.23rem", // to be the same height as the adjacent button
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    transition: "all .3s ease-in-out",
    "&:hover, &:focus-within": {
      backgroundColor: `hsl(${COLOR.main.hue}deg, ${COLOR.main.saturation}%, 90%)`,
      [`& .${outlinedInputClasses.notchedOutline}`]: {
        border: "none !important",
      },
    },
  },
  [`& .${outlinedInputClasses.input}`]: {
    paddingTop: theme.spacing(0.25),
    paddingBottom: theme.spacing(0.25),
    paddingLeft: theme.spacing(0.25),
  },
}));

const DescriptionField = styled(
  observer(
    ({ file, className }: {| file: GalleryFile, className: string |}) => {
      const { changeDescription } = useGalleryActions();
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
            multiline
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
                color="primary"
                onClick={() => {
                  void changeDescription(
                    file,
                    Description.Present(description)
                  );
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
  [`& .${outlinedInputClasses.root}`]: {
    borderRadius: "4px",
    fontSize: "0.9rem",
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    backgroundColor: `hsl(${COLOR.main.hue}deg, ${COLOR.main.saturation}%, 90%)`,
  },
  [`& .${outlinedInputClasses.input}`]: {
    paddingLeft: theme.spacing(1),
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
  const [previewSize, setPreviewSize] = React.useState<null | PreviewSize>(
    null
  );
  const [previewOpen, setPreviewOpen] = React.useState(false);
  const collaboraEnabled = useDeploymentProperty("collabora.wopi.enabled");
  const { supportedExts: supportedCollaboraExts } = useCollabora();
  const officeOnlineEnabled = useDeploymentProperty("msOfficeEnabled");
  const { supportedExts: supportedOfficeOnlineExts } = useOfficeOnline();

  return (
    <>
      <Grid
        container
        direction="row"
        spacing={0.5}
        alignItems="flex-start"
        flexWrap="nowrap"
        sx={{
          marginLeft: "-10px",
          marginTop: "-8px",
          width: "calc(100% + 9px)",
        }}
      >
        <Grid item sx={{ flexShrink: 1, flexGrow: 1 }}>
          {selection
            .asSet()
            .only.toResult(() => new Error("Empty or multiple selected"))
            .flatMap((f) =>
              f.isSystemFolder
                ? Result.Error<GalleryFile>([
                    new Error("Cannot rename system folder"),
                  ])
                : Result.Ok(f)
            )
            .map((f) => <NameFieldForLargeViewports key={null} file={f} />)
            .orElse(
              <Typography
                variant="h3"
                sx={{
                  border: "none",
                  // these margins are setup so that the heading takes up the
                  // same amount of space as the text field when it is shown
                  mr: 0.75,
                  ml: 1,
                  mt: 1.25,
                  mb: 1,
                  lineBreak: "anywhere",
                  textTransform: "none",
                }}
              >
                {selection.size === 0 && "Nothing selected"}
                {selection
                  .asSet()
                  .only.map((f) => f.name)
                  .orElse(null)}
                {selection.size > 1 && selection.label}
              </Typography>
            )}
        </Grid>
        {selection
          .asSet()
          .only.map((file) => {
            if (file.open)
              return (
                <Grid item sx={{ mt: 0.5, mb: 0.25 }}>
                  <ActionButton
                    onClick={() => file.open?.()}
                    label="Open"
                    sx={{
                      borderRadius: 1,
                      px: 1.125,
                      py: 0.25,
                    }}
                  />
                </Grid>
              );
            if (file.isImage && file.downloadHref)
              return (
                <Grid item sx={{ mt: 0.5, mb: 0.25 }}>
                  <ActionButton
                    onClick={() => {
                      setPreviewOpen(true);
                    }}
                    label="Preview"
                    sx={{
                      borderRadius: 1,
                      px: 1.125,
                      py: 0.25,
                    }}
                  />
                  {previewOpen && (
                    <ImagePreview
                      closePreview={() => {
                        setPreviewOpen(false);
                      }}
                      link={file.downloadHref}
                      size={previewSize}
                      setSize={(s) => setPreviewSize(s)}
                    />
                  )}
                </Grid>
              );
            return FetchingData.getSuccessValue(collaboraEnabled)
              .flatMap(Parsers.isBoolean)
              .flatMap(Parsers.isTrue)
              .flatMap(() => Parsers.isNotNull(file.extension))
              .flatMap((extension) =>
                supportedCollaboraExts.has(extension)
                  ? Result.Ok(null)
                  : Result.Error([
                      new Error(
                        "Selected file's extension is not supported by collabora"
                      ),
                    ])
              )
              .map(() => (
                <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                  <ActionButton
                    onClick={() => {
                      window.open(
                        "/collaboraOnline/" + file.globalId + "/edit"
                      );
                    }}
                    label="Edit"
                    sx={{
                      borderRadius: 1,
                      px: 1.125,
                      py: 0.25,
                    }}
                  />
                </Grid>
              ))
              .orElseTry((collaboraErrors) =>
                FetchingData.getSuccessValue(officeOnlineEnabled)
                  .flatMap(Parsers.isBoolean)
                  .flatMap(Parsers.isTrue)
                  .flatMap(() => Parsers.isNotNull(file.extension))
                  .flatMap((extension) =>
                    supportedOfficeOnlineExts.has(extension)
                      ? Result.Ok(null)
                      : Result.Error([
                          new Error(
                            "Selected file's extension is not supported by office online"
                          ),
                        ])
                  )
                  .map(() => (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={() => {
                          window.open(
                            "/officeOnline/" + file.globalId + "/view"
                          );
                        }}
                        label="Edit"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  ))
                  .orElseTry((officeOnlineErrors) =>
                    Result.Error<Node>([
                      ...collaboraErrors,
                      ...officeOnlineErrors,
                    ])
                  )
              )
              .orElseGet((errors) => {
                errors.forEach((e) => {
                  console.error(e);
                });
                return null;
              });
          })
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
  const [previewSize, setPreviewSize] = React.useState<null | PreviewSize>(
    null
  );
  const [previewOpen, setPreviewOpen] = React.useState(false);
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
                  <ActionButton
                    label="Open"
                    sx={{
                      borderRadius: 3,
                      px: 2.5,
                      py: 0.5,
                    }}
                    onClick={(e) => {
                      e.stopPropagation();
                      file.open?.();
                      setMobileInfoPanelOpen(false);
                    }}
                  />
                </Grid>
              )}
              {file.isImage && file.downloadHref && (
                <Grid item>
                  <ActionButton
                    onClick={() => {
                      setPreviewOpen(true);
                    }}
                    label="Preview"
                    sx={{
                      borderRadius: 3,
                      px: 2.5,
                      py: 0.5,
                    }}
                  />
                  {previewOpen && (
                    <ImagePreview
                      closePreview={() => {
                        setPreviewOpen(false);
                      }}
                      link={file.downloadHref}
                      size={previewSize}
                      setSize={(s) => setPreviewSize(s)}
                    />
                  )}
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
