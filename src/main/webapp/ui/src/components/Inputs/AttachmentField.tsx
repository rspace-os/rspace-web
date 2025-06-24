import React, { type ReactNode } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import { justFilenameExtension } from "../../util/files";
import TableContainer from "@mui/material/TableContainer";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import FileField from "../../components/Inputs/FileField";
import NoValue from "../../components/NoValue";
import AttachmentTableRow from "../../Inventory/components/Fields/Attachments/AttachmentTableRow";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import Grid from "@mui/material/Grid";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type { HasEditableFields } from "../../stores/definitions/Editable";
import { type BlobUrl } from "../../util/types";
import type { Attachment } from "../../stores/definitions/Attachment";
import { type GalleryFile } from "../../eln/gallery/useGalleryListing";
import UploadIcon from "@mui/icons-material/Publish";
import BigIconButton from "../BigIconButton";
import Result from "../../util/result";
import { useDeploymentProperty } from "../../eln/useDeploymentProperty";
import * as FetchingData from "../../util/fetchingData";
import * as Parser from "../../util/parsers";

const GalleryPicker = React.lazy(() => import("../../eln/gallery/picker"));

const useStyles = makeStyles()(() => ({
  descriptionText: {
    fontSize: "14px",
  },
}));

export type AttachmentFieldArgs<FieldOwner> = {
  attachment: Attachment | null;
  onAttachmentChange: (file: File | GalleryFile) => void;
  onChange: React.ChangeEventHandler<HTMLInputElement | HTMLTextAreaElement>;
  value: string; // for description

  /**
   * This is used for setting the preview image, if the attachment can be
   * displayed as an image.
   */
  fieldOwner: FieldOwner;

  disabled?: boolean;

  /*
   * There are times when we want to allow the user to provide a description of
   * an attachment whilst not being able to attach any actual files, for
   * example on templates. That is when this prop should be true.
   */
  disableFileUpload?: boolean;

  error?: boolean;
  helperText?: string;
  noValueLabel?: string | null;
};

/*
 * Lets you select, attach, save, preview and download a single file.
 */
function AttachmentField<
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>
>({
  attachment,
  disableFileUpload,
  disabled,
  error = false,
  helperText = "",
  noValueLabel,
  onAttachmentChange,
  onChange,
  value,
  fieldOwner,
}: AttachmentFieldArgs<FieldOwner>): ReactNode {
  const { classes } = useStyles();
  const { trackingStore } = useStores();
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);

  const chemistryProvider = FetchingData.getSuccessValue(
    useDeploymentProperty("chemistry.provider")
  )
    .flatMap(Parser.isString)
    .orElse("");

  const onFileSelection = (file: File | GalleryFile) => {
    if (typeof file.type !== "string") throw new Error("Unknown file type");

    onAttachmentChange(file);

    trackingStore.trackEvent("AddedFieldAttachment", {
      extension: justFilenameExtension(file.name),
      contentMimeType: file.type,
    });
  };

  return (
    <Grid container direction="column">
      <Grid item>
        {!value && disabled ? (
          <NoValue label={noValueLabel ?? "No description"} />
        ) : (
          <TextField
            variant={disabled ? "standard" : "outlined"}
            size="small"
            multiline
            value={value}
            onChange={onChange}
            error={error}
            helperText={!error ? null : helperText}
            disabled={disabled}
            InputProps={{
              classes: {
                input: classes.descriptionText,
              },
            }}
            fullWidth
          />
        )}
      </Grid>
      <Grid item>
        {/*
         * This help text, which provides examples of what types of files can
         * be attached to the sample, is not shown if the `value` has been set
         * because the `value` is used by the attachment field solely as a
         * descriptive piece of text that the author of a template can use to
         * describe the kinds of file that ought to be attached. If no such
         * description has been set, and thus `value` is the empty string,
         * then we show our suggestive text. This text is further shown in an
         * info alert to make it clear that this is a suggestion coming from
         * the RSpace product and not their colleagues.
         */}
        {!value && (
          <Box mt={1}>
            <Alert severity="info" role="none">
              <AlertTitle>Attachment</AlertTitle>A file of any type can be
              attached (e.g. image, document, or chemistry file)
            </Alert>
          </Box>
        )}
      </Grid>
      {!disableFileUpload && !disabled && (
        /* this should be disabled, no? what is disableFileUpload? */ <Grid
          item
          sx={{ mt: 1 }}
        >
          <Grid container direction="column" spacing={1}>
            <Grid item>
              <FileField
                accept="*"
                buttonLabel="Upload"
                onChange={({ file }) => onFileSelection(file)}
                showSelectedFilename={false}
                icon={<UploadIcon />}
                loading={false}
                error={false}
                disabled={disabled}
                explanatoryText="Upload a file from your device."
                containerProps={{
                  wrap: "nowrap",
                  alignItems: "stretch",
                  flexDirection: "column",
                }}
                InputProps={{
                  startAdornment: (
                    <Grid item>
                      <BigIconButton
                        onClick={() => {
                          setGalleryDialogOpen(true);
                        }}
                        icon={<AttachFileIcon />}
                        label="Browse Gallery"
                        explanatoryText="Link to existing items in the Gallery."
                      />
                    </Grid>
                  ),
                }}
              />
            </Grid>
            {galleryDialogOpen && (
              <React.Suspense fallback={<></>}>
                <GalleryPicker
                  open={true}
                  onClose={() => {
                    setGalleryDialogOpen(false);
                  }}
                  onSubmit={(files) => {
                    files.only.do((file) => {
                      onFileSelection(file);
                    });
                    setGalleryDialogOpen(false);
                  }}
                  onlyAllowSingleSelection
                  validateSelection={(file) =>
                    file.isSnippet
                      ? Result.Error([
                          new Error(
                            "Snippets cannot be attached to Inventory records."
                          ),
                        ])
                      : Result.Ok(null)
                  }
                />
              </React.Suspense>
            )}
            <Grid item>
              {!attachment && (
                <Box pl={2}>
                  <NoValue label="No File Attached" />
                </Box>
              )}
            </Grid>
          </Grid>
        </Grid>
      )}
      <Grid item>
        {attachment && (
          <TableContainer>
            <Table size="small">
              <TableBody>
                <AttachmentTableRow
                  attachment={attachment}
                  editable={!disabled}
                  fieldOwner={fieldOwner}
                  chemistryProvider={chemistryProvider}
                />
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Grid>
    </Grid>
  );
}

export default observer(AttachmentField);