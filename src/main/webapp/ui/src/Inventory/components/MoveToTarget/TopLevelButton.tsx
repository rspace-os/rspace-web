import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import ContainerModel from "../../../stores/models/ContainerModel";
import { hasLocation } from "../../../stores/models/HasLocation";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import React from "react";

type TopLevelButtonArgs = {
  onClose: () => void;
};

function TopLevelButton({ onClose }: TopLevelButtonArgs): React.ReactNode {
  const { moveStore, uiStore } = useStores();
  const allContainers = moveStore.selectedResults.every(
    (r) => r instanceof ContainerModel
  );
  const allTopLevel = moveStore.selectedResults.every((r) =>
    hasLocation(r)
      .map((r) => r.immediateParentContainer === null)
      .orElse(false)
  );
  return (
    <SubmitSpinner
      onClick={() => {
        void moveStore.moveSelected(true).then(() => {
          onClose();
        });
      }}
      fullWidth={uiStore.isSmall}
      disabled={
        !allContainers || allTopLevel || moveStore.submitting === "MAKE-TOP"
      }
      loading={moveStore.submitting === "MAKE-TOP"}
      label="Make Top-level"
    />
  );
}

export default observer(TopLevelButton);
