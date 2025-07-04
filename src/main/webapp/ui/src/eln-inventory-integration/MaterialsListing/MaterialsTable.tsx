// @flow

import GlobalId from "../../components/GlobalId";
import NameWithBadge from "../../Inventory/components/NameWithBadge";
import React, { useState, useEffect } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import clsx from "clsx";
import {
  Material,
  type ListOfMaterials,
} from "../../stores/models/MaterialsModel";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import ClearIcon from "@mui/icons-material/Clear";
import RecordLocation from "../../Inventory/components/RecordLocation";
import IconButtonWithTooltip from "../../components/IconButtonWithTooltip";
import UserDetails from "../../Inventory/components/UserDetails";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Checkbox from "@mui/material/Checkbox";
import UsedQuantityField from "./UsedQuantityField";
import SubSampleModel from "../../stores/models/SubSampleModel";
import { hasQuantity } from "../../stores/models/HasQuantity";

const NameCell = withStyles<
  { material: Material },
  { withBadge: string; withoutBadge: string }
>(() => ({
  withBadge: {
    "@media print": {
      display: "none",
    },
  },
  withoutBadge: {
    "@media print": {
      display: "block",
    },
    display: "none",
  },
}))(({ classes, material }) => (
  <>
    <div className={classes.withBadge}>
      <NameWithBadge record={material.invRec} />
    </div>
    <div className={classes.withoutBadge}>{material.invRec.name}</div>
  </>
));

const useStyles = makeStyles()((theme) => ({
  bottomBorder: { borderBottom: `1px dotted ${theme.palette.primary.main}` },
  columnWrapper: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
  },
  containerWrapper: { overflowX: "hidden", overflowY: "hidden" },
  fullWidth: { width: "100%" },
  relativeAnchor: { position: "relative" },
  textField: {
    marginLeft: theme.spacing(1),
    marginRight: theme.spacing(1),
    fontWeight: "normal",
    display: "flex",
  },
  primary: { color: theme.palette.primary.main },
  modifiedHighlight: { color: theme.palette.modifiedHighlight },
  warningRed: { color: theme.palette.warningRed },

  /* responsive styling */
  tableRowCell: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    borderBottomWidth: "0px",
    alignItems: "center",
    "@media print": {
      padding: theme.spacing(0.5),
    },
  },
  tableRowCellDesktop: {
    width: "47%",
  },
  tableRowCellMobile: {
    width: "94%",
  },
  tableRow: {
    width: "100%",
    display: "flex",
  },
  tableRowDesktop: {
    flexDirection: "row",
  },
  tableRowMobile: {
    flexDirection: "column",
  },
  spacedSubCell: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  centeredText: {
    textAlign: "center",
  },

  removeIcon: {
    color: theme.palette.warningRed,
    paddingLeft: theme.spacing(0.5),
    paddingRight: theme.spacing(0.75),
    "@media print": {
      display: "none",
    },
  },
}));

const colorCodedQuantity = (material: Material, list: ListOfMaterials) => {
  const { classes } = useStyles();
  return clsx(
    material.editing && (!material.enoughLeft || !list.validAdditionalAmount)
      ? classes.warningRed
      : material.editing && material.usedQuantityChanged
      ? classes.modifiedHighlight
      : material.editing
      ? classes.primary
      : ""
  );
};

type BodyRowArgs = {
  material: Material;
};

type TableArgs = {
  list: ListOfMaterials;
  isSingleColumn: boolean;
  onRemove?: (material: Material) => void;
  canEdit: boolean;
};

const TableSubCell = withStyles<
  {
    flex: number;
    className?: string;
    children: React.ReactNode;
    datatestid?: string;
  },
  {
    root: string;
  }
>(() => ({
  root: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
}))(({ flex, children, className, classes, datatestid }) => (
  <span
    className={clsx(className, classes.root)}
    style={{ flex }}
    data-test-id={datatestid}
  >
    {children}
  </span>
));

