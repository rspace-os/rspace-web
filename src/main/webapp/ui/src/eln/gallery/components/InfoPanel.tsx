import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import Collapse from "@mui/material/Collapse";
import { grey } from "@mui/material/colors";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { paperClasses } from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import SwipeableDrawer from "@mui/material/SwipeableDrawer";
import type { SxProps, Theme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import axios from "@/common/axios";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/gallery";
import DescriptionList from "../../../components/DescriptionList";
import ImagePreview, { type PreviewSize } from "../../../components/ImagePreview";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { filenameExceptExtension, formatFileSize } from "../../../util/files";
import { Optional } from "../../../util/optional";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";
import usePrimaryAction from "../primaryActionHooks";
import { useGalleryActions } from "../useGalleryActions";
import { Description, Filestore, type GalleryFile, idToString, RemoteFile } from "../useGalleryListing";
import { useGallerySelection } from "../useGallerySelection";
import { useAsposePreview } from "./CallableAsposePreview";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";
import { useSnapGenePreview } from "./CallableSnapGenePreview";
import { useSnippetPreview } from "./CallableSnippetPreview";
import { useFolderOpen } from "./OpenFolderProvider";
import { ReferencingInventoryItemsPanel } from "./ReferencingInventoryItemsPanel";

/**
 * The height, in pixels, of the region that responds to touch/pointer events
 * to trigger the opening and closing of the floating info panel that is shown
 * on small viewports. When the panel is closed only the trigger region is shown.
 */
export const CLOSED_MOBILE_INFO_PANEL_HEIGHT = 80;

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
}: {
  onClick: (event: React.MouseEvent<HTMLButtonElement>) => void;
  label: string;
  disabled?: boolean;
  sx?: SxProps<Theme>;
}): React.ReactNode => {
  return (
    <Button
      component="button"
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
const NameFieldForLargeViewports = observer(({ file }: { file: GalleryFile }) => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { rename } = useGalleryActions();
  const [name, setName] = React.useState(file.name);
  const textField = React.useRef<HTMLInputElement | null>(null);
  function handleSubmit() {
    void rename(file, name).then(() => {
      textField.current?.blur();
      setName(file.transformFilename(() => name));
      trackEvent("user:renames:file:gallery");
    });
  }
  return (
    <Stack
      sx={{
        pr: 0.25,
        pl: 0.75,
      }}
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSubmit();
        }}
      >
        <TextField
          value={name}
          placeholder="No Name"
          /*
           * We use multiline so that long names wrap, but prevent the user
           * from typing in a return character by using string replacement.
           */
          multiline
          onChange={({ target: { value } }) => setName(value.replace(/\n/g, ""))}
          fullWidth
          size="small"
          sx={(theme) => ({
            ...(name !== file.name
              ? {
                  [`& .${outlinedInputClasses.root}`]: {
                    backgroundColor: `hsl(${ACCENT_COLOR.main.hue}deg, ${ACCENT_COLOR.main.saturation}%, 90%)`,
                    [`& .${outlinedInputClasses.notchedOutline}`]: {
                      border: "none",
                    },
                  },
                }
              : {
                  [`& .${outlinedInputClasses.notchedOutline}`]: {
                    border: "none",
                  },
                }),
            [`& .${outlinedInputClasses.root}`]: {
              border: "none",
              borderRadius: "4px",
              fontSize: "1.4rem",
              marginTop: theme.spacing(0.5),
              marginBottom: theme.spacing(0.5),
              transition: "all .3s ease-in-out",
              "&:hover, &:focus-within": {
                backgroundColor: `hsl(${ACCENT_COLOR.main.hue}deg, ${ACCENT_COLOR.main.saturation}%, 90%)`,
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
          })}
          onFocus={() => {
            if (name === file.name) setName(filenameExceptExtension(file.name));
          }}
          onBlur={() => {
            if (name === filenameExceptExtension(file.name)) setName(file.name);
          }}
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              setName(file.name);
              textField.current?.blur();
            }
            /*
             * We have to explicitly handle enter key and can't rely on the
             * form's onSubmit being automaticaly called because we're using
             * a multiline textfield and so the enter key will naturally
             * enter a newline
             */
            if (e.key === "Enter") {
              handleSubmit();
            }
          }}
          slotProps={{
            htmlInput: {
              "aria-label": "Name",
              ref: textField,
            },
          }}
        />
        <Collapse
          in={name !== file.name}
          timeout={window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200}
        >
          <Stack
            direction="row"
            spacing={0.5}
            sx={{
              justifyContent: "flex-end",
            }}
          >
            <Button
              size="small"
              onClick={() => {
                setName(file.name);
              }}
              sx={{
                px: 0.75,
              }}
            >
              Cancel
            </Button>
            <Button
              size="small"
              variant="contained"
              color="callToAction"
              type="submit"
              sx={{
                px: 0.75,
              }}
            >
              Save
            </Button>
          </Stack>
        </Collapse>
      </form>
    </Stack>
  );
});

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
 *                       The `description` prop should be derived from
 *                       `file.description`.
 * @param description    The current description, as a string. If the
 *                       description is missing then this component should not
 *                       be rendered at all. If the description is empty then
 *                       this string should be the empty string.
 * @param minimalStyling Whether minimal styling is applied.
 *                       Ignored when high contrast mode is requested, with the
 *                       borders always being shown.
 * @param className      Ignore; it is provided by the `styled` HOC.
 */
