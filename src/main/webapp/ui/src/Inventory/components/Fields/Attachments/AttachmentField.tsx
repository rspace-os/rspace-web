import React, { type ReactNode } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../../stores/use-stores";
import { justFilenameExtension } from "@/util/files";
import TableContainer from "@mui/material/TableContainer";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import FileField from "../../../../components/Inputs/FileField";
import NoValue from "../../../../components/NoValue";
import AttachmentTableRow from "./AttachmentTableRow";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import Stack from "@mui/material/Stack";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import type { HasEditableFields } from "@/stores/definitions/Editable";
import type { BlobUrl } from "@/util/types";
import type { Attachment } from "@/stores/definitions/Attachment";
import type { GalleryFile } from "@/eln/gallery/useGalleryListing";
import UploadIcon from "@mui/icons-material/Publish";
import BigIconButton from "../../../../components/BigIconButton";
import Result from "../../../../util/result";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import * as FetchingData from "../../../../util/fetchingData";
import * as Parser from "../../../../util/parsers";

const GalleryPicker = React.lazy(
  () => import("../../../../eln/gallery/picker"),
);

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
  FieldOwner extends HasEditableFields<Fields>,
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
  const { trackingStore } = useStores();
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);

  const chemistryProvider = FetchingData.getSuccessValue(
    useDeploymentProperty("chemistry.provider"),
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
    <Stack>
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
          fullWidth
          sx={{ "& .MuiInputBase-input": { fontSize: "14px" } }}
        />
      )}
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
        <Box sx={{ mt: 1 }}>
          <Alert severity="info" role="none">
            <AlertTitle>Attachment</AlertTitle>A file of any type can be
            attached (e.g. image, document, or chemistry file)
          </Alert>
        </Box>
      )}
      {!disableFileUpload && !disabled && (
        /* this should be disabled, no? what is disableFileUpload? */
        <Box sx={{ mt: 1 }}>
          <Stack spacing={1}>
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
                sx: { alignItems: "stretch", flexDirection: "column" },
              }}
              slotProps={{
                input: {
                  startAdornment: (
                    <BigIconButton
                      onClick={() => {
                        setGalleryDialogOpen(true);
                      }}
                      icon={<AttachFileIcon />}
                      label="Browse Gallery"
                      explanatoryText="Link to existing items in the Gallery."
                    />
                  ),
                },
              }}
            />
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
                            "Snippets cannot be attached to Inventory records.",
                          ),
                        ])
                      : Result.Ok(null)
                  }
                />
              </React.Suspense>
            )}
            {!attachment && (
              <Box sx={{ pl: 2 }}>
                <NoValue label="No File Attached" />
              </Box>
            )}
          </Stack>
        </Box>
      )}
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
    </Stack>
  );
}

export default observer(AttachmentField);
