//@flow

import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import useStores from "../../../stores/use-stores";
import Stepper from "./Stepper";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import React, { type ComponentType } from "react";
import TopLevelButton from "./TopLevelButton";
import { type Panel } from "../../../util/types";

const MoveSubmitButton = observer(function MoveSubmitButton({
  handleSubmit,
  isInvalid,
}: {|
  handleSubmit: () => void,
  isInvalid: boolean,
|}) {
  const { moveStore } = useStores();
  return (
    <SubmitSpinner
      onClick={handleSubmit}
      disabled={isInvalid || moveStore.submitting === "TO-OTHER"}
      loading={moveStore.submitting === "TO-OTHER"}
      label="Move"
    />
  );
});

const CancelButton = observer(function CancelButton({
  onClick,
}: {|
  onClick: () => void,
|}) {
  const { moveStore } = useStores();
  return (
    <Button onClick={onClick} disabled={moveStore.submitting !== "NO"}>
      Cancel
    </Button>
  );
});
type ActionsArgs = {|
  handleClose: () => void,
  handleMove: () => void,
  handleBack: () => void,
  handleNext: () => void,
  activeStep: Panel,
|};

function Actions({
  handleClose,
  handleMove,
  handleBack,
  handleNext,
  activeStep,
}: ActionsArgs) {
  const { uiStore, moveStore } = useStores();
  const isSelectionValid = () => {
    const ar = moveStore.activeResult;
    if (!ar) return false;
    if (!ar.selectedLocations)
      throw new Error("Locations of container must be known.");
    const selectedLocations = ar.selectedLocations;
    const infiniteSpace = ar.cType === "LIST" || ar.cType === "WORKBENCH";
    const allLocsSelected =
      ar && moveStore.selectedResults.length === selectedLocations.length;

    return (
      !moveStore.loading &&
      Boolean(ar) &&
      ar.canStoreRecords &&
      ar.canEdit &&
      (infiniteSpace || allLocsSelected)
    );
  };

  return !uiStore.isSingleColumnLayout ? (
    <>
      <TopLevelButton onClose={handleClose} />
      <div style={{ flexGrow: 1 }}></div>
      <CancelButton onClick={handleClose} />
      <MoveSubmitButton
        handleSubmit={handleMove}
        isInvalid={!isSelectionValid()}
      />
    </>
  ) : (
    <Grid container direction="column" spacing={1}>
      {activeStep === "left" && (
        <Grid item>
          <TopLevelButton onClose={handleClose} />
        </Grid>
      )}
      <Grid>
        <Stepper
          handleBack={handleBack}
          handleNext={handleNext}
          handleCancel={handleClose}
          activeStep={activeStep === "left" ? 0 : 1}
          onMove={handleMove}
          disabled={!isSelectionValid()}
          stepsCount={2}
          loading={moveStore.loading}
        />
      </Grid>
    </Grid>
  );
}
export default (observer(Actions): ComponentType<ActionsArgs>);
