// @flow

import React, { useContext, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import RecordDetails from "../RecordDetails";
import { match } from "../../../util/Util";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Typography from "@mui/material/Typography";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import InventoryBaseRecord from "../../../stores/models/InventoryBaseRecord";
import SearchContext from "../../../stores/contexts/Search";
import Stack from "@mui/material/Stack";
import ContainerModel from "../../../stores/models/ContainerModel";

function MoveInstructions(): Node {
  const { scopedResult } = useContext(SearchContext);
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  const { moveStore } = useStores();
  const [expand, setExpand] = React.useState(false);

  const instruction = () => {
    const numOfSelectedResults = moveStore.selectedResults.length;
    const numOfSelectedLocations = moveStore.targetLocations?.length ?? 0;
    const infiniteContainer =
      container.cType === "LIST" || container.cType === "WORKBENCH";
    const moreToSelect = numOfSelectedResults - numOfSelectedLocations;
    const canStoreLabel = (
      container.isWorkbench ? ["samples", "containers"] : container.canStore
    ).join(" and ");
    const plural = (l: number) => `location${l === 1 ? "" : "s"}`;
    const placedLabel = `(${numOfSelectedLocations}/${numOfSelectedResults} placed)`;
    const {
      message,
      severity,
      action = false,
    } = match<
      void,
      {|
        message: string,
        severity: "success" | "info" | "warning" | "error",
        action?: boolean,
      |}
    >([
      [
        () => moveStore.loading || container.loading,
        { message: "Loading...", severity: "info" },
      ],
      [
        () => container.movingIntoItself,
        {
          message: "A container can't be moved into itself or a subcontainer.",
          severity: "error",
        },
      ],
      [
        () => container.deleted,
        {
          message: "Can't move items into deleted containers.",
          severity: "error",
        },
      ],
      [
        () => !container.canEdit,
        {
          message:
            "You do not have permission to place items in this container.",
          severity: "error",
        },
      ],
      [
        () => container.cType === "IMAGE" && !container.locationsImage,
        {
          message:
            "This visual container doesn't yet have a locations image onto which locations can be marked. Please edit first.",
          severity: "warning",
        },
      ],
      [
        () =>
          container.cType === "IMAGE" &&
          Boolean(container.locationsImage) &&
          container.locationsCount === 0,
        {
          message:
            "This visual container doesn't yet have any marked locations into which items can be placed. Please edit first.",
          severity: "warning",
        },
      ],
      [
        () => !container.hasEnoughSpace,
        {
          message: "This container does not have enough space.",
          severity: "warning",
        },
      ],
      [
        () => !container.canStoreRecords,
        {
          message: `This container can store ${canStoreLabel} only.`,
          severity: "warning",
        },
      ],
      [
        () => infiniteContainer,
        {
          message: `Destination  selected (${
            container.cType === "WORKBENCH" ? "Bench" : "Container"
          }).`,
          severity: "success",
        },
      ],
      [
        () => numOfSelectedLocations === 0,
        {
          message: `Select ${numOfSelectedResults} ${plural(
            numOfSelectedResults
          )}. ${placedLabel}`,
          severity: "info",
          action: true,
        },
      ],
      [
        () => moreToSelect !== 0,
        {
          message: `Select ${moreToSelect} more ${plural(
            moreToSelect
          )}. ${placedLabel}`,
          severity: "info",
          action: true,
        },
      ],
      [() => true, { message: "Selection complete.", severity: "success" }],
    ])();

    return { message, severity, action };
  };

  const instructionAlert = () => {
    const { message, severity, action } = instruction();
    return (
      <Alert
        severity={severity}
        action={
          action && (
            <IconButtonWithTooltip
              title={expand ? "Collapse" : "Expand"}
              icon={<ExpandCollapseIcon open={expand} />}
              onClick={() => setExpand(!expand)}
              size="small"
            />
          )
        }
      >
        {message}
      </Alert>
    );
  };

  // close next item if not needed
  React.useEffect(() => {
    if (!["GRID", "IMAGE"].includes(container.cType)) {
      setExpand(false);
    }
  }, [container.id]);

  const nextSelection = (): ?InventoryBaseRecord => {
    if (!moveStore.activeResult?.selectedLocations)
      throw new Error("Destination container's locations must be known.");
    const destinationSelectedLocations =
      moveStore.activeResult.selectedLocations;

    const selectedGlobalIds = destinationSelectedLocations.map(
      (l) => l.content?.globalId
    );
    const firstNotPlaced = moveStore.selectedResults.find(
      (r) => !selectedGlobalIds.includes(r.globalId)
    );
    return firstNotPlaced;
  };

  const nextDetails = () =>
    nextSelection() ? (
      <Card variant="outlined">
        <CardContent style={{ paddingBottom: 0 }}>
          <Typography variant="h6" gutterBottom>
            Next item to be placed:
          </Typography>
          {/* $FlowExpectedError[incompatible-type] nextSelection() is a Result */}
          <RecordDetails record={nextSelection()} />
        </CardContent>
      </Card>
    ) : null;

  const { action } = instruction();

  const showDragAndDropTip =
    moveStore.sourceIsAlsoDestination && container.cType === "GRID";

  return (
    <Stack spacing={1}>
      {instructionAlert()}
      {action && expand && nextDetails()}
      {showDragAndDropTip && (
        <Alert
          severity="info"
          sx={{ fontSize: "0.8rem", letterSpacing: "0.015em" }}
        >
          <AlertTitle sx={{ fontSize: "0.85rem" }}>
            Tip: when rearranging the contents of grid containers you can simply
            drag-and-drop them into their new locations.
          </AlertTitle>
          Select one or more grid cells in the container&apos;s &quot;Locations
          and Content&quot; section and then tap and hold to enter drag-and-drop
          mode.
        </Alert>
      )}
    </Stack>
  );
}

export default (observer(MoveInstructions): ComponentType<{||}>);
