import React, { type ReactNode, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import PolygonCard from "./PolygonCard";
import { type GeoLocation } from "../../../../stores/definitions/GeoLocation";

type PolygonDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
  editable: boolean;
  geoLocation: GeoLocation;
  doUpdateIdentifiers: () => void;
};

function PolygonDialog({
  open,
  setOpen,
  editable,
  geoLocation,
  doUpdateIdentifiers,
}: PolygonDialogArgs): ReactNode {
  const handleClose = () => {
    setOpen(false);
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Geolocation Polygon</DialogTitle>
      <DialogContent>
        <FormControl component="fieldset" fullWidth>
          <PolygonCard
            editable={editable}
            geoLocation={geoLocation}
            doUpdateIdentifiers={doUpdateIdentifiers}
          />
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default observer(PolygonDialog) as ComponentType<PolygonDialogArgs>;
