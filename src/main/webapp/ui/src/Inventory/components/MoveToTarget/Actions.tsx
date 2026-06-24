import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import useStores from "../../../stores/use-stores";
import type { Panel } from "../../../util/types";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import Stepper from "./Stepper";
import TopLevelButton from "./TopLevelButton";

const MoveSubmitButton = observer(({ handleSubmit, isInvalid }: { handleSubmit: () => void; isInvalid: boolean }) => {
  const { t } = useTranslation("inventory");
  const { moveStore } = useStores();
  return (
    <SubmitSpinner
      onClick={handleSubmit}
      disabled={isInvalid || moveStore.submitting === "TO-OTHER"}
      loading={moveStore.submitting === "TO-OTHER"}
      label={t("contextMenu.actions.move")}
    />
  );
});

const CancelButton = observer(({ onClick }: { onClick: () => void }) => {
  const { t } = useTranslation("common");
  const { moveStore } = useStores();
  return (
    <Button onClick={onClick} disabled={moveStore.submitting !== "NO"}>
      {t("actions.cancel")}
    </Button>
  );
});
type ActionsArgs = {
  handleClose: () => void;
  handleMove: () => void;
  handleBack: () => void;
  handleNext: () => void;
  activeStep: Panel;
};

function Actions({ handleClose, handleMove, handleBack, handleNext, activeStep }: ActionsArgs): React.ReactNode {
  const { moveStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const isSelectionValid = () => {
    const ar = moveStore.activeResult;
    if (!ar) return false;
    if (!ar.selectedLocations) throw new Error("Locations of container must be known.");
    const selectedLocations = ar.selectedLocations;
    const infiniteSpace = ar.cType === "LIST" || ar.cType === "WORKBENCH";
    const allLocsSelected = ar && moveStore.selectedResults.length === selectedLocations.length;

    return !moveStore.loading && Boolean(ar) && ar.canStoreRecords && ar.canEdit && (infiniteSpace || allLocsSelected);
  };

  return !isSingleColumnLayout ? (
    <>
      <TopLevelButton onClose={handleClose} />
      <Box sx={{ flexGrow: 1 }} />
      <CancelButton onClick={handleClose} />
      <MoveSubmitButton handleSubmit={handleMove} isInvalid={!isSelectionValid()} />
    </>
  ) : (
    <Stack spacing={1}>
      {activeStep === "left" && <TopLevelButton onClose={handleClose} />}
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
    </Stack>
  );
}
export default observer(Actions);
