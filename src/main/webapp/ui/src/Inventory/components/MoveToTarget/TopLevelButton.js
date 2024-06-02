//@flow

import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import ContainerModel from "../../../stores/models/ContainerModel";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import React, { type ComponentType } from "react";

type TopLevelButtonArgs = {|
  onClose: () => void,
|};

function TopLevelButton({ onClose }: TopLevelButtonArgs) {
  const { moveStore, uiStore } = useStores();
  const allContainers = moveStore.selectedResults.every(
    (r) => r instanceof ContainerModel
  );
  const allTopLevel = moveStore.selectedResults.every(
    (r) => !r.hasParentContainers()
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
export default (observer(TopLevelButton): ComponentType<TopLevelButtonArgs>);
