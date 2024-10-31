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
} from "../../../components/ImagePreview";
import clsx from "clsx";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { paperClasses } from "@mui/material/Paper";
import usePrimaryAction from "../primaryActionHooks";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useAsposePreview } from "./CallableAsposePreview";

/*
 * The height, in pixels, of the region that responds to touch/pointer events
 * to trigger the opening and closing of the floating info panel that is shown
 * on small viewports. When the panel is closed only the trigger region is shown.
 */
export const CLOSED_MOBILE_INFO_PANEL_HEIGHT = 80;

/*
 * To reduce the amount of visual noise, some components have a minimal stying
 * mode. This class is used to conditionally apply those styles.
 */
const MINIMAL_STYLING_CLASS = "minimal-styling";

/**
 * The info panel, be it the right column on desktop or the floating panel on
 * mobile, presents the user with a primary action that can be performed on the
 * item in question.
 *
 * The button is bright blue colour, despite the fact that the rest of the page
 * is themed with a very saturated purple, to draw the user's attention and to
 * match the colour of the cards/tree nodes that are selected; making a visual
 * link between the two.
 */
const ActionButton = ({
  onClick,
  label,
  disabled,
  sx,
}: {|
  onClick: (Event) => void,
  label: string,
  disabled?: boolean,
  sx: {| borderRadius: number, px: number, py: number |},
|}): Node => {
  return (
    <Button
      color="callToAction"
      variant="contained"
      sx={sx}
      onClick={onClick}
      /*
       * The SwipeableDrawer used on mobile has a little discoverability
       * feature where if you tap the card without swiping it it jiggles up and
       * down to indicate that it can be moved; this is controlled by the
       * `disableDiscovery` prop. When someone taps a button that is on the
       * card we want to prevent this discovery animation as the button has
       * nought to do with the sliding of the card. As such, we capture that
       * touch start event and prevent it from bubbling up to the
       * SwipeableDrawer component.
       */
      onTouchStart={(e) => {
        e.stopPropagation();
      }}
      disabled={disabled}
    >
      {label}
    </Button>
  );
};

const CustomSwipeableDrawer: typeof SwipeableDrawer = styled(SwipeableDrawer)(
  () => ({
    [`& .${paperClasses.root}`]: {
      /*
       * When open, the floating info panel takes up 90% of the height of
       * viewport, leaving just a small section at the top unobscured so that
       * the panel feels temporary and not modal. This height style is the
       * height of the info panel, minus the region at the top with the border
       * radii that triggers the open and closing and is therefore 0px when the
       * panel is closed.
       */
      height: `calc(90% - ${CLOSED_MOBILE_INFO_PANEL_HEIGHT}px)`,
      overflow: "visible",
    },
  })
);

/**
 * This components wraps all of the content inside of the floating info panel,
 * adjusting the positioning of the content so that the title and action button
 * are shown even when the panel is closed.
 */
const MobileInfoPanelContent: ComponentType<{|
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

/**
 * This button serves two purposes:
 *
 *  1. On touch devices, it provides a visual indicator that the panel can be
 *     swiped up and down to open and close, respectively.
 *
 *  2. On non-touch devices with small viewports, it provides a button that the
 *     user can tap to trigger the opening/closing of the floating panel.
 *
 */
const Puller: ComponentType<{|
  onClick: () => void,
  onKeyDown: (KeyboardEvent) => void,
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

/**
 * In addition to simply stating the name of the selected file, this text field
 * allows users to rename the file in place. At a glance it looks just like the
 * heading of the info panel, but on hover there is a subtle background and the
 * I-beam cursor to indicate that the text is editable. Upon clicking, the file
 * extension disappears and the text can be edited. Upon editing, cancel and
 * save buttons emerge which do the obvious. Escape and return keyboard events
 * similarly clear and submit the changes.
 *
 * Whilst this functionality is not the most discoverable, and isn't at all
 * available on mobile, the rename action remains in the actions menu so this
 * is purely as a functionality enhancement for quick editing. Given that this
 * is not the *only* way to perform a rename, this functionality MAY NOT fully
 * meet the accessibility standard.
 *
 * @param file      The selected file that is being shown in the info panel
 * @param className Ignore; it is provided by the `styled` HOC.
 */
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
              /*
               * We use multiline so that long names wrap, but prevent the user
               * from typing in a return character by using string replacement
               */
              multiline
              onChange={({ target: { value } }) =>
                setName(value.replace(/\n/g, ""))
              }
              fullWidth
              size="small"
              className={clsx(className, name !== file.name && "modified")}
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
            <Collapse
              in={name !== file.name}
              timeout={
                window.matchMedia("(prefers-reduced-motion: reduce)").matches
                  ? 0
                  : 200
              }
            >
              <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                <Button
                  size="small"
                  onClick={() => {
                    setName(file.name);
                  }}
                  sx={{ px: 0.75 }}
                >
                  Cancel
                </Button>
                <Button
                  size="small"
                  variant="contained"
                  color="callToAction"
                  type="submit"
                  sx={{ px: 0.75 }}
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
  [`& .${outlinedInputClasses.multiline}`]: {
    paddingTop: theme.spacing(0.25),
    paddingBottom: theme.spacing(0.25),
    paddingLeft: theme.spacing(0.25),
  },
}));

