// @flow

import ExtendedContextMenu from "../../components/ContextMenu/ExtendedContextMenu";
import SelectAllIcon from "@mui/icons-material/SelectAll";
import React, { useContext, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import OpenInBrowserIcon from "@mui/icons-material/OpenInBrowser";
import useNavigateHelpers from "../../useNavigateHelpers";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import { match } from "../../../util/Util";
import ContainerModel from "../../../stores/models/ContainerModel";
import Badge from "@mui/material/Badge";

function ContentContextMenu(): Node {
  const { navigateToRecord } = useNavigateHelpers();
  const { search, scopedResult } = useContext(SearchContext);
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  if (!container.locations || !container.selectedLocations)
    throw new Error("Locations of container must be known.");
  const locations = container.locations;
  const selectedLocations = container.selectedLocations;

  const selectedResults = search.selectedResults;

  const selectedSubsampleLocations = selectedLocations.filter(
    (l) => l.content?.recordType === "subSample"
  );

  const onSelectOptions: Array<SplitButtonOption> = [
    {
      text: "All locations",
      selection: () => {
        locations
          .filter((l) => !l.content || !search.alwaysFilterOut(l.content))
          .map((l) => l.toggleSelected(true));
      },
    },
    {
      text: "Siblings of selected subsample",
      selection: () => {
        selectedSubsampleLocations.map((l) =>
          l.siblings.map((s) => s.toggleSelected(true))
        );
      },
    },
    {
      text: "None",
      selection: () => {
        selectedLocations.map((l) => l.toggleSelected(false));
      },
    },
    {
      text: "Invert",
      selection: () => {
        locations.map((l) => l.toggleSelected());
      },
    },
    {
      text: "Mine",
      selection: () => {
        locations.map(
          (l) => l.toggleSelected(l.content?.currentUserIsOwner === true) // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
    {
      text: "Not Mine",
      selection: () => {
        locations.map(
          (l) => l.toggleSelected(l.content?.currentUserIsOwner === false) // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
  ];

  const prefixActions = [
    {
      key: "open",
      onClick: (event: Event) => {
        event.stopPropagation();
        void navigateToRecord(selectedResults[0]);
      },
      icon: <OpenInBrowserIcon />,
      label: "Open",
      disabledHelp: match<void, string>([
        [() => selectedResults.length === 0, "Nothing selected."],
        [() => selectedResults.length > 1, "More than 1 item selected."],
        [() => true, ""],
      ])(),
      variant: "filled",
    },
    {
      key: "select",
      options: onSelectOptions,
      icon: (
        <Badge
          badgeContent={selectedLocations.length}
          color="primary"
          max={Infinity}
        >
          <SelectAllIcon />
        </Badge>
      ),
      disabledHelp: "",
    },
  ];

  return (
    <ExtendedContextMenu
      menuID="content"
      prefixActions={prefixActions}
      selectedResults={selectedResults}
      forceDisabled={
        search.processingContextActions ? "Action In Progress" : ""
      }
    />
  );
}

export default (observer(ContentContextMenu): ComponentType<{||}>);
