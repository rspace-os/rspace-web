// @flow

import PublishIcon from "@mui/icons-material/Publish";
import React, { type Node, useContext } from "react";
import SidebarSubMenu, {
  type SidebarSubMenuRecordTypes,
} from "./SidebarSubMenu";
import useStores from "../../stores/use-stores";
import {
  IMPORT_PATHNAME,
  type ImportRecordType,
} from "../../stores/stores/ImportStore";
import { doNotAwait } from "../../util/Util";
import NavigateContext, {
  type UseLocation,
} from "../../stores/contexts/Navigate";

type ImportMenuArgs = {|
  onClick: () => void,
|};

export default function ImportMenu({ onClick }: ImportMenuArgs): Node {
  const { useNavigate, useLocation } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { importStore, uiStore } = useStores();
  const isSelected = () =>
    (useLocation(): UseLocation).pathname === IMPORT_PATHNAME;

  function sidebarMenuTypeToRecordType(
    s: SidebarSubMenuRecordTypes
  ): ImportRecordType {
    if (s === "SAMPLE") return "SAMPLES";
    if (s === "CONTAINER") return "CONTAINERS";
    if (s === "SUBSAMPLE") return "SUBSAMPLES";
    throw new Error(`${s} is not supported by import`);
  }

  const handleOnClick = async (t: SidebarSubMenuRecordTypes) => {
    if (await uiStore.confirmDiscardAnyChanges()) {
      const recordType: ImportRecordType = sidebarMenuTypeToRecordType(t);
      await importStore.initializeNewImport(recordType);
      navigate(`/inventory/import?recordType=${recordType}`);
      onClick();
    }
  };

  return (
    <SidebarSubMenu
      buttonLabel="Import"
      buttonIcon={<PublishIcon />}
      types={["CONTAINER", "SAMPLE", "SUBSAMPLE"]}
      onClick={doNotAwait(handleOnClick)}
      selected={isSelected()}
      plural
    />
  );
}
