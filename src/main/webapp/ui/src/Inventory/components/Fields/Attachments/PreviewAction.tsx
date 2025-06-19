import React, { type ComponentType, useState } from "react";
import { observer } from "mobx-react-lite";
import NoPreviewIcon from "@mui/icons-material/VisibilityOff";
import PreviewIcon from "@mui/icons-material/Visibility";
import ImagePreview, {
  type PreviewSize,
} from "../../../../components/ImagePreview";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
library.add(faSpinner);
import { type Attachment } from "../../../../stores/definitions/Attachment";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import useStores from "../../../../stores/use-stores";
import { mkAlert } from "../../../../stores/contexts/Alert";

type PreviewArgs = {
  attachment: Attachment;
};

function Preview({ attachment }: PreviewArgs) {
  const { uiStore } = useStores();

  const [showPreview, setShowPreview] = useState(false);
  const [size, setSize] = useState<PreviewSize | null>(null);

  const loadingImage = attachment.loadingImage;
  const imageLink = attachment.imageLink;
  const previewSupported = attachment.previewSupported;

  const openPreview = () => {
    attachment
      .setImageLink()
      .then(() => setShowPreview(true))
      .catch((e: Error) => {
        uiStore.addAlert(
          mkAlert({
            title: "Could not fetch image",
            message: e.message,
            variant: "error",
          })
        );
      });
  };

  const closePreview = () => {
    if (imageLink) attachment.revokeAuthenticatedLink(imageLink);
    setShowPreview(false);
  };

  return (
    <>
      <IconButtonWithTooltip
        title={
          imageLink
            ? ""
            : loadingImage
            ? "Loading image"
            : previewSupported
            ? "Preview file as image"
            : !attachment.id
            ? "Save first to enable image preview"
            : "Preview is not supported for this file type"
        }
        size="small"
        color="primary"
        onClick={openPreview}
        disabled={loadingImage || !previewSupported}
        icon={
          loadingImage ? (
            <FontAwesomeIcon icon="spinner" spin size="lg" />
          ) : previewSupported ? (
            <PreviewIcon />
          ) : (
            <NoPreviewIcon />
          )
        }
      />
      {showPreview && imageLink && (
        <ImagePreview
          closePreview={closePreview}
          link={imageLink}
          size={size}
          setSize={setSize}
        />
      )}
    </>
  );
}

export default observer(Preview);