/*
 * With this component, the user can edit the description of the passed file.
 *
 * The component has two styles: a regular mode where it has a border and
 * background which makes it look like a normal text field and a minimal
 * styling mode where the border and background are only shown on hover, on
 * focus, and when there have been edits. This so that we can minimise the
 * visual noise on desktop where the fact that the rendered text is editable is
 * discoverable by hovering over it with the cursor or tabbing to it with the
 * keyboard. On mobile devices, the regular styling should be used to make it
 * obvious that the text is editable.
 *
 * When the field has focus or edits have been made, a save and cancel button
 * appear. Enter allows the user to enter multiple lines of text and so
 * submitting by keyboard requires tabbing to the submit button.
 *
 * @param file           The selected file whose description is being edited.
 *                       A description that is anything but `present` is
 *                       considered to be blank.
 * @param minimalStyling Whether minimal styling is applied.
 *                       Ignored when high contrast mode is requested, with the
 *                       borders always being shown.
 * @param className      Ignore; it is provided by the `styled` HOC.
 */
const DescriptionField = styled(
  observer(
    ({
      file,
      minimalStyling = false,
      className,
    }: {|
      file: GalleryFile,
      minimalStyling?: boolean,
      className: string,
    |}) => {
      const { changeDescription } = useGalleryActions();
      function getDescValue(f: GalleryFile) {
        return f.description.match({
          missing: () => "",
          empty: () => "",
          present: (d) => d,
        });
      }

      const [description, setDescription] = React.useState<string>(
        getDescValue(file)
      );
      React.useEffect(() => {
        setDescription(getDescValue(file));
      }, [file]);

      const prefersMoreContrast = window.matchMedia(
        "(prefers-contrast: more)"
      ).matches;

      return (
        <Stack>
          <TextField
            value={description}
            placeholder="No description"
            fullWidth
            size="small"
            className={clsx(
              className,
              minimalStyling && !prefersMoreContrast && MINIMAL_STYLING_CLASS,
              description !== getDescValue(file) && "modified"
            )}
            onChange={({ target: { value } }) => setDescription(value)}
            multiline
            onKeyDown={(e) => {
              if (e.key === "Enter" && e.shiftKey) {
                e.stopPropagation();
                e.preventDefault();
                void changeDescription(file, Description.Present(description));
              }
            }}
          />
          <Collapse
            in={description !== getDescValue(file)}
            timeout={
              window.matchMedia("(prefers-reduced-motion: reduce)").matches
                ? 0
                : 200
            }
          >
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
                color="callToAction"
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
  [`&.${MINIMAL_STYLING_CLASS}`]: {
    "&.modified": {
      [`& .${outlinedInputClasses.root}`]: {
        backgroundColor: `hsl(${COLOR.main.hue}deg, ${COLOR.main.saturation}%, 90%)`,
        [`& .${outlinedInputClasses.notchedOutline}`]: {
          border: "none",
        },
      },
    },
    "&:not(.modified)": {
      [`& .${outlinedInputClasses.root}`]: {
        backgroundColor: "unset",
        [`& .${outlinedInputClasses.notchedOutline}`]: {
          border: "none",
        },
      },
    },
    [`& .${outlinedInputClasses.root}`]: {
      border: "none",
      borderRadius: "4px",
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
    [`& .${outlinedInputClasses.multiline}`]: {
      paddingTop: theme.spacing(0.25),
      paddingBottom: theme.spacing(0.25),
      paddingLeft: theme.spacing(0.25),
    },
  },
}));

const InfoPanelContent = ({
  file,
  smallViewport = false,
}: {
  file: GalleryFile,
  smallViewport?: boolean,
}): Node => {
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
            value: (
              <DescriptionField file={file} minimalStyling={!smallViewport} />
            ),
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

const AsposePreviewButton = ({ file }: {| file: GalleryFile |}) => {
  const { openAsposePreview, loading } = useAsposePreview();
  return (
    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
      <ActionButton
        disabled={loading}
        onClick={() => {
          void openAsposePreview(file);
        }}
        label={loading ? "Loading" : "View"}
        sx={{
          borderRadius: 1,
          px: 1.125,
          py: 0.25,
        }}
      />
    </Grid>
  );
};

export const InfoPanelForLargeViewports: ComponentType<{||}> = () => {
  const selection = useGallerySelection();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const primaryAction = usePrimaryAction();

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
                  ml: 1.5,
                  mt: 1,
                  mb: 1,
                  lineBreak: "anywhere",
                  textTransform: "none",
                  fontWeight: 400,
                }}
              >
                {selection.size === 0 && "Nothing selected."}
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
          .only.map((file) =>
            primaryAction(file)
              .map((action) => {
                if (action.tag === "open")
                  return (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={action.open}
                        label="Open"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "image")
                  return (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={() => {
                          openImagePreview(action.downloadHref, {
                            caption: action.caption,
                          });
                        }}
                        label="View"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "collabora")
                  return (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={() => {
                          window.open(action.url);
                        }}
                        label="Edit"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "officeonline")
                  return (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={() => {
                          window.open(action.url);
                        }}
                        label="Edit"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "pdf")
                  return (
                    <Grid item sx={{ mt: 0.5, mb: 0.25 }} key={null}>
                      <ActionButton
                        onClick={() => {
                          openPdfPreview(action.downloadHref);
                        }}
                        label="View"
                        sx={{
                          borderRadius: 1,
                          px: 1.125,
                          py: 0.25,
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "aspose")
                  return <AsposePreviewButton key={null} file={file} />;
              })
              .orElseGet((errors) => {
                console.info("Could not provide view");
                errors.forEach((e) => {
                  console.info(e);
                });
                return (
                  <Grid item sx={{ mt: 0.5, mb: 0.25 }}>
                    <ActionButton
                      onClick={() => {
                        // do nothing
                      }}
                      disabled
                      label="View"
                      sx={{
                        borderRadius: 1,
                        px: 1.125,
                        py: 0.25,
                      }}
                    />
                  </Grid>
                );
              })
          )
          .orElse(null)}
      </Grid>
      {selection
        .asSet()
        .only.map((f) => (
          <CardContent sx={{ p: 1, pr: 0.5 }} key={null}>
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
      SwipeAreaProps={{
        sx: {
          display: { xs: "block", md: "none" },
        },
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
      <MobileInfoPanelContent>
        <Stack spacing={1} height="100%">
          <Puller
            onClick={() => setMobileInfoPanelOpen(!mobileInfoPanelOpen)}
            onKeyDown={(e) => {
              if (e.key === " ") setMobileInfoPanelOpen(!mobileInfoPanelOpen);
            }}
            role="button"
            tabIndex={0}
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
                  sx={{
                    border: "none",
                    textTransform: "none",
                    overflowWrap: "anywhere",
                  }}
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
                    label="View"
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
              .only.map((f) => (
                <InfoPanelContent key={null} file={f} smallViewport />
              ))
              .orElse(null)}
          </CardContent>
        </Stack>
      </MobileInfoPanelContent>
    </CustomSwipeableDrawer>
  );
};
