// @flow

import Button from "@mui/material/Button";
import ContentImage from "../Content/ImageView/PlaceMarkers/ContentImage";
import ImageField, {
  type ImageData,
} from "../../../components/Inputs/ImageField";
import LocationOnIcon from "@mui/icons-material/LocationOn";
import LocationsImageMarkersDialog from "./LocationsImageMarkersDialog";
import React, { type Node, type ComponentType } from "react";
import useStores from "../../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import ContainerModel from "../../../stores/models/ContainerModel";
import { doNotAwait } from "../../../util/Util";
import docLinks from "../../../assets/DocLinks";
import { mkAlert, type Alert } from "../../../stores/contexts/Alert";
import FormField from "../../../components/Inputs/FormField";
import Grid from "@mui/material/Grid";

const CANVAS_ID = "locationsCanvas";

const useStyles = makeStyles()(() => ({
  editBtn: {
    flexGrow: 1,
  },
  divider: {
    width: "calc(100% - 20px)",
  },
}));

function LocationsImageField(): Node {
  const { searchStore, trackingStore, uiStore } = useStores();
  const activeResult = searchStore.activeResult;
  if (!activeResult || !(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const [editMarkers, setEditMarkers] = React.useState(false);
  const [toast, setToast] = React.useState<?Alert>(null);
  const { classes } = useStyles();

  const storeImage = (newImageData: ImageData) => {
    activeResult.setImage("locationsImage", CANVAS_ID)(newImageData);
    if (activeResult.image) return;

    if (toast) {
      uiStore.removeAlert(toast);
      activeResult.removeScopedToast(toast);
    }
    const newToast = mkAlert({
      message: "Set preview image too?",
      variant: "notice",
      isInfinite: true,
      actionLabel: "yes",
      onActionClick: doNotAwait(() =>
        activeResult.setImage("image", CANVAS_ID)(newImageData)
      ),
    });
    setToast(newToast);
    activeResult.addScopedToast(newToast);
    uiStore.addAlert(newToast);
  };

  return (
    <>
      <ContentImage />
      <FormField
        label="Locations Image"
        value={activeResult.locationsImage}
        explanation={
          <>
            See the documentation for information on{" "}
            <a
              href={docLinks.editLocationsInVisualContainers}
              target="_blank"
              rel="noreferrer"
            >
              choosing an image and marking locations
            </a>
            .
          </>
        }
        renderInput={({ id, value: locationsImage }) => (
          <>
            <ImageField
              storeImage={storeImage}
              imageAsObjectURL={locationsImage}
              id={id}
              endAdornment={
                <Grid item flexGrow={1} xs={12}>
                  <Button
                    fullWidth
                    size="large"
                    color="primary"
                    variant="outlined"
                    className={classes.editBtn}
                    startIcon={<LocationOnIcon />}
                    disabled={!locationsImage}
                    onClick={() => {
                      setEditMarkers(true);
                    }}
                  >
                    Edit Locations
                  </Button>
                </Grid>
              }
              showPreview={false}
              warningAlert={
                activeResult.loading
                  ? ""
                  : !locationsImage
                  ? "Visual containers require an image to add locations to. Click on 'Add Image' (above) to provide one."
                  : !activeResult.locationsCount
                  ? "Click on ‘Edit Locations’ to add locations and start using the visual container."
                  : ""
              }
              alt={"The marked locations of ${activeResult.name}"}
            />
            <canvas id={CANVAS_ID} style={{ display: "none" }}></canvas>
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

export default (observer(LocationsImageField): ComponentType<{||}>);
