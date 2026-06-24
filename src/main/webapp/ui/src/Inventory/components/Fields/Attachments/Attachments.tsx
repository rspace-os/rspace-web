import AttachFileIcon from "@mui/icons-material/AttachFile";
import UploadIcon from "@mui/icons-material/Publish";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import docLinks from "../../../../assets/DocLinks";
import BigIconButton from "../../../../components/BigIconButton";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import FileField from "../../../../components/Inputs/FileField";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import { useDeploymentProperty } from "../../../../hooks/api/useDeploymentProperty";
import type { Attachment } from "../../../../stores/definitions/Attachment";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { newAttachment, newGalleryAttachment } from "../../../../stores/models/AttachmentModel";
import useStores from "../../../../stores/use-stores";
import * as FetchingData from "../../../../util/fetchingData";
import { justFilenameExtension } from "../../../../util/files";
import * as Parser from "../../../../util/parsers";
import Result from "../../../../util/result";
import type { BlobUrl } from "../../../../util/types";
import { match } from "../../../../util/Util";
import AttachmentTableRow from "./AttachmentTableRow";

const GalleryPicker = React.lazy(() => import("../../../../eln/gallery/picker"));
const CollapseContents = <
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  attachments,
  fieldOwner,
  editable,
}: {
  attachments: Array<Attachment>;
  fieldOwner?: FieldOwner;
  editable: boolean;
}): ReactNode => {
  const { t } = useTranslation("inventory");
  const chemistryProvider = FetchingData.getSuccessValue(useDeploymentProperty("chemistry.provider"))
    .flatMap(Parser.isString)
    .orElse("");
  return (
    <Box
      sx={{
        mt: 1,
      }}
    >
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t("fields.attachments.columns.name")}</TableCell>
              <TableCell>{t("fields.attachments.columns.actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {attachments.map((a, i) => (
              <AttachmentTableRow
                attachment={a}
                key={i}
                fieldOwner={fieldOwner}
                editable={editable}
                chemistryProvider={chemistryProvider}
              />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
};
const FileSelector = ({
  activeResult,
  setOpen,
  editable,
}: {
  activeResult: InventoryRecord;
  setOpen: (value: boolean) => void;
  editable: boolean;
}): ReactNode => {
  const { t } = useTranslation("inventory");
  const { trackingStore } = useStores();
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);
  const onFileSelection = ({ file }: { file: File }) => {
    activeResult.setAttributesDirty({
      attachments: [
        ...activeResult.attachments,
        newAttachment(file, activeResult.permalinkURL, () => activeResult.setAttributesDirty({})),
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
        buttonLabel={t("fields.attachments.actions.upload")}
        name="attachments"
        onChange={onFileSelection}
        showSelectedFilename={false}
        icon={<UploadIcon />}
        loading={false}
        error={false}
        key={0}
        disabled={!editable}
        explanatoryText={t("fields.attachments.uploadFromDevice")}
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
                label={t("fields.attachments.actions.browseGallery")}
                explanatoryText={t("fields.attachments.linkGalleryItems")}
              />
            ),
          },
        }}
      />
      {galleryDialogOpen && (
        // biome-ignore lint/complexity/noUselessFragments: initial biome migration
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
                  ...files.map((f) => newGalleryAttachment(f, () => activeResult.setAttributesDirty({}))),
                ],
              });
              setGalleryDialogOpen(false);
            }}
            validateSelection={(file) => {
              if (file.isSnippet) return Result.Error([new Error(t("fields.attachments.validation.noSnippets"))]);
              if (file.isFolder) return Result.Error([new Error(t("fields.attachments.validation.noFolders"))]);
              if (!file.globalId)
                return Result.Error([
                  // some of the files will be from filestores
                  new Error(t("fields.attachments.validation.missingGlobalId", { name: file.name })),
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
    Fields extends {
      image: BlobUrl | null;
      newBase64Image: string | null;
    },
    FieldOwner extends HasEditableFields<Fields>,
  >({
    fieldOwner,
  }: {
    fieldOwner?: FieldOwner;
  }): ReactNode => {
    const { t } = useTranslation("inventory");
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
        <CardHeader
          sx={{ p: "4px 12px 2px 16px" }}
          slotProps={{
            action: { sx: { m: 0 } },
            subheader: {
              variant: "body2",
            },
          }}
          subheader={t("fields.attachments.summary")}
          action={
            <CustomTooltip
              title={match<void, string>([
                [() => attachments.length === 0, t("fields.attachments.toggle.none")],
                [() => open, t("fields.attachments.toggle.hide")],
                [() => true, t("fields.attachments.toggle.show")],
              ])()}
            >
              <IconButton onClick={() => setOpen(!open)} disabled={attachments.length === 0}>
                <Badge color="primary" badgeContent={attachments.length}>
                  <ExpandCollapseIcon open={open} />
                </Badge>
              </IconButton>
            </CustomTooltip>
          }
        />
        {editable && (
          <CardContent
            sx={{
              pt: 0.5,
            }}
          >
            <FileSelector activeResult={activeResult} setOpen={setOpen} editable={editable} />
          </CardContent>
        )}
        <Collapse in={open}>
          <CollapseContents attachments={attachments} fieldOwner={fieldOwner} editable={editable} />
        </Collapse>
      </Card>
    );
  },
);
function Attachments<
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({ fieldOwner }: { fieldOwner?: FieldOwner }): ReactNode {
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
export default observer(Attachments);
