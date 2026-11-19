import Box from "@mui/material/Box";
import type React from "react";
import { useTranslation } from "react-i18next";
import RecordTypeIcon from "../../../components/RecordTypeIcon";

/**
 * The Inventory record types a chemical search hit can have, as named by the
 * backend's InventoryRecordType. Workbenches are never hits.
 */
export type InventoryHitType =
  | "SAMPLE"
  | "SAMPLE_TEMPLATE"
  | "SUBSAMPLE"
  | "CONTAINER"
  | "INSTRUMENT"
  | "INSTRUMENT_TEMPLATE";

function useIconData(inventoryType: InventoryHitType): { iconName: string; recordTypeLabel: string } {
  const { t } = useTranslation("inventory");
  switch (inventoryType) {
    case "SAMPLE":
      return { iconName: "sample", recordTypeLabel: t("recordTypes.sample.singular") };
    case "SAMPLE_TEMPLATE":
      return { iconName: "template", recordTypeLabel: t("recordTypes.sampleTemplate.singular") };
    case "SUBSAMPLE":
      return { iconName: "subsample", recordTypeLabel: t("recordTypes.subsample.singular") };
    case "CONTAINER":
      return { iconName: "container", recordTypeLabel: t("recordTypes.container.singular") };
    case "INSTRUMENT":
      return { iconName: "instrument", recordTypeLabel: t("recordTypes.instrument.singular") };
    case "INSTRUMENT_TEMPLATE":
      return { iconName: "instrumentTemplate", recordTypeLabel: t("recordTypes.instrumentTemplate.singular") };
  }
}

/** The preview for an Inventory hit in the Workspace chemical search results: its record type icon. */
export default function InventoryHitIcon({ inventoryType }: { inventoryType: InventoryHitType }): React.ReactNode {
  const record = useIconData(inventoryType);
  return (
    <Box
      role="img"
      aria-label={record.recordTypeLabel}
      sx={{ width: "150px", height: "150px", display: "flex", alignItems: "center", justifyContent: "center" }}
    >
      <RecordTypeIcon record={record} style={{ fontSize: "64px" }} />
    </Box>
  );
}
