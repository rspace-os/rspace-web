//@flow

import React, {
  useState,
  useEffect,
  type Node,
  type ElementProps,
} from "react";
import { observer } from "mobx-react-lite";
import FileField from "../../../../components/Inputs/FileField";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import docLinks from "../../../../assets/DocLinks";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import {
  newAttachment,
  newGalleryAttachment,
} from "../../../../stores/models/AttachmentModel";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import useStores from "../../../../stores/use-stores";
import { match } from "../../../../util/Util";
import { justFilenameExtension } from "../../../../util/files";
import AttachmentTableRow from "./AttachmentTableRow";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { withStyles } from "Styles";
import AttachFileIcon from "@mui/icons-material/AttachFile";
import { type Attachment } from "../../../../stores/definitions/Attachment";
import { type HasEditableFields } from "../../../../stores/definitions/Editable";
import { type BlobUrl } from "../../../../stores/stores/ImageStore";
import BigIconButton from "../../../../components/BigIconButton";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";
import UploadIcon from "@mui/icons-material/Publish";
import Result from "../../../../util/result";

const GalleryPicker = React.lazy(() =>
  import("../../../../eln/gallery/picker")
);

const CustomCardHeader = withStyles<
  ElementProps<typeof CardHeader>,
  { root: string, action: string }
>((theme) => ({
  root: {
    padding: theme.spacing(0.5, 1.5, 0.25, 2),
  },
  action: {
    margin: 0,
  },
}))(CardHeader);

const CollapseContents = <
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  attachments,
  fieldOwner,
  editable,
}: {
  attachments: Array<Attachment>,
  fieldOwner?: FieldOwner,
  editable: boolean,
}): Node => {
  return (
    <Box mt={1}>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {attachments.map((a, i) => (
              <AttachmentTableRow
                attachment={a}
                key={i}
                fieldOwner={fieldOwner}
                editable={editable}
              />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};

const ToggleButton = ({
  attachmentCount,
  open,
  setOpen,
}: {
  attachmentCount: number,
  open: boolean,
  setOpen: (boolean) => void,
}): Node => (
  <CustomTooltip
    title={match<void, string>([
      [() => attachmentCount === 0, "No current attachments"],
      [() => open, "Hide attachment listing"],
      [() => true, "Show attachment listing"],
    ])()}
  >
    <IconButton onClick={() => setOpen(!open)} disabled={attachmentCount === 0}>
      <Badge color="primary" badgeContent={attachmentCount}>
        <ExpandCollapseIcon open={open} />
      </Badge>
    </IconButton>
  </CustomTooltip>
);

const FileSelector = ({
  activeResult,
  setOpen,
  editable,
}: {
  activeResult: InventoryRecord,
  setOpen: (boolean) => void,
  editable: boolean,
}): Node => {
  const { trackingStore } = useStores();
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);

  const onFileSelection = ({ file }: { file: File }) => {
    activeResult.setAttributesDirty({
      attachments: [
        ...activeResult.attachments,
        newAttachment(file, activeResult.permalinkURL, () =>
          activeResult.setAttributesDirty({})
        ),
      ],
    });
    setOpen(true);
    trackingStore.trackEvent("AddedAttachment", {
      extension: justFilenameExtension(file.name),
      contentMimeType: file.type,
    });
  };

  return (
    <>
      <FileField
        accept="*"
        buttonLabel="Upload"
        name="attachments"
        onChange={onFileSelection}
        showSelectedFilename={false}
        icon={<UploadIcon />}
        loading={false}
        error={false}
        key={0}
        disabled={!editable}
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
      {galleryDialogOpen && (
        <React.Suspense fallback={<></>}>
          <GalleryPicker
            open={true}
            onClose={() => {
              setGalleryDialogOpen(false);
            }}
            onSubmit={(files) => {
              activeResult.setAttributesDirty({
                attachments: [
                  ...activeResult.attachments,
                  ...files.map((f) =>
                    newGalleryAttachment(f, () =>
                      activeResult.setAttributesDirty({})
                    )
                  ),
                ],
              });
              setGalleryDialogOpen(false);
            }}
            validateSelection={(file) => {
              if (file.isSnippet)
                return Result.Error([
                  new Error(
                    "Snippets cannot be attached to Inventory records."
                  ),
                ]);
              if (!file.globalId)
                return Result.Error([
                  // some of the files will be from filestores
                  new Error(`"${file.name}" does not have an RSpace Global Id`),
                ]);
              return Result.Ok(null);
            }}
          />
        </React.Suspense>
      )}
    </>
  );
};

const FilesCard = observer(
  <
    Fields: {
      image: ?BlobUrl,
      newBase64Image: ?string,
      ...
    },
    FieldOwner: HasEditableFields<Fields>
  >({
    fieldOwner,
  }: {
    fieldOwner?: FieldOwner,
  }): Node => {
    const [open, setOpen] = useState(false);

    const {
      searchStore: { activeResult },
    } = useStores();
    if (!activeResult) throw new Error("ActiveResult must be a Record");
    const editable = activeResult.isFieldEditable("attachments");
    const attachments = activeResult.attachments ?? [];

    useEffect(() => {
      setOpen(attachments.length > 0);
    }, [attachments]);

    return (
      <Card variant="outlined">
        <CustomCardHeader
          subheader="Attach files of any type, e.g. images, documents, or chemistry files."
          subheaderTypographyProps={{ variant: "body2" }}
          action={
            <>
              <ToggleButton
                attachmentCount={attachments.length}
                open={open}
                setOpen={setOpen}
              />
            </>
          }
        />
        {editable && (
          <CardContent sx={{ pt: 0.5 }}>
            <FileSelector
              activeResult={activeResult}
              setOpen={setOpen}
              editable={editable}
            />
          </CardContent>
        )}
        <Collapse in={open}>
          <CollapseContents
            attachments={attachments}
            fieldOwner={fieldOwner}
            editable={editable}
          />
        </Collapse>
      </Card>
    );
  }
);

function Attachments<
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({ fieldOwner }: { fieldOwner?: FieldOwner }): Node {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");

  return (
    <InputWrapper
      label=""
      error={false}
      explanation={
        activeResult.isFieldEditable("attachments") ? (
          <>
            See the documentation for information on{" "}
            <a href={docLinks.attachments} target="_blank" rel="noreferrer">
              adding attachments
            </a>
            .
          </>
        ) : null
      }
    >
      <FilesCard fieldOwner={fieldOwner} />
    </InputWrapper>
  );
}

export default (observer(Attachments): typeof Attachments);
