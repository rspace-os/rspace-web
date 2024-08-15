// @flow
import AddIcon from "@mui/icons-material/Add";
import React, { type Node, useContext } from "react";
import SidebarSubMenu, {
  type SidebarSubMenuRecordTypes,
} from "./SidebarSubMenu";
import useStores from "../../stores/use-stores";
import { doNotAwait } from "../../util/Util";
import NavigateContext from "../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../util/error";

type CreateNewArgs = {|
  onCreate: () => void,
|};

export default function CreateNew({ onCreate }: CreateNewArgs): Node {
  const { searchStore, trackingStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const handleCreate = async (recordType: SidebarSubMenuRecordTypes) => {
    trackingStore.trackEvent("CreateInventoryRecordClicked", {
      type: recordType,
    });
    try {
      // $FlowExpectedError[incompatible-call] recordType is the uppercase version of what createNew takes
      const newRecord = await searchStore.createNew(recordType.toLowerCase());
      onCreate();
      const params = searchStore.fetcher.generateNewQuery(
        newRecord.showNewlyCreatedRecordSearchParams
      );
      navigate(`/inventory/search?${params.toString()}`, {
        modifyVisiblePanel: false,
      });
    } catch (e) {
      if (e instanceof UserCancelledAction) return;
      throw e;
    }
  };

  return (
    <SidebarSubMenu
      types={["CONTAINER", "SAMPLE", "TEMPLATE"]}
      onClick={doNotAwait(handleCreate)}
      buttonLabel="Create"
      buttonIcon={<AddIcon />}
    />
  );
}