function MaterialsTable({
  list,
  isSingleColumn,
  onRemove,
  canEdit,
}: TableArgs): React.ReactNode {
  const { classes } = useStyles();
  const editingMode = list.editingMode;
  const materials = list.materials;
  const editableMaterials = materials.filter(
    (m) => m.invRec instanceof SubSampleModel
  );

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

  const ResponsiveMaterialsHeaderRow = observer(() => {
    const [selectOption, setSelectOption] = useState("None");
    return (
      <TableRow
        className={clsx(
          classes.tableRow,
          isSingleColumn ? classes.tableRowMobile : classes.tableRowDesktop
        )}
      >
        <TableCell
          className={clsx(
            classes.tableRowCell,
            isSingleColumn
              ? classes.tableRowCellMobile
              : classes.tableRowCellDesktop
          )}
        >
          <span style={{ flex: 5 }}>Name</span>
          <Tooltip title="For subsamples only" enterDelay={200}>
            <span style={{ flex: 2 }} className={classes.centeredText}>
              Consumed Quantity
            </span>
          </Tooltip>
          <span style={{ flex: 2 }} className={classes.centeredText}>
            Inventory Quantity
          </span>
        </TableCell>
        {editingMode ? (
          <TableCell
            className={clsx(
              classes.tableRowCell,
              isSingleColumn
                ? classes.tableRowCellMobile
                : classes.tableRowCellDesktop
            )}
          >
            <div style={{ flex: 3 }} className={classes.columnWrapper}>
              <span className={clsx(!isSingleColumn && classes.centeredText)}>
                Batch Edit
              </span>
              <Select
                sx={{ width: "75px" }}
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
            </div>
            <span style={{ flex: 4 }} className={classes.centeredText}>
              Additional Consumed Quantity
            </span>
            <span style={{ flex: 3 }} className={classes.centeredText}>
              Update Inventory Quantity
            </span>
          </TableCell>
        ) : (
          <TableCell
            className={clsx(
              classes.tableRowCell,
              isSingleColumn
                ? classes.tableRowCellMobile
                : classes.tableRowCellDesktop
            )}
          >
            <span
              style={{ flex: 5 }}
              className={clsx(!isSingleColumn && classes.centeredText)}
            >
              Location
            </span>
            <span style={{ flex: 2 }} className={classes.centeredText}>
              Global ID
            </span>
            <span style={{ flex: 2 }} className={classes.centeredText}>
              Owner
            </span>
          </TableCell>
        )}
      </TableRow>
    );
  });

  const CustomTableCell = observer(
    ({
      className,
      children,
    }: {
      className?: string;
      children: React.ReactNode;
    }) => {
      return (
        <TableCell
          className={clsx(
            className,
            classes.tableRowCell,
            isSingleColumn
              ? classes.tableRowCellMobile
              : classes.tableRowCellDesktop
          )}
        >
          {children}
        </TableCell>
      );
    }
  );

  const ResponsiveMaterialsRow = observer(({ material }: BodyRowArgs) => {
    const record = material.invRec;
    if (!record.globalId) throw new Error("Item Global ID must be known");
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
        data-test-id={`material-row-${globalId}`}
        className={clsx(
          classes.bottomBorder,
          classes.tableRow,
          isSingleColumn ? classes.tableRowMobile : classes.tableRowDesktop
        )}
      >
        <CustomTableCell>
          <span style={{ flex: 5 }} className={classes.spacedSubCell}>
            <NameCell material={material} />
            {onRemove && (
              <IconButtonWithTooltip
                title="Remove from list"
                icon={<ClearIcon />}
                size="small"
                className={classes.removeIcon}
                onClick={() => onRemove(material)}
                disabled={!canEdit}
              />
            )}
          </span>
          <TableSubCell
            flex={2}
            className={colorCodedQuantity(material, list)}
            datatestid={`material-used-quantity-${globalId}`}
          >
            {material.usedQuantity ? material.usedQuantityLabel : <>&mdash;</>}
          </TableSubCell>
          <TableSubCell
            datatestid={`material-inventory-quantity-${globalId}`}
            flex={2}
            className={colorCodedQuantity(material, list)}
          >
            {hasQuantity(record).isEmpty() || noQuantitySample ? (
              <>&mdash;</>
            ) : (
              material.inventoryQuantityLabel
            )}
          </TableSubCell>
        </CustomTableCell>
        {editingMode && material.canEditQuantity ? (
          <CustomTableCell className={classes.relativeAnchor}>
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
            <TableSubCell className={classes.primary} flex={3}>
              <Checkbox
                color="primary"
                onChange={() => {
                  material.toggleUpdateInventoryRecord();
                  material.updateQuantityEstimate();
                }}
                value={material.updateInventoryQuantity}
                checked={material.updateInventoryQuantity ?? undefined}
                disabled={!material.selected || list.mixedSelectedCategories}
                inputProps={{ "aria-label": "Linked quantities" }}
              />
            </TableSubCell>
          </CustomTableCell>
        ) : editingMode ? (
          <CustomTableCell className={classes.relativeAnchor}>
            <TableSubCell flex={3}>
              <Checkbox disabled={true} />
            </TableSubCell>
            <TableSubCell flex={4}>
              <>&mdash;</>
            </TableSubCell>
            <TableSubCell className={classes.primary} flex={3}>
              <>&mdash;</>
            </TableSubCell>
          </CustomTableCell>
        ) : (
          <CustomTableCell className={classes.relativeAnchor}>
            <TableSubCell flex={5}>
              <RecordLocation record={record} />
            </TableSubCell>
            <TableSubCell flex={2}>
              <GlobalId record={record} />
            </TableSubCell>
            <TableSubCell className={classes.primary} flex={2}>
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
          </CustomTableCell>
        )}
      </TableRow>
    );
  });

  return (
    <TableContainer className={classes.containerWrapper}>
      <Table size="small">
        <TableHead className={classes.fullWidth}>
          <ResponsiveMaterialsHeaderRow />
        </TableHead>
        <TableBody className={classes.fullWidth}>
          {materials.map((m) => (
            <ResponsiveMaterialsRow key={m.invRec.globalId} material={m} />
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default observer(MaterialsTable);
