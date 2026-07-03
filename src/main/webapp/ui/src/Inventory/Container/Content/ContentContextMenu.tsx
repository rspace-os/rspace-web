import OpenInBrowserIcon from "@mui/icons-material/OpenInBrowser";
import SelectAllIcon from "@mui/icons-material/SelectAll";
import Badge from "@mui/material/Badge";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import SearchContext from "../../../stores/contexts/Search";
import ContainerModel from "../../../stores/models/ContainerModel";
import { match } from "../../../util/Util";
import type { SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import ExtendedContextMenu from "../../components/ContextMenu/ExtendedContextMenu";
import useNavigateHelpers from "../../useNavigateHelpers";

function ContentContextMenu(): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { navigateToRecord } = useNavigateHelpers();
  const { search, scopedResult } = useContext(SearchContext);
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  if (!container.locations || !container.selectedLocations) throw new Error("Locations of container must be known.");
  const locations = container.locations;
  const selectedLocations = container.selectedLocations;

  const selectedResults = search.selectedResults;

  const selectedSubsampleLocations = selectedLocations.filter((l) => l.content?.recordType === "subSample");

  const onSelectOptions: Array<SplitButtonOption> = [
    {
      text: t("container.content.contextMenu.allLocations"),
      selection: () => {
        locations.filter((l) => !l.content || !search.alwaysFilterOut(l.content)).map((l) => l.toggleSelected(true));
      },
    },
    {
      text: t("container.content.contextMenu.siblingsOfSelectedSubsample"),
      selection: () => {
        selectedSubsampleLocations.map((l) => l.siblings.map((s) => s.toggleSelected(true)));
      },
    },
    {
      text: t("common:actions.none"),
      selection: () => {
        selectedLocations.map((l) => l.toggleSelected(false));
      },
    },
    {
      text: t("container.content.contextMenu.invert"),
      selection: () => {
        locations.map((l) => l.toggleSelected(null));
      },
    },
    {
      text: t("container.content.contextMenu.mine"),
      selection: () => {
        locations.map(
          (l) => l.toggleSelected(l.content?.currentUserIsOwner === true), // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
    {
      text: t("container.content.contextMenu.notMine"),
      selection: () => {
        locations.map(
          (l) => l.toggleSelected(l.content?.currentUserIsOwner === false), // if currentUserIsOwner cannot be determined then don't select
        );
      },
    },
  ];

  const prefixActions = [
    {
      key: "open",
      onClick: (event: React.MouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        void navigateToRecord(selectedResults[0]);
      },
      icon: <OpenInBrowserIcon />,
      label: t("common:actions.open"),
      disabledHelp: match<void, string>([
        [() => selectedResults.length === 0, t("container.content.contextMenu.nothingSelected")],
        [() => selectedResults.length > 1, t("container.content.contextMenu.tooManySelected")],
        [() => true, ""],
      ])(),
      variant: "filled" as const,
    },
    {
      key: "select",
      options: onSelectOptions,
      icon: (
        <Badge badgeContent={selectedLocations.length} color="primary" max={Infinity}>
          <SelectAllIcon fontSize="small" />
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
      forceDisabled={search.processingContextActions ? t("contextMenu.actionInProgress") : ""}
    />
  );
}

export default observer(ContentContextMenu);
