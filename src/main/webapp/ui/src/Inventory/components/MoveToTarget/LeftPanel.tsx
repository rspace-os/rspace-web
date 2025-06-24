import HelpLinkIcon from "../../../components/HelpLinkIcon";
import useStores from "../../../stores/use-stores";
import InventoryPicker from "../Picker/Picker";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import docLinks from "../../../assets/DocLinks";
import ContainerModel from "../../../stores/models/ContainerModel";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

type LeftPanelArgs = Record<string, never>;

function LeftPanel(_: LeftPanelArgs) {
  const { moveStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const selectionHelpText = () => {
    if (!(moveStore.search?.activeResult instanceof ContainerModel))
      return null;
    if (
      moveStore.search.activeResult.isWorkbench &&
      moveStore.search.fetcher.parentIsBench
    ) {
      if (isSingleColumnLayout) {
        return "Press 'Next', followed by 'Move', to move the selected items to this bench.";
      }
      return "Press 'Move' in the bottom-right to move the selected items to this bench.";
    }
    return null;
  };

  return (
    <>
      {moveStore.search ? (
        <InventoryPicker
          search={moveStore.search}
          onAddition={() => {
            /*
             * Do nothing; having passed moveStore.search, its activeResult
             * will automatically be the picked destination
             */
          }}
          header={
            <Typography variant="h6" component="h3">
              Pick Destination&nbsp;
              <HelpLinkIcon
                link={docLinks.moving}
                title="Info on moving items."
              />
            </Typography>
          }
          selectionHelpText={selectionHelpText()}
          testId="movePicker"
        />
      ) : (
        <Typography variant="h5">Loading</Typography>
      )}
    </>
  );
}

export default observer(LeftPanel);
