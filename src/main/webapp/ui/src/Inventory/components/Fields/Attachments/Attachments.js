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
import AddButton from "../../../../components/AddButton";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import { newAttachment } from "../../../../stores/models/AttachmentModel";
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

const CustomCardHeader = withStyles<
  ElementProps<typeof CardHeader>,
  { root: string, action: string }
>((theme) => ({
  root: {
    padding: theme.spacing(0, 0, 0, 1.5),
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

const AddButtonWrapper = ({
  disabled,
  id,
}: {|
  disabled: boolean,
  id: string,
|}): Node => (
  <label htmlFor={id}>
    <AddButton
      disabled={disabled}
      title={disabled ? "Press Edit to add a file" : "Select a file to attach"}
    />
  </label>
);

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
    <FileField
      accept="*"
      buttonLabel="Add Attachment"
      name="attachments"
      onChange={onFileSelection}
      showSelectedFilename={false}
      icon={<AttachFileIcon />}
      loading={false}
      error={false}
      key={0}
      disabled={!editable}
      triggerButton={({ id }) => (
        <AddButtonWrapper disabled={!editable} id={id} />
      )}
    />
  );
};

const FilesCard = observer(function FilesCard<
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({ fieldOwner }: { fieldOwner?: FieldOwner }): Node {
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
            <FileSelector
              activeResult={activeResult}
              setOpen={setOpen}
              editable={editable}
            />
            <ToggleButton
              attachmentCount={attachments.length}
              open={open}
              setOpen={setOpen}
            />
          </>
        }
      />
      <Collapse in={open}>
        <CollapseContents
          attachments={attachments}
          fieldOwner={fieldOwner}
          editable={editable}
        />
      </Collapse>
    </Card>
  );
});

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
