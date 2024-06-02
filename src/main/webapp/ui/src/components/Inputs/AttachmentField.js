//@flow

import React, { type Node } from "react";
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
import AddButton from "../../components/AddButton";
import AttachmentTableRow from "../../Inventory/components/Fields/Attachments/AttachmentTableRow";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import Grid from "@mui/material/Grid";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type { HasEditableFields } from "../../stores/definitions/Editable";
import { type BlobUrl } from "../../stores/stores/ImageStore";
import type { Attachment } from "../../stores/definitions/Attachment";

const useStyles = makeStyles()(() => ({
  row: {
    alignItems: "center",
    width: "100%",
  },
  descriptionText: {
    fontSize: "14px",
  },
}));

export type AttachmentFieldArgs<FieldOwner> = {|
  // required
  attachment: ?Attachment,
  onAttachmentChange: (File) => void,
  onChange: ({| target: HTMLInputElement |}) => void,
  value: string, // for description

  /**
   * This is used for setting the preview image, if the attachment can be
   * displayed as an image.
   */
  fieldOwner: FieldOwner,

  // optional
  disabled?: boolean,
  disableFileUpload?: boolean,
  error?: boolean,
  helperText?: string,
  noValueLabel?: ?string,
|};

/*
 * Lets you select, attach, save, preview and download a single file.
 */
function AttachmentField<
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
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
}: AttachmentFieldArgs<FieldOwner>): Node {
  const { classes } = useStyles();
  const { trackingStore } = useStores();

  const onFileSelection = ({ file }: { file: File, ... }) => {
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
      {!disableFileUpload && (
        <Grid item>
          <Grid container direction="row" className={classes.row}>
            <Grid item>
              <FileField
                accept="*"
                buttonLabel="Add Attachment"
                onChange={onFileSelection}
                showSelectedFilename={false}
                icon={<AttachFileIcon />}
                loading={false}
                error={false}
                disabled={disabled}
                triggerButton={({ id }) => (
                  // this label is not for a11y, but just to make the button trigger the FileField
                  <label htmlFor={id}>
                    <AddButton
                      disabled={disabled}
                      title={
                        disabled
                          ? `Press Edit to ${
                              attachment ? "replace the" : "select a"
                            } file`
                          : attachment
                          ? "Replace file"
                          : "Select a file to attach"
                      }
                    />
                  </label>
                )}
              />
            </Grid>
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
                />
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Grid>
    </Grid>
  );
}

export default (observer(AttachmentField): typeof AttachmentField);
