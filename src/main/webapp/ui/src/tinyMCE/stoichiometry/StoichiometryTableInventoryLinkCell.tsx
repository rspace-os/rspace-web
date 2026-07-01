import { faTrashAlt } from "@fortawesome/free-regular-svg-icons/faTrashAlt";
import { faClockRotateLeft } from "@fortawesome/free-solid-svg-icons/faClockRotateLeft";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutlined";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutlined";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import Box from "@mui/material/Box";
import Chip, { chipClasses } from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Tooltip from "@mui/material/Tooltip";
import { runInAction } from "mobx";
import { observer } from "mobx-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import InventoryPicker from "@/Inventory/components/Picker/Picker";
import { RecordLink } from "@/Inventory/components/RecordLink";
import type { InventoryLink } from "@/modules/stoichiometry/schema";
import type { InventoryRecord } from "@/stores/definitions/InventoryRecord";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import Search from "@/stores/models/Search";

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

function createInventoryPickerSearch(alwaysFilteredOutReason: string): Search {
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
      instantConfirm: false,
      requiredPermissions: ["UPDATE"],
      alwaysFilteredOutReason,
    },
  });
}

type StoichiometryTableInventoryLinkCellProps = {
  inventoryLink: InventoryLink | null | undefined;
  isDeleted?: boolean;
  moleculeName: string | null;
  editable?: boolean;
  showInsufficientStockWarning?: boolean;
  linkedInventoryItemGlobalIds?: string[];
  onPickInventoryItem?: (id: number, inventoryItemGlobalId: string) => void;
  onRemoveInventoryLink?: () => void;
  onUndoRemoveInventoryLink?: () => void;
};

function toLinkedSubsampleRecord(inventoryLink: InventoryLink, recordTypeLabel: string): InventoryRecord {
  const id = Number.parseInt(inventoryLink.inventoryItemGlobalId.slice(2), 10);

  return {
    id: Number.isNaN(id) ? inventoryLink.id : id,
    name: inventoryLink.inventoryItemGlobalId,
    globalId: inventoryLink.inventoryItemGlobalId,
    iconName: "subsample",
    recordTypeLabel,
    permalinkURL: Number.isNaN(id) ? null : `/inventory/subsample/${id}`,
    recordLinkLabel: inventoryLink.inventoryItemGlobalId,
    showRecordOnNavigate: true,
  } as InventoryRecord;
}

/*
 * NB: This component cannot be split into subcomponents right now because there
 * is a lot of circular dependencies coming from the InventoryPicker and Search
 * imports.
 */
