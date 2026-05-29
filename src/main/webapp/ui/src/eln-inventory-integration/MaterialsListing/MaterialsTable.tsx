// @flow

import GlobalId from "../../components/GlobalId";
import NameWithBadge from "../../Inventory/components/NameWithBadge";
import React, { useState, useEffect } from "react";
import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import {
  Material,
  type ListOfMaterials,
} from "../../stores/models/MaterialsModel";
import { observer } from "mobx-react-lite";
import ClearIcon from "@mui/icons-material/Clear";
import RecordLocation from "../../Inventory/components/RecordLocation";
import IconButtonWithTooltip from "../../components/IconButtonWithTooltip";
import UserDetails from "../../components/UserDetails";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Checkbox from "@mui/material/Checkbox";
import UsedQuantityField from "./UsedQuantityField";
import SubSampleModel from "../../stores/models/SubSampleModel";
import { hasQuantity } from "../../stores/models/HasQuantity";
import { useTheme, type SxProps, type Theme } from "@mui/material/styles";
const colorCodedQuantity = (
  material: Material,
  list: ListOfMaterials,
  colors: { warningRed: string; modifiedHighlight: string; primary: string },
): string => {
  if (!material.editing) {
    return "";
  }
  if (!material.enoughLeft || !list.validAdditionalAmount) {
    return colors.warningRed;
  }
  if (material.usedQuantityChanged) {
    return colors.modifiedHighlight;
  }
  return colors.primary;
};
type TableArgs = {
  list: ListOfMaterials;
  isSingleColumn: boolean;
  onRemove?: (material: Material) => void;
  canEdit: boolean;
};
function TableSubCell({
  flex,
  children,
  sx,
  datatestid,
}: {
  flex: number;
  sx?: SxProps<Theme>;
  children: React.ReactNode;
  datatestid?: string;
}): React.ReactNode {
  return (
    <Box
      component="span"
      sx={[
        {
          flex,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
      data-test-id={datatestid}
    >
      {children}
    </Box>
  );
}
function MaterialsTable({
  list,
  isSingleColumn,
  onRemove,
  canEdit,
}: TableArgs): React.ReactNode {
  const theme = useTheme();
  const quantityColors = {
    warningRed: theme.palette.warningRed,
    modifiedHighlight: theme.palette.modifiedHighlight,
    primary: theme.palette.primary.main,
  };
  const editingMode = list.editingMode;
  const materials = list.materials;
  const editableMaterials = materials.filter(
    (m) => m.invRec instanceof SubSampleModel,
  );
  const [selectOption, setSelectOption] = useState("None");
  const afterToggleUpdates = (material: Material) => {
    if (material.selected) {
      material.setEditing(true, false);
      if (material.usedQuantity && list.additionalQuantity) {
        const unitId =
          list.additionalQuantity.unitId ?? material.usedQuantity.unitId;
        material.setUsedQuantity(list.additionalQuantity.numericValue, unitId);
        material.updateQuantityEstimate();
      }
    } else {
      material.setEditing(false, true); // cancelChange will reset value, unit, delta
      if (list.selectedMaterials.length < 1) {
        list.setAdditionalQuantity(null);
      }
    }
  };
  const handleSelect = (material: Material) => {
    material.toggleSelected();
    afterToggleUpdates(material);
  };
  useEffect(() => {
    /* on mixed selected categories: prevent values from being updated (and units disappearing) */
    if (list.mixedSelectedCategories) {
      list.setAdditionalQuantity(null);
      list.selectedMaterials.forEach((m) => {
        m.setEditing(false, true); // cancelChanges and updateQuantityEstimates
      });
    } else {
      list.selectedMaterials.forEach((m) => m.setEditing(true, false));
    }
  }, [list.mixedSelectedCategories]);
  const multipleSelectOptions = [
    {
      name: "None",
      handler: () =>
        editableMaterials
          .filter((m) => m.selected)
          .forEach((m) => handleSelect(m)),
    },
    {
      name: "All",
      handler: () =>
        editableMaterials
          .filter((m) => !m.selected)
          .forEach((m) => handleSelect(m)),
    },
    {
      name: "Invert",
      handler: () => editableMaterials.forEach((m) => handleSelect(m)),
    },
  ];
  const tableRowCellSx = {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    borderBottomWidth: "0px",
    alignItems: "center",
    "@media print": {
      padding: theme.spacing(0.5),
    },
    width: isSingleColumn ? "94%" : "47%",
  } as const;

  return (
    <TableContainer sx={{ overflowX: "hidden", overflowY: "hidden" }}>
      <Table size="small">
        <TableHead sx={{ width: "100%" }}>
          <TableRow
            sx={{
              width: "100%",
              display: "flex",
              flexDirection: isSingleColumn ? "column" : "row",
            }}
          >
            <TableCell sx={tableRowCellSx}>
              <Box component="span" sx={{ flex: 5 }}>
                Name
              </Box>
              <Tooltip title="For subsamples only" enterDelay={200}>
                <Box component="span" sx={{ flex: 2, textAlign: "center" }}>
                  Consumed Quantity
                </Box>
              </Tooltip>
              <Box component="span" sx={{ flex: 2, textAlign: "center" }}>
                Inventory Quantity
              </Box>
            </TableCell>
            {editingMode ? (
              <TableCell sx={tableRowCellSx}>
                <Box
                  sx={{
                    flex: 3,
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                  }}
                >
                  <Box
                    component="span"
                    sx={!isSingleColumn ? { textAlign: "center" } : undefined}
                  >
                    Batch Edit
                  </Box>
                  <Select
                    sx={{
                      width: "75px",
                    }}
                    id="quantity-editor-selector"
                    value={selectOption}
                    onChange={({ target: { value } }) => setSelectOption(value)}
                    variant="standard"
                  >
                    {multipleSelectOptions.map((option) => (
                      <MenuItem
                        key={option.name}
                        value={option.name}
                        onClick={option.handler}
                      >
                        {option.name}
                      </MenuItem>
                    ))}
                  </Select>
                </Box>
                <Box component="span" sx={{ flex: 4, textAlign: "center" }}>
                  Additional Consumed Quantity
                </Box>
                <Box component="span" sx={{ flex: 3, textAlign: "center" }}>
                  Update Inventory Quantity
                </Box>
              </TableCell>
            ) : (
              <TableCell sx={tableRowCellSx}>
                <Box
                  component="span"
                  sx={{
                    flex: 5,
                    ...(!isSingleColumn ? { textAlign: "center" } : {}),
                  }}
                >
                  Location
                </Box>
                <Box component="span" sx={{ flex: 2, textAlign: "center" }}>
                  Global ID
                </Box>
                <Box component="span" sx={{ flex: 2, textAlign: "center" }}>
                  Owner
                </Box>
              </TableCell>
            )}
          </TableRow>
        </TableHead>
        <TableBody sx={{ width: "100%" }}>
          {materials.map((material) => {
            const record = material.invRec;
            if (!record.globalId)
              throw new Error("Item Global ID must be known");
            const globalId = record.globalId;

            // Some samples don't have a quantity, so the UI must check for that
            const noQuantitySample = hasQuantity(record)
              .map((r) => {
                if (!r.quantity) return true;
                if (typeof r.quantity.numericValue !== "number") return true;
                if (!r.quantity.unitId) return true;
                return false;
              })
              .orElse(false);
            return (
              <TableRow
                key={globalId}
                data-test-id={`material-row-${globalId}`}
                sx={(theme) => ({
                  borderBottom: `1px dotted ${theme.palette.primary.main}`,
                  width: "100%",
                  display: "flex",
                  flexDirection: isSingleColumn ? "column" : "row",
                })}
              >
                <TableCell sx={tableRowCellSx}>
                  <Box
                    component="span"
                    sx={{
                      flex: 5,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                    }}
                  >
                    <Box sx={{ "@media print": { display: "none" } }}>
                      <NameWithBadge record={material.invRec} />
                    </Box>
                    <Box
                      sx={{
                        display: "none",
                        "@media print": { display: "block" },
                      }}
                    >
                      {material.invRec.name}
                    </Box>
                    {onRemove && (
                      <IconButtonWithTooltip
                        title="Remove from list"
                        icon={<ClearIcon />}
                        size="small"
                        sx={{
                          color: theme.palette.warningRed,
                          paddingLeft: theme.spacing(0.5),
                          paddingRight: theme.spacing(0.75),
                          "@media print": { display: "none" },
                        }}
                        onClick={() => onRemove(material)}
                        disabled={!canEdit}
                      />
                    )}
                  </Box>
                  <TableSubCell
                    flex={2}
                    datatestid={`material-used-quantity-${globalId}`}
                    sx={{
                      color: colorCodedQuantity(material, list, quantityColors),
                    }}
                  >
                    {material.usedQuantity ? (
                      material.usedQuantityLabel
                    ) : (
                      <>&mdash;</>
                    )}
                  </TableSubCell>
                  <TableSubCell
                    datatestid={`material-inventory-quantity-${globalId}`}
                    flex={2}
                    sx={{
                      color: colorCodedQuantity(material, list, quantityColors),
                    }}
                  >
                    {hasQuantity(record).isEmpty() || noQuantitySample ? (
                      <>&mdash;</>
                    ) : (
                      material.inventoryQuantityLabel
                    )}
                  </TableSubCell>
                </TableCell>
                {editingMode && material.canEditQuantity ? (
                  <TableCell sx={{ ...tableRowCellSx, position: "relative" }}>
                    <TableSubCell flex={3}>
                      <Checkbox
                        color="primary"
                        onClick={() => handleSelect(material)}
                        checked={material.selected}
                        value={material.selected}
                        disabled={!material.canEditQuantity}
                      />
                    </TableSubCell>
                    <TableSubCell flex={4}>
                      <UsedQuantityField material={material} list={list} />
                    </TableSubCell>
                    <TableSubCell
                      sx={{ color: theme.palette.primary.main }}
                      flex={3}
                    >
                      <Checkbox
                        color="primary"
                        onChange={() => {
                          material.toggleUpdateInventoryRecord();
                          material.updateQuantityEstimate();
                        }}
                        value={material.updateInventoryQuantity}
                        checked={material.updateInventoryQuantity ?? undefined}
                        disabled={
                          !material.selected || list.mixedSelectedCategories
                        }
                        slotProps={{
                          input: {
                            "aria-label": "Linked quantities",
                          },
                        }}
                      />
                    </TableSubCell>
                  </TableCell>
                ) : editingMode ? (
                  <TableCell sx={{ ...tableRowCellSx, position: "relative" }}>
                    <TableSubCell flex={3}>
                      <Checkbox disabled={true} />
                    </TableSubCell>
                    <TableSubCell flex={4}>
                      <>&mdash;</>
                    </TableSubCell>
                    <TableSubCell
                      sx={{ color: theme.palette.primary.main }}
                      flex={3}
                    >
                      <>&mdash;</>
                    </TableSubCell>
                  </TableCell>
                ) : (
                  <TableCell sx={{ ...tableRowCellSx, position: "relative" }}>
                    <TableSubCell flex={5}>
                      <RecordLocation record={record} />
                    </TableSubCell>
                    <TableSubCell flex={2}>
                      <GlobalId record={record} />
                    </TableSubCell>
                    <TableSubCell
                      sx={{ color: theme.palette.primary.main }}
                      flex={2}
                    >
                      {record.owner ? (
                        <UserDetails
                          userId={record.owner.id}
                          fullName={record.owner.fullName}
                          position={["bottom", "right"]}
                        />
                      ) : (
                        ""
                      )}
                    </TableSubCell>
                  </TableCell>
                )}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
export default observer(MaterialsTable);
