import LocationOnIcon from "@mui/icons-material/LocationOn";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText, { richTextLink } from "@/modules/common/i18n/TransRichText";
import { type Alert, mkAlert } from "@/stores/contexts/Alert";
import docLinks from "../../../assets/DocLinks";
import ImageField, { type ImageData } from "../../../components/Inputs/ImageField";
import ContainerModel from "../../../stores/models/ContainerModel";
import useStores from "../../../stores/use-stores";
import FormField from "../../components/Inputs/FormField";
import ContentImage from "../Content/ImageView/PlaceMarkers/ContentImage";
import LocationsImageMarkersDialog from "./LocationsImageMarkersDialog";

function LocationsImageField(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore, trackingStore, uiStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!activeResult || !(activeResult instanceof ContainerModel)) throw new Error("ActiveResult must be a Container");
  const [editMarkers, setEditMarkers] = React.useState(false);
  const [toast, setToast] = React.useState<Alert | null>(null);

  const storeImage = (newImageData: ImageData) => {
    void activeResult.setImage("locationsImage")(newImageData);
    if (activeResult.image) return;

    if (toast) {
      uiStore.removeAlert(toast);
    }
    const newToast = mkAlert({
      message: t("container.fields.locationsImage.setPreviewImage"),
      variant: "notice",
      isInfinite: true,
      actionLabel: "yes",
      onActionClick: () => void activeResult.setImage("image")(newImageData),
    });
    setToast(newToast);
    activeResult.addScopedToast(newToast);
    uiStore.addAlert(newToast);
  };

  return (
    <>
      <ContentImage />
      <FormField
        label={t("container.fields.locationsImage.label")}
        value={activeResult.locationsImage}
        explanation={
          <TransRichText
            ns="inventory"
            i18nKey="container.fields.locationsImage.explanation"
            components={{
              a: richTextLink({
                href: docLinks.editLocationsInVisualContainers,
                target: "_blank",
                rel: "noreferrer",
              }),
            }}
          />
        }
        renderInput={({ id, value: locationsImage }) => (
          <>
            <ImageField
              storeImage={storeImage}
              imageAsObjectURL={locationsImage}
              id={id}
              endAdornment={
                <Grid sx={{ flexGrow: 1 }} size={12}>
                  <Button
                    fullWidth
                    size="large"
                    color="primary"
                    variant="outlined"
                    sx={{ flexGrow: 1 }}
                    startIcon={<LocationOnIcon />}
                    disabled={!locationsImage}
                    onClick={() => {
                      setEditMarkers(true);
                    }}
                  >
                    {t("container.fields.locationsImage.editLocations")}
                  </Button>
                </Grid>
              }
              showPreview={false}
              warningAlert={
                activeResult.loading
                  ? ""
                  : !locationsImage
                    ? t("container.fields.locationsImage.warningNoImage")
                    : !activeResult.locationsCount
                      ? t("container.fields.locationsImage.warningNoMarkers")
                      : ""
              }
              alt={t("container.fields.locationsImage.alt", { name: activeResult.name })}
            />
          </>
        )}
      />
      <LocationsImageMarkersDialog
        open={editMarkers}
        close={() => {
          setEditMarkers(false);
          trackingStore.trackEvent("ImageContainerLocationsEdited");
        }}
      />
    </>
  );
}

export default observer(LocationsImageField);
