import Layout2x1Dialog from "../Layout/Layout2x1Dialog";
import useStores from "../../../stores/use-stores";
import Actions from "./Actions";
import LeftPanel from "./LeftPanel";
import RightPanel from "./RightPanel";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import SimpleRecordsTable from "../SimpleRecordsTable";
import { type Record } from "../../../stores/definitions/Record";
import NameWithBadge from "../NameWithBadge";
import { doNotAwait } from "../../../util/Util";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";

type DialogTitleArgs<RecordLike extends Record> = {
  beingMoved: Array<RecordLike>;
};

const DialogTitle = <RecordLike extends Record>({
  beingMoved,
}: DialogTitleArgs<RecordLike>): React.ReactNode => {
  const [open, setOpen] = useState(false);

  const label = () => {
    if (beingMoved.length === 0)
      // i.e. the dialog is closed
      return "";
    if (beingMoved.length > 1) return `Moving ${beingMoved.length} items`;
    return (
      <>
        Moving <NameWithBadge record={beingMoved[0]} />
      </>
    );
  };

  return (
    <>
      {label()}
      <IconButtonWithTooltip
        onClick={() => setOpen(!open)}
        icon={<ExpandCollapseIcon open={open} />}
        title={`${open ? "Hide" : "Show"} items being moved`}
      />
      <SimpleRecordsTable records={beingMoved} open={open} />
    </>
  );
};

function MoveDialog(): React.ReactNode {
  const { moveStore, uiStore } = useStores();

  const handleBack = () => {
    moveStore.setActivePane("left");
  };

  const handleNext = () => {
    moveStore.setActivePane("right");
  };

  const handleClose = () => {
    void moveStore.setIsMoving(false);
  };

  const handleMove = async () => {
    await moveStore.moveSelected();
    handleClose();
  };

  return (
    <AlwaysNewWindowNavigationContext>
      <Layout2x1Dialog
        open={moveStore.isMoving}
        onClose={handleClose}
        actions={
          <Actions
            handleMove={doNotAwait(handleMove)}
            handleClose={handleClose}
            handleBack={handleBack}
            handleNext={handleNext}
            activeStep={uiStore.dialogVisiblePanel}
          />
        }
        dialogTitle={<DialogTitle beingMoved={moveStore.selectedResults} />}
        colLeft={<LeftPanel />}
        colRight={<RightPanel />}
      />
    </AlwaysNewWindowNavigationContext>
  );
}

export default observer(MoveDialog);
