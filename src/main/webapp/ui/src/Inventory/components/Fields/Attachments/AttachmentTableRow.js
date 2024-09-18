//@flow

import React, { type Node, useId } from "react";
import { observer } from "mobx-react-lite";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import DownloadIcon from "@mui/icons-material/GetApp";
import Grid from "@mui/material/Grid";
import NameWithBadge from "../../NameWithBadge";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner, faAtom } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import ChemicalViewerDialog from "../../../../eln-inventory-integration/ChemicalViewer/ChemicalViewerDialog";
import { type Attachment } from "../../../../stores/definitions/Attachment";
import DeleteButton from "../../DeleteButton";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import Preview from "./PreviewAction";
import { capImageAt1MB } from "../../../../util/images";
import InsertPhotoIcon from "@mui/icons-material/InsertPhoto";
import useStores from "../../../../stores/use-stores";
import { mkAlert } from "../../../../stores/contexts/Alert";
import { type HasEditableFields } from "../../../../stores/definitions/Editable";
import { type BlobUrl } from "../../../../stores/stores/ImageStore";
import { doNotAwait } from "../../../../util/Util";

const ChemicalPreview = observer(
  ({ attachment }: { attachment: Attachment }) => {
    const loadingString = attachment.loadingString;
    const chemicalString = attachment.chemicalString;
    const isChemicalFile = attachment.isChemicalFile;
    const chemistrySupported = attachment.chemistrySupported;

    return (
      <>
        <IconButtonWithTooltip
          title={
            chemicalString
              ? ""
              : loadingString
              ? "Loading file"
              : chemistrySupported
              ? "Preview file as 3D structure"
              : isChemicalFile && !attachment.id
              ? "Save first to enable 3D preview"
              : "3D Preview is not supported for this file type"
          }
          size="small"
          color="primary"
          onClick={doNotAwait(() => attachment.createChemicalPreview())}
          disabled={loadingString || !chemistrySupported}
          icon={
            loadingString ? (
              <FontAwesomeIcon icon="spinner" spin size="lg" />
            ) : (
              <FontAwesomeIcon icon={faAtom} size="lg" />
            )
          }
        />
        {!loadingString && Boolean(chemicalString) && (
          <ChemicalViewerDialog
            attachment={attachment}
            open={Boolean(chemicalString)}
          />
        )}
      </>
    );
  }
);

const Download = ({ attachment }: { attachment: Attachment }) => (
  <IconButtonWithTooltip
    title="Download"
    size="small"
    color="primary"
    onClick={() => {
      void attachment.download();
    }}
    icon={<DownloadIcon />}
  />
);

const SetAsPreviewImage = <
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  attachment,
  disabled,
  fieldOwner,
}: {
  attachment: Attachment,
  disabled: boolean,
  fieldOwner?: FieldOwner,
}): Node => {
  const { uiStore } = useStores();
  const canvasId = useId();

  const storeImage = async (dataURL: ?string, file: ?Blob) => {
    if (!fieldOwner)
      throw new Error(
        "The preview image cannot be set as the item is not available."
      );
    if (!dataURL || !file)
      throw new Error("Unable to set attachment as preview image.");
    const scaledImage = await capImageAt1MB(file, dataURL, canvasId);
    fieldOwner.setFieldsDirty({
      image: scaledImage,
      newBase64Image: scaledImage,
    });
    uiStore.addAlert(
      mkAlert({
        message: `Setting ${attachment.name} as preview image. Please Save the item to confirm.`,
        variant: "notice",
        isInfinite: false,
      })
    );
  };

  const setAsPreviewImage = async () => {
    try {
      await attachment.setImageLink();
      await storeImage(attachment.imageLink, await attachment.getFile());
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: "Could not fetch image",
          message: e.message,
          variant: "error",
        })
      );
    }
  };
  return (
    <>
      <IconButtonWithTooltip
        title={
          disabled && !attachment.previewSupported
            ? "Save first to enable setting this file as the item's Preview Image."
            : disabled
            ? "First press Edit to set as Preview Image."
            : "Set as Preview Image"
        }
        size="small"
        color="primary"
        onClick={doNotAwait(setAsPreviewImage)}
        icon={<InsertPhotoIcon />}
        disabled={disabled}
      />
      <canvas id={canvasId} style={{ display: "none" }}></canvas>
    </>
  );
};

function AttachmentTableRow<
  Fields: {
    image: ?BlobUrl,
    newBase64Image: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  attachment,
  fieldOwner,
  editable,
}: {|
  attachment: Attachment,
  fieldOwner?: FieldOwner,
  editable: boolean,
|}): Node {
  return (
    <TableRow>
      <TableCell>
        <NameWithBadge record={attachment} />
      </TableCell>
      <TableCell width={1}>
        <Grid container direction="row" spacing={1} wrap="nowrap">
          <Grid item>
            <Preview attachment={attachment} />
          </Grid>
          {/* Remove the chemical preview button while there is no open-source chemistry implementation*/}
          {/*<Grid item>*/}
          {/*  <ChemicalPreview attachment={attachment} />*/}
          {/*</Grid>*/}
          <Grid item>
            <SetAsPreviewImage
              attachment={attachment}
              disabled={!editable || !attachment.previewSupported}
              fieldOwner={fieldOwner}
            />
          </Grid>
          <Grid item>
            <Download attachment={attachment} />
          </Grid>
          <Grid item>
            <DeleteButton
              onClick={() => {
                attachment.remove();
              }}
              disabled={!editable}
              tooltipAfterClicked="Attachment will be deleted once this item is saved."
              tooltipBeforeClicked="Remove"
              tooltipWhenDisabled="First press Edit to remove this attachment."
            />
          </Grid>
        </Grid>
      </TableCell>
    </TableRow>
  );
}

export default (observer(AttachmentTableRow): typeof AttachmentTableRow);