const StoichiometryTableInventoryLinkCell = ({
  inventoryLink,
  isDeleted = false,
  moleculeName,
  editable = true,
  showInsufficientStockWarning = false,
  linkedInventoryItemGlobalIds = [],
  onPickInventoryItem,
  onRemoveInventoryLink,
  onUndoRemoveInventoryLink,
}: StoichiometryTableInventoryLinkCellProps) => {
  const { t } = useTranslation(["common", "inventory"]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerSearch] = useState(() => createInventoryPickerSearch(t("stoichiometry.inventoryLink.alreadyLinked")));
  const linkedInventoryItemGlobalIdSet = new Set(linkedInventoryItemGlobalIds);

  const moleculeLabel = moleculeName ?? t("stoichiometry.inventoryUpdate.unnamedMolecule");
  const pickerTitle = t("stoichiometry.inventoryLink.pickerTitle", { molecule: moleculeLabel });

  const openPicker = () => {
    setPickerOpen(true);
  };

  const closePicker = () => {
    setPickerOpen(false);
  };

  useEffect(() => {
    runInAction(() => {
      pickerSearch.alwaysFilterOut = (result) =>
        typeof result.globalId === "string" && linkedInventoryItemGlobalIdSet.has(result.globalId);
    });
  }, [linkedInventoryItemGlobalIdSet, pickerSearch]);

  useEffect(() => {
    if (!pickerOpen) return;
    pickerSearch.fetcher.resetFetcher();
    void pickerSearch.fetcher.performInitialSearch({
      resultType: INVENTORY_PICKER_SEARCH_PARAMS.resultType,
    });
  }, [pickerOpen, pickerSearch]);

  const handlePickerAddition = (records: Array<InventoryRecord>) => {
    const [record] = records;
    if (!record?.id) {
      return;
    }
    const inventoryItemGlobalId = record.globalId;
    if (typeof inventoryItemGlobalId !== "string") {
      return;
    }
    if (linkedInventoryItemGlobalIdSet.has(inventoryItemGlobalId)) {
      return;
    }
    onPickInventoryItem?.(record.id, inventoryItemGlobalId);
    closePicker();
  };

  if (isDeleted) {
    return (
      <Stack direction="row" spacing={0.5} sx={{ alignItems: "center", height: "100%" }}>
        {/* Font Size for the icon is necessary as MUI overrides FA font-size */}
        <Chip
          size="small"
          variant="outlined"
          label={t("stoichiometry.inventoryLink.deleted")}
          icon={<FontAwesomeIcon icon={faTrashAlt} size="2xs" />}
          sx={(theme) => ({
            fontSize: theme.typography.caption.fontSize,
            [`& .${chipClasses.label}`]: {
              fontSize: "inherit",
            },
            [`& .${chipClasses.icon}`]: {
              fontSize: "inherit",
            },
          })}
        />
        {editable && (
          <Tooltip title={t("stoichiometry.inventoryLink.undo")}>
            <span>
              <IconButton
                size="small"
                aria-label={t("stoichiometry.inventoryLink.undoForMolecule", { molecule: moleculeLabel })}
                onClick={onUndoRemoveInventoryLink}
              >
                <FontAwesomeIcon icon={faClockRotateLeft} size="sm" />
              </IconButton>
            </span>
          </Tooltip>
        )}
      </Stack>
    );
  }

  if (inventoryLink) {
    const record = toLinkedSubsampleRecord(inventoryLink, t("inventory:recordTypes.subsample.singular"));
    const showStockDeductedIndicator = inventoryLink.stockDeducted === true;
    const showInsufficientStockIndicator = showInsufficientStockWarning && !showStockDeductedIndicator;

    return (
      <Stack direction="row" spacing={0.5} sx={{ alignItems: "center", height: "100%" }}>
        <RecordLink record={record} disableNavigationContext={true} hideRecordTypeTooltip={true} newTab={true} />
        {showStockDeductedIndicator && (
          <Tooltip title={t("stoichiometry.inventoryLink.stockDeducted")}>
            <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.25 }}>
              <CheckCircleOutlineIcon
                fontSize="small"
                sx={{ color: "success.main" }}
                aria-label={t("stoichiometry.inventoryLink.stockDeducted")}
              />
            </Box>
          </Tooltip>
        )}
        {showInsufficientStockIndicator && (
          <Tooltip title={t("stoichiometry.inventoryLink.insufficientStock")}>
            <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.25 }}>
              <WarningAmberIcon
                titleAccess={t("stoichiometry.inventoryLink.insufficientStock")}
                fontSize="small"
                sx={{ color: "warning.main" }}
              />
            </Box>
          </Tooltip>
        )}
        {editable && (
          <Tooltip title={t("stoichiometry.inventoryLink.remove")}>
            <span>
              <IconButton
                size="small"
                aria-label={t("stoichiometry.inventoryLink.removeForMolecule", { molecule: moleculeLabel })}
                onClick={onRemoveInventoryLink}
              >
                <DeleteOutlineIcon fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        )}
      </Stack>
    );
  }

  return (
    <>
      <Tooltip title={t("stoichiometry.inventoryLink.add")}>
        <span>
          <IconButton
            size="small"
            aria-label={t("stoichiometry.inventoryLink.addForMolecule", { molecule: moleculeLabel })}
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
            onCancel={closePicker}
            showActions={true}
            resetActiveResultOnClose
            paddingless
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default observer(StoichiometryTableInventoryLinkCell);