const DescriptionField = observer(
  ({
    file,
    description: initialDescription,
    minimalStyling = false,
  }: {
    file: GalleryFile;
    description: string;
    minimalStyling?: boolean;
  }) => {
    const { changeDescription } = useGalleryActions();
    const [description, setDescription] = React.useState<string>(initialDescription);
    const prefersMoreContrast = window.matchMedia("(prefers-contrast: more)").matches;
    return (
      <Stack>
        <TextField
          value={description}
          placeholder="No description"
          fullWidth
          size="small"
          sx={(theme) => ({
            width: "100%",
            [`& .${outlinedInputClasses.root}`]: {
              borderRadius: "4px",
              fontSize: "0.9rem",
              marginTop: theme.spacing(0.5),
              marginBottom: theme.spacing(0.5),
              backgroundColor: `hsl(${ACCENT_COLOR.main.hue}deg, ${ACCENT_COLOR.main.saturation}%, 90%)`,
            },
            [`& .${outlinedInputClasses.input}`]: {
              paddingLeft: theme.spacing(1),
            },
            ...(minimalStyling && !prefersMoreContrast
              ? {
                  ...(description !== initialDescription
                    ? {
                        [`& .${outlinedInputClasses.root}`]: {
                          backgroundColor: `hsl(${ACCENT_COLOR.main.hue}deg, ${ACCENT_COLOR.main.saturation}%, 90%)`,
                          [`& .${outlinedInputClasses.notchedOutline}`]: {
                            border: "none",
                          },
                        },
                      }
                    : {
                        [`& .${outlinedInputClasses.root}`]: {
                          backgroundColor: "unset",
                          [`& .${outlinedInputClasses.notchedOutline}`]: {
                            border: "none",
                          },
                        },
                      }),
                  [`& .${outlinedInputClasses.root}`]: {
                    border: "none",
                    borderRadius: "4px",
                    marginTop: theme.spacing(0.5),
                    marginBottom: theme.spacing(0.5),
                    transition: "all .3s ease-in-out",
                    "&:hover, &:focus-within": {
                      backgroundColor: `hsl(${ACCENT_COLOR.main.hue}deg, ${ACCENT_COLOR.main.saturation}%, 90%)`,
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
                }
              : {}),
          })}
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
          in={description !== initialDescription}
          timeout={window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200}
        >
          <Stack
            direction="row"
            spacing={0.5}
            sx={{
              justifyContent: "flex-end",
            }}
          >
            <Button
              size="small"
              onClick={() => {
                setDescription(initialDescription);
              }}
            >
              Cancel
            </Button>
            <Button
              size="small"
              variant="contained"
              color="callToAction"
              onClick={() => {
                void changeDescription(file, Description.Present(description));
              }}
            >
              Save
            </Button>
          </Stack>
        </Collapse>
      </Stack>
    );
  },
);
const formatDmpSource = (source: string): string => {
  switch (source) {
    case "UNKNOWN":
      return "Unknown";
    case "DMP_TOOL":
      return "DMP Tool";
    case "DMP_ONLINE":
      return "DMP Online";
    case "ARGOS":
      return "ARGOS";
    case "DSW":
      return "DSW / FAIR Wizard";
    default:
      return source;
  }
};
/**
 * Fetches an S3 filestore item's write-provenance (created-by / created-at) on demand when it is
 * selected, rather than HeadObject-ing every item during a folder listing. Returns nulls for
 * non-RemoteFiles, backends without provenance, or while the request is in flight.
 */
function useS3Provenance(file: GalleryFile): { createdBy: string | null; createdAt: Date | null } {
  const { getToken } = useOauthToken();
  const [audit, setAudit] = React.useState<{ createdBy: string | null; createdAt: Date | null }>({
    createdBy: null,
    createdAt: null,
  });
  const filestore = file.path[0];
  const remotePath = file instanceof RemoteFile ? file.remotePath : null;
  // S3-only; other backends have no provenance to fetch.
  const filestoreId =
    file instanceof RemoteFile && filestore instanceof Filestore && filestore.filesystemType === "S3"
      ? filestore.id
      : null;
  React.useEffect(() => {
    setAudit({ createdBy: null, createdAt: null });
    if (remotePath === null || filestoreId === null) return;
    let cancelled = false;
    void (async () => {
      try {
        const api = axios.create({
          baseURL: "/api/v1/gallery",
          headers: { Authorization: `Bearer ${await getToken()}` },
        });
        const { data } = await api.get<unknown>(
          `filestores/${idToString(filestoreId).elseThrow()}/metadata?remotePath=${encodeURIComponent(remotePath)}`,
        );
        if (cancelled) return;
        setAudit({
          createdBy: Parsers.objectPath(["createdBy"], data).flatMap(Parsers.isString).orElse(null),
          createdAt: Parsers.objectPath(["createdAt"], data)
            .flatMap(Parsers.isString)
            .flatMap(Parsers.parseDate)
            .orElse(null),
        });
      } catch {
        // provenance is supplementary; ignore failures
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [remotePath, filestoreId, getToken]);
  return audit;
}

const InfoPanelContent = observer(
  ({ file, smallViewport = false }: { file: GalleryFile; smallViewport?: boolean }): React.ReactNode => {
    const s3Provenance = useS3Provenance(file);
    return (
      <Stack
        sx={{
          height: "100%",
        }}
      >
        <DescriptionList
          content={[
            ...(typeof file.globalId === "string"
              ? [
                  {
                    label: "Global ID",
                    value: file.globalId,
                  },
                ]
              : []),
            ...(typeof file.ownerName === "string"
              ? [
                  {
                    label: "Owner",
                    value: file.ownerName,
                  },
                ]
              : []),
            ...file.description
              .toString()
              .map((desc) => [
                {
                  label: "Description",
                  value: <DescriptionField file={file} description={desc} minimalStyling={!smallViewport} />,
                  below: true,
                },
              ])
              .orElse(
                [] as ReadonlyArray<{
                  label: string;
                  value: React.ReactNode;
                  below?: boolean;
                  reducedPadding?: boolean;
                }>,
              ),
          ]}
          sx={{
            pl: 2,
            "& dd.below": {
              justifySelf: "start",
              width: "100%",
            },
          }}
        />
        {(file.metadata.doiLink || file.metadata.dmpLink || file.metadata.dmpSource) && (
          <Box
            component="section"
            sx={{
              mt: 0.5,
            }}
          >
            <Typography variant="h4" component="h4">
              DMP Details
            </Typography>
            <DescriptionList
              content={[
                ...(file.metadata.dmpLink
                  ? [
                      {
                        label: "Link",
                        value: (
                          <Link href={file.metadata.dmpLink} target="_blank" rel="noopener noreferrer">
                            {file.metadata.dmpLink}
                          </Link>
                        ),
                      },
                    ]
                  : []),
                ...(file.metadata.dmpSource
                  ? [
                      {
                        label: "Source",
                        value: (
                          <Chip label={formatDmpSource(file.metadata.dmpSource)} size="small" variant="outlined" />
                        ),
                      },
                    ]
                  : []),
                ...(file.metadata.doiLink
                  ? [
                      {
                        label: "DOI Link",
                        value: (
                          <Link href={file.metadata.doiLink} target="_blank" rel="noopener noreferrer">
                            {file.metadata.doiLink}
                          </Link>
                        ),
                      },
                    ]
                  : []),
              ]}
              sx={{
                pl: 2,
                "& dd.below": {
                  justifySelf: "start",
                  width: "100%",
                },
              }}
            />
          </Box>
        )}
        <Box
          component="section"
          sx={{
            mt: 0.5,
          }}
        >
          <Typography variant="h4" component="h4">
            Details
          </Typography>
          <DescriptionList
            content={[
              ...(typeof file.type === "string"
                ? [
                    {
                      label: "Type",
                      value: file.type,
                    },
                  ]
                : []),
              {
                label: "Size",
                value: formatFileSize(file.size),
              },
              ...(file.creationDate
                ? [
                    {
                      label: "Created",
                      value: file.creationDate.toLocaleString(),
                    },
                  ]
                : []),
              ...(file.modificationDate
                ? [
                    {
                      label: "Modified",
                      value: file.modificationDate.toLocaleString(),
                    },
                  ]
                : []),
              ...(typeof file.version === "number"
                ? [
                    {
                      label: "Version",
                      value: file.version,
                    },
                  ]
                : []),
              ...(typeof file.originalImageId === "string"
                ? [
                    {
                      label: "Original Image ID",
                      value: file.originalImageId,
                    },
                  ]
                : []),
              // RSpace-stamped write provenance, kept distinct from the object's real Owner/Created.
              ...(s3Provenance.createdBy
                ? [
                    {
                      label: "Added to S3 by",
                      value: s3Provenance.createdBy,
                    },
                  ]
                : []),
              ...(s3Provenance.createdAt
                ? [
                    {
                      label: "Added to S3 on",
                      value: s3Provenance.createdAt.toLocaleString(),
                    },
                  ]
                : []),
            ]}
            sx={{
              pl: 2,
            }}
          />
        </Box>
        {file.linkedDocuments}
        {/* Inventory items can only link to gallery media files (GL...); folders (GF),
            snippets (ST) etc. 404 and would show a spurious error. See PRT-1091. */}
        {file.globalId?.startsWith("GL") && <ReferencingInventoryItemsPanel file={file} />}
      </Stack>
    );
  },
);
const InfoPanelMultipleContent = (): React.ReactNode => {
  const selection = useGallerySelection();
  const sortedByCreated = selection
    .asSet()
    .mapOptional((file) => (!file.creationDate ? Optional.empty<Date>() : Optional.present(file.creationDate)))
    .toArray((dateA, dateB) => dateA.getTime() - dateB.getTime());
  const sortedByModified = selection
    .asSet()
    .mapOptional((file) => (!file.modificationDate ? Optional.empty<Date>() : Optional.present(file.modificationDate)))
    .toArray((dateA, dateB) => dateA.getTime() - dateB.getTime());
  return (
    <DescriptionList
      content={[
        {
          label: "Total size",
          value: formatFileSize(selection.asSet().reduce((sum, file) => sum + file.size, 0)),
        },
        ...Result.lift2<
          Date,
          Date,
          Array<{
            label: string;
            value: React.ReactNode;
          }>
        >((oldestDate, newestDate) => [
          {
            label: "Created",
            value: (
              <>
                {oldestDate.toLocaleDateString()} &ndash; {newestDate.toLocaleDateString()}
              </>
            ),
          },
        ])(
          Result.fromNullable(sortedByCreated.at(0), new Error("No creation dates available.")),
          Result.fromNullable(sortedByCreated.at(-1), new Error("No creation dates available.")),
        ).orElse([]),
        ...Result.lift2<
          Date,
          Date,
          Array<{
            label: string;
            value: React.ReactNode;
          }>
        >((oldestDate, newestDate) => [
          {
            label: "Modified",
            value: (
              <>
                {oldestDate.toLocaleDateString()} &ndash; {newestDate.toLocaleDateString()}
              </>
            ),
          },
        ])(
          Result.fromNullable(sortedByModified.at(0), new Error("No modification dates available.")),
          Result.fromNullable(sortedByModified.at(-1), new Error("No modification dates available.")),
        ).orElse([]),
      ]}
    />
  );
};
/**
 * On larger viewports, the info panel is shown in the right column of the
 * gallery. This component is responsible for rendering the metadata of the
 * selected file, including the name, description, and details such as the
 * documents that link to the file.
 */
export function InfoPanelForLargeViewports() {
  const selection = useGallerySelection();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openSnapGenePreview } = useSnapGenePreview();
  const { openSnippetPreview } = useSnippetPreview();
  const { openAsposePreview, loading: asposeLoading } = useAsposePreview();
  const primaryAction = usePrimaryAction();
  const { openFolder } = useFolderOpen();
  const { trackEvent } = React.useContext(AnalyticsContext);
  return (
    <>
      <Grid
        container
        direction="row"
        spacing={0.5}
        sx={{
          alignItems: "flex-start",
          flexWrap: "nowrap",
          marginLeft: "-10px",
          marginTop: "-8px",
          width: "calc(100% + 9px)",
        }}
      >
        <Grid
          sx={{
            flexShrink: 1,
            flexGrow: 1,
          }}
        >
          {selection
            .asSet()
            .only.toResult(() => new Error("Empty or multiple selected"))
            .flatMapDiscarding((f) => f.canRename)
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
              </Typography>,
            )}
        </Grid>
        {selection
          .asSet()
          .only.map((file) =>
            primaryAction(file)
              .map((action) => {
                if (action.tag === "open")
                  return (
                    <Grid key={null}>
                      <ActionButton
                        onClick={() => {
                          openFolder(file);
                        }}
                        label="Open"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "image")
                  return (
                    <Grid key={null}>
                      <ActionButton
                        onClick={() => {
                          void action.downloadHref().then((url) => {
                            openImagePreview(url, {
                              caption: action.caption,
                            });
                          });
                        }}
                        label="View"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "collabora")
                  return (
                    <Grid
                      sx={{
                        mt: 0.5,
                        mb: 0.25,
                      }}
                      key={null}
                    >
                      <ActionButton
                        onClick={() => {
                          window.open(action.url);
                          trackEvent("user:opens:document:collabora");
                        }}
                        label="Edit"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "officeonline")
                  return (
                    <Grid
                      sx={{
                        mt: 0.5,
                        mb: 0.25,
                      }}
                      key={null}
                    >
                      <ActionButton
                        onClick={() => {
                          window.open(action.url);
                          trackEvent("user:opens:document:officeonline");
                        }}
                        label="Edit"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "pdf")
                  return (
                    <Grid
                      sx={{
                        mt: 0.5,
                        mb: 0.25,
                      }}
                      key={null}
                    >
                      <ActionButton
                        onClick={() => {
                          void action.downloadHref().then((href) => {
                            openPdfPreview(href);
                          });
                        }}
                        label="View"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "aspose")
                  return (
                    <Grid key={null}>
                      <ActionButton
                        disabled={asposeLoading}
                        onClick={() => {
                          void openAsposePreview(file);
                        }}
                        label={asposeLoading ? "Loading" : "View"}
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "snapgene")
                  return (
                    <Grid
                      sx={{
                        mt: 0.5,
                        mb: 0.25,
                      }}
                      key={null}
                    >
                      <ActionButton
                        onClick={() => {
                          void openSnapGenePreview(file);
                        }}
                        label="View"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                if (action.tag === "snippet")
                  return (
                    <Grid
                      sx={{
                        mt: 0.5,
                        mb: 0.25,
                      }}
                      key={null}
                    >
                      <ActionButton
                        onClick={() => {
                          void openSnippetPreview(file);
                        }}
                        label="View"
                        sx={{
                          height: "100%",
                          marginTop: "8px",
                        }}
                      />
                    </Grid>
                  );
                return null;
              })
              .orElseGet((errors) => {
                console.info("Could not provide view", errors);
                return (
                  <Grid
                    sx={{
                      mt: 0.5,
                      mb: 0.25,
                    }}
                  >
                    <ActionButton
                      onClick={() => {
                        // do nothing
                      }}
                      disabled
                      label="View"
                      sx={{
                        height: "100%",
                        marginTop: "8px",
                      }}
                    />
                  </Grid>
                );
              }),
          )
          .orElse(null)}
      </Grid>
      {selection
        .asSet()
        .only.map((f) => (
          <CardContent
            sx={{
              p: 1,
              pr: 0.5,
            }}
            key={null}
          >
            <InfoPanelContent file={f} />
          </CardContent>
        ))
        .orElse(null)}
      {selection.size > 1 && (
        <CardContent
          sx={{
            p: 1,
            pr: 0.5,
          }}
          key={null}
        >
          <InfoPanelMultipleContent />
        </CardContent>
      )}
    </>
  );
}

/**
 * On smaller viewports, the info panel is shown in a floating panel that
 * slides up from the bottom of the screen when the user has selected a file.
 * Initially, only the name of the file is shown with the primary action
 * button, with the rest of the metadata shown when the panel is expanded. This
 * other data includes the name, description, and details such as the documents
 * that link to the file.
 */
export const InfoPanelForSmallViewports: React.ComponentType<{
  file: GalleryFile;
}> = ({ file }) => {
  const [mobileInfoPanelOpen, setMobileInfoPanelOpen] = React.useState(false);
  const [previewSize, setPreviewSize] = React.useState<null | PreviewSize>(null);
  const [previewImageUrl, setPreviewImageUrl] = React.useState<null | string>(null);
  const selection = useGallerySelection();
  const mobileInfoPanelId = React.useId();
  const { openFolder } = useFolderOpen();
  const { trackEvent } = React.useContext(AnalyticsContext);
  return (
    <SwipeableDrawer
      key={null}
      anchor="bottom"
      open={mobileInfoPanelOpen}
      sx={{
        // z-index stays at the default drawer level; the picker raises it via theme.
        [`& .${paperClasses.root}`]: {
          height: `calc(90% - ${CLOSED_MOBILE_INFO_PANEL_HEIGHT}px)`,
          overflow: "visible",
        },
        display: {
          xs: "block",
          md: "none",
        },
        touchAction: "none",
      }}
      onClose={() => {
        setMobileInfoPanelOpen(false);
      }}
      onOpen={() => {
        setMobileInfoPanelOpen(true);
        trackEvent("user:opens:mobileInfoPanel:gallery");
      }}
      swipeAreaWidth={CLOSED_MOBILE_INFO_PANEL_HEIGHT}
      disableSwipeToOpen={false}
      ModalProps={{
        keepMounted: true,
        "aria-hidden": false,
      }}
      allowSwipeInChildren={(event) => {
        if ((event.target as HTMLElement | null)?.id === "open") return false;
        return true;
      }}
      slotProps={{
        swipeArea: {
          sx: {
            display: {
              xs: "block",
              md: "none",
            },
          },
        },
      }}
    >
      {/*
       * Wraps all of the floating info panel's content, positioning it so the
       * title and action button remain visible even when the panel is closed.
       */}
      <Box
        sx={(theme) => ({
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
        })}
      >
        <Stack
          spacing={1}
          sx={{
            height: "100%",
          }}
          role="region"
          aria-label="info panel"
          id={mobileInfoPanelId}
        >
          {/*
           * Drawer "puller": on touch devices a visual indicator that the
           * panel can be swiped open/closed, on non-touch small viewports a
           * tap target that toggles the floating panel.
           */}
          <Box
            component="button"
            onClick={() => setMobileInfoPanelOpen(!mobileInfoPanelOpen)}
            onKeyDown={(e) => {
              if (e.key === " ") setMobileInfoPanelOpen(!mobileInfoPanelOpen);
            }}
            role="button"
            tabIndex={0}
            aria-controls={mobileInfoPanelId}
            aria-expanded={mobileInfoPanelOpen ? "true" : "false"}
            sx={{
              width: 30,
              height: 6,
              backgroundColor: grey[300],
              borderRadius: 3,
              position: "absolute",
              top: 8,
              left: "calc(50% - 15px)",
            }}
          />
          <CardContent>
            <Grid
              container
              direction="row"
              spacing={2}
              sx={{
                flexWrap: "nowrap",
                mb: 2,
                minHeight: "54px",
              }}
            >
              <Grid
                sx={{
                  flexShrink: 1,
                  flexGrow: 1,
                  mt: 0.5,
                }}
              >
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
              {file.canOpen
                .map(() => (
                  <Grid key={null}>
                    <ActionButton
                      label="Open"
                      sx={{
                        borderRadius: 3,
                        px: 2.5,
                        py: 0.5,
                      }}
                      onClick={(e) => {
                        e.stopPropagation();
                        openFolder(file);
                        setMobileInfoPanelOpen(false);
                      }}
                    />
                  </Grid>
                ))
                .orElse(null)}
              {file.isImage && file.downloadHref && (
                <Grid>
                  <ActionButton
                    onClick={() => {
                      if (file.downloadHref)
                        void file.downloadHref().then((url) => {
                          setPreviewImageUrl(url);
                        });
                    }}
                    label="View"
                    sx={{
                      borderRadius: 3,
                      px: 2.5,
                      py: 0.5,
                    }}
                  />
                  {previewImageUrl && (
                    <ImagePreview
                      closePreview={() => {
                        setPreviewImageUrl(null);
                      }}
                      link={previewImageUrl}
                      size={previewSize}
                      setSize={(s) => setPreviewSize(s)}
                    />
                  )}
                </Grid>
              )}
            </Grid>
            {selection
              .asSet()
              .only.map((f) => <InfoPanelContent key={null} file={f} smallViewport />)
              .orElse(null)}
          </CardContent>
        </Stack>
      </Box>
    </SwipeableDrawer>
  );
};
