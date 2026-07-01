import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import DownloadIcon from "@mui/icons-material/GetApp";
import InsertPhotoIcon from "@mui/icons-material/InsertPhoto";
import Backdrop from "@mui/material/Backdrop";
import CircularProgress from "@mui/material/CircularProgress";
import Grid from "@mui/material/Grid";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import React, { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import ChemistryIcon from "../../../../assets/graphics/ChemistryIcon";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { Attachment } from "../../../../stores/definitions/Attachment";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import useStores from "../../../../stores/use-stores";
import { capImageAt1MB } from "../../../../util/images";
import type { BlobUrl } from "../../../../util/types";
import DeleteButton from "../../DeleteButton";
import NameWithBadge from "../../NameWithBadge";
import Preview from "./PreviewAction";

const KetcherDialog = React.lazy(() => import("../../../../components/Ketcher/KetcherDialog"));

const ChemicalPreview = observer(({ attachment }: { attachment: Attachment }) => {
  const { t } = useTranslation("inventory");
  const { loadingString, chemicalString, isChemicalFile, chemistrySupported } = attachment;

  const [showPreview, setShowPreview] = useState(true);

  const handleClick = () => {
    attachment
      .createChemicalPreview()
      .then(() => {
        setShowPreview(true);
      })
      .catch(() => {});
  };

  return (
    <>
      <IconButtonWithTooltip
        title={
          chemicalString
            ? ""
            : loadingString
              ? t("fields.attachments.tooltips.loadingFile")
              : chemistrySupported
                ? t("fields.attachments.tooltips.preview3d")
                : isChemicalFile && !attachment.id
                  ? t("fields.attachments.tooltips.saveFirst3dPreview")
                  : t("fields.attachments.tooltips.preview3dUnsupported")
        }
        size="small"
        color="primary"
        onClick={handleClick}
        disabled={loadingString || !chemistrySupported}
        icon={loadingString ? <FontAwesomeIcon icon={faSpinner} spin size="lg" /> : <ChemistryIcon />}
      />
      {!loadingString && Boolean(chemicalString) && showPreview && (
        <React.Suspense
          fallback={
            <Backdrop
              open
              sx={{
                color: "#fff",
                zIndex: (theme) => theme.zIndex.drawer + 1,
              }}
            >
              <CircularProgress color="inherit" />
            </Backdrop>
          }
        >
          <KetcherDialog
            isOpen
            handleInsert={() => {}}
            title={t("fields.attachments.viewChemicalReadOnly")}
            existingChem={attachment.chemicalString}
            handleClose={() => {
              setShowPreview(false);
            }}
            readOnly={true}
          />
        </React.Suspense>
      )}
    </>
  );
});

const Download = ({ attachment }: { attachment: Attachment }) => {
  const { t } = useTranslation("common");
  return (
    <IconButtonWithTooltip
      title={t("actions.download")}
      size="small"
      color="primary"
      onClick={() => {
        void attachment.download();
      }}
      icon={<DownloadIcon />}
    />
  );
};

const SetAsPreviewImage = <
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  attachment,
  disabled,
  fieldOwner,
}: {
  attachment: Attachment;
  disabled: boolean;
  fieldOwner?: FieldOwner;
}): ReactNode => {
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();

  const storeImage = async (dataURL: string | null, file: Blob | null) => {
    if (!fieldOwner) throw new Error(t("fields.attachments.errors.previewImageUnavailable"));
    if (!dataURL || !file) throw new Error(t("fields.attachments.errors.unableToSetPreviewImage"));
    const scaledImage = await capImageAt1MB(file, dataURL);
    fieldOwner.setFieldsDirty({
      image: scaledImage,
      newBase64Image: scaledImage,
    });
    uiStore.addAlert(
      mkAlert({
        message: t("fields.attachments.alert.settingPreviewImage", { name: attachment.name }),
        variant: "notice",
        isInfinite: false,
      }),
    );
  };

  const setAsPreviewImage = async () => {
    try {
      await attachment.setImageLink();
      await storeImage(attachment.imageLink, await attachment.getFile());
    } catch (e) {
      uiStore.addAlert(
        mkAlert({
          title: t("fields.attachments.alert.couldNotFetchImage"),
          message: (e as Error).message,
          variant: "error",
        }),
      );
    }
  };
  return (
    <IconButtonWithTooltip
      title={
        disabled && !attachment.previewSupported
          ? t("fields.attachments.tooltips.saveFirstSetPreviewImage")
          : disabled
            ? t("fields.attachments.tooltips.editFirstSetPreviewImage")
            : t("fields.attachments.tooltips.setAsPreviewImage")
      }
      size="small"
      color="primary"
      onClick={() => void setAsPreviewImage()}
      icon={<InsertPhotoIcon />}
      disabled={disabled}
    />
  );
};

function AttachmentTableRow<
  Fields extends {
    image: BlobUrl | null;
    newBase64Image: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  attachment,
  fieldOwner,
  editable,
  chemistryProvider: _ = "",
}: {
  attachment: Attachment;
  fieldOwner?: FieldOwner;
  editable: boolean;
  chemistryProvider: string;
}): ReactNode {
  const { t } = useTranslation("inventory");
  const { t: tCommon } = useTranslation("common");
  return (
    <TableRow>
      <TableCell>
        <NameWithBadge record={attachment} />
      </TableCell>
      <TableCell width={1}>
        <Grid container direction="row" spacing={1} sx={{ flexWrap: "nowrap" }}>
          <Grid>
            <Preview attachment={attachment} />
          </Grid>
          <Grid>
            <ChemicalPreview attachment={attachment} />
          </Grid>
          <Grid>
            <SetAsPreviewImage
              attachment={attachment}
              disabled={!editable || !attachment.previewSupported}
              fieldOwner={fieldOwner}
            />
          </Grid>
          <Grid>
            <Download attachment={attachment} />
          </Grid>
          <Grid>
            <DeleteButton
              onClick={() => {
                attachment.remove();
              }}
              disabled={!editable}
              tooltipAfterClicked={t("fields.attachments.tooltips.deleteAfterClicked")}
              tooltipBeforeClicked={tCommon("actions.remove")}
              tooltipWhenDisabled={t("fields.attachments.tooltips.editFirstRemove")}
            />
          </Grid>
        </Grid>
      </TableCell>
    </TableRow>
  );
}

export default observer(AttachmentTableRow);
