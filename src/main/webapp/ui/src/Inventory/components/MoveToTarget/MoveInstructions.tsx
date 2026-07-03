import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchContext from "../../../stores/contexts/Search";
import type { Record } from "../../../stores/definitions/Record";
import ContainerModel from "../../../stores/models/ContainerModel";
import useStores from "../../../stores/use-stores";
import { match } from "../../../util/Util";
import RecordDetails from "../RecordDetails";

function MoveInstructions(): React.ReactNode {
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const { scopedResult } = useContext(SearchContext);
  if (!(scopedResult && scopedResult instanceof ContainerModel))
    throw new Error("Search context's scopedResult must be a ContainerModel");
  const container: ContainerModel = scopedResult;
  const { moveStore } = useStores();
  const [expand, setExpand] = useState(false);

  const instruction = () => {
    const numOfSelectedResults = moveStore.selectedResults.length;
    const numOfSelectedLocations = moveStore.targetLocations?.length ?? 0;
    const infiniteContainer = container.cType === "LIST" || container.cType === "WORKBENCH";
    const moreToSelect = numOfSelectedResults - numOfSelectedLocations;
    const canStoreLabel = formatList(
      container.isWorkbench ? ["samples", "containers"] : container.canStore,
      i18n.resolvedLanguage ?? i18n.language,
    );
    const {
      message,
      severity,
      action = false,
    } = match<
      void,
      {
        message: string;
        severity: "success" | "info" | "warning" | "error";
        action?: boolean;
      }
    >([
      [() => moveStore.loading || container.loading, { message: t("moveToTarget.loadingEllipsis"), severity: "info" }],
      [
        () => container.movingIntoItself,
        {
          message: t("moveToTarget.messages.movingIntoItself"),
          severity: "error",
        },
      ],
      [
        () => container.deleted,
        {
          message: t("moveToTarget.messages.deletedDestination"),
          severity: "error",
        },
      ],
      [
        () => !container.canEdit,
        {
          message: t("moveToTarget.messages.noPermission"),
          severity: "error",
        },
      ],
      [
        () => container.cType === "IMAGE" && !container.locationsImage,
        {
          message: t("moveToTarget.messages.visualContainerNoImage"),
          severity: "warning",
        },
      ],
      [
        () => container.cType === "IMAGE" && Boolean(container.locationsImage) && container.locationsCount === 0,
        {
          message: t("moveToTarget.messages.visualContainerNoLocations"),
          severity: "warning",
        },
      ],
      [
        () => !container.hasEnoughSpace,
        {
          message: t("moveToTarget.messages.notEnoughSpace"),
          severity: "warning",
        },
      ],
      [
        () => !container.canStoreRecords,
        {
          message: t("moveToTarget.messages.canStoreOnly", { canStoreLabel }),
          severity: "warning",
        },
      ],
      [
        () => infiniteContainer,
        {
          message:
            container.cType === "WORKBENCH"
              ? t("moveToTarget.messages.benchSelected")
              : t("moveToTarget.messages.containerSelected"),
          severity: "success",
        },
      ],
      [
        () => numOfSelectedLocations === 0,
        {
          message: t("moveToTarget.messages.selectLocations", {
            count: numOfSelectedResults,
            placed: numOfSelectedLocations,
            total: numOfSelectedResults,
          }),
          severity: "info",
          action: true,
        },
      ],
      [
        () => moreToSelect !== 0,
        {
          message: t("moveToTarget.messages.selectMoreLocations", {
            count: moreToSelect,
            placed: numOfSelectedLocations,
            total: numOfSelectedResults,
          }),
          severity: "info",
          action: true,
        },
      ],
      [() => true, { message: t("moveToTarget.messages.selectionComplete"), severity: "success" }],
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
              title={expand ? t("common:actions.collapse") : t("common:actions.expand")}
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
  useEffect(() => {
    if (!["GRID", "IMAGE"].includes(container.cType)) {
      setExpand(false);
    }
  }, [container.id, container.cType]);

  const nextSelection = (): Record | undefined => {
    if (!moveStore.activeResult?.selectedLocations) throw new Error("Destination container's locations must be known.");
    const destinationSelectedLocations = moveStore.activeResult.selectedLocations;

    const selectedGlobalIds = destinationSelectedLocations.map((l) => l.content?.globalId);
    const firstNotPlaced = moveStore.selectedResults.find((r) => !selectedGlobalIds.includes(r.globalId));
    return firstNotPlaced;
  };

  const nextDetails = () => {
    const nextRecord = nextSelection();
    return nextRecord ? (
      <Card variant="outlined">
        <CardContent sx={{ paddingBottom: 0 }}>
          <Typography variant="h6" gutterBottom>
            {t("moveToTarget.nextItem")}
          </Typography>
          <RecordDetails record={nextRecord} />
        </CardContent>
      </Card>
    ) : null;
  };

  const { action } = instruction();

  const showDragAndDropTip = moveStore.sourceIsAlsoDestination && container.cType === "GRID";

  return (
    <Stack spacing={1}>
      {instructionAlert()}
      {action && expand && nextDetails()}
      {showDragAndDropTip && (
        <Alert severity="info" sx={{ fontSize: "0.8rem", letterSpacing: "0.015em" }}>
          <AlertTitle sx={{ fontSize: "0.85rem" }}>{t("moveToTarget.dragDropTip")}</AlertTitle>
          {t("moveToTarget.dragDropInstructions")}
        </Alert>
      )}
    </Stack>
  );
}

export default observer(MoveInstructions);
