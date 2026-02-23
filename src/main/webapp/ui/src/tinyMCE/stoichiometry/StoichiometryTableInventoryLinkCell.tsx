import React, { useEffect, useState, useCallback, useMemo } from "react";
import Stack from "@mui/material/Stack";
import Chip from "@mui/material/Chip";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import type { InventoryLink } from "@/modules/stoichiometry/schema";
import InventoryPicker from "@/Inventory/components/Picker/Picker";
import Search from "@/stores/models/Search";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import { observer } from "mobx-react";

const INVENTORY_PICKER_SEARCH_PARAMS = {
  query: "",
  pageNumber: 0,
  pageSize: 5,
  orderBy: "name",
  order: "asc" as const,
  resultType: "SUBSAMPLE" as const,
  deletedItems: "EXCLUDE" as const,
  parentGlobalId: null,
  ownedBy: null,
  permalink: null,
};

function createInventoryPickerSearch(): Search {
  return new Search({
    factory: new MemoisedFactory(),
    fetcherParams: {
      resultType: INVENTORY_PICKER_SEARCH_PARAMS.resultType,
      pageSize: INVENTORY_PICKER_SEARCH_PARAMS.pageSize,
      orderBy: INVENTORY_PICKER_SEARCH_PARAMS.orderBy,
      order: INVENTORY_PICKER_SEARCH_PARAMS.order,
    },
    uiConfig: {
      allowedTypeFilters: new Set(["SUBSAMPLE"]),
      selectionMode: "SINGLE",
    },
  });
}

type StoichiometryTableInventoryLinkCellProps = {
  inventoryLink: InventoryLink | null | undefined;
  moleculeName: string | null;
  editable?: boolean;
  linkedInventoryItemGlobalIds?: ReadonlyArray<string>;
  onPickInventoryItem?: (inventoryItemGlobalId: string) => void;
  onRemoveInventoryLink?: () => void;
};

const StoichiometryTableInventoryLinkCell = ({
  inventoryLink,
  moleculeName,
  editable = true,
  linkedInventoryItemGlobalIds = [],
  onPickInventoryItem,
  onRemoveInventoryLink,
}: StoichiometryTableInventoryLinkCellProps) => {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch] = useState(createInventoryPickerSearch);
  const linkedInventoryItemGlobalIdSet = useMemo(
    () => new Set(linkedInventoryItemGlobalIds),
    [linkedInventoryItemGlobalIds],
  );

  const moleculeLabel = moleculeName ?? "molecule";
  const pickerTitle = `Pick inventory item for ${moleculeLabel}`;

  const openPicker = () => {
    setPickerOpen(true);
  };

  const closePicker = () => {
    setPickerOpen(false);
  };

  useEffect(() => {
    pickerSearch.alwaysFilterOut = (result) =>
      typeof result.globalId === "string" &&
      linkedInventoryItemGlobalIdSet.has(result.globalId);
  }, [linkedInventoryItemGlobalIdSet, pickerSearch]);

  useEffect(() => {
    if (!pickerOpen) return;
    pickerSearch.fetcher.resetFetcher();
    void pickerSearch.fetcher.performInitialSearch({
      resultType: INVENTORY_PICKER_SEARCH_PARAMS.resultType,
    });
  }, [pickerOpen, pickerSearch]);

  const handlePickerAddition = useCallback(
    (records: Array<InventoryRecord>) => {
      const [record] = records;
      if (!record) {
        return;
      }
      const inventoryItemGlobalId = record.globalId;
      if (typeof inventoryItemGlobalId !== "string") {
        return;
      }
      if (linkedInventoryItemGlobalIdSet.has(inventoryItemGlobalId)) {
        return;
      }
      onPickInventoryItem?.(inventoryItemGlobalId);
      closePicker();
    },
    [linkedInventoryItemGlobalIdSet, onPickInventoryItem],
  );

  if (inventoryLink) {
    return (
      <Stack direction="row" alignItems="center" spacing={0.5}>
        <Chip
          label={inventoryLink.inventoryItemGlobalId}
          size="small"
          sx={{
            color: "#1566b7",
            backgroundColor: "#f5fbfe",
            border: "1px solid #1566b7",
            fontWeight: 500,
          }}
        />
        <Tooltip title="Remove inventory link">
          <span>
            <IconButton
              size="small"
              aria-label={`Remove inventory link for ${moleculeLabel}`}
              disabled={!editable}
              onClick={onRemoveInventoryLink}
            >
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
    );
  }

  return (
    <>
      <Tooltip title="Add inventory link">
        <span>
          <IconButton
            size="small"
            aria-label={`Add inventory link for ${moleculeLabel}`}
            disabled={!editable}
            onClick={() => void openPicker()}
          >
            <AddCircleOutlineIcon fontSize="small" />
          </IconButton>
        </span>
      </Tooltip>
      <Dialog open={pickerOpen} onClose={closePicker} fullWidth maxWidth="lg">
        <DialogTitle>{pickerTitle}</DialogTitle>
        <DialogContent sx={{ height: 540 }}>
          <InventoryPicker
            search={pickerSearch}
            onAddition={handlePickerAddition}
            paddingless
          />
        </DialogContent>
      </Dialog>
    </>
  );
}

export default observer(StoichiometryTableInventoryLinkCell);
