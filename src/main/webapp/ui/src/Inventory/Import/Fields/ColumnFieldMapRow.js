//@flow

import React, { useState, type ComponentType, type ElementProps } from "react";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Collapse from "@mui/material/Collapse";
import CustomTooltip from "../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import FieldMenuItem from "./FieldSelectMenuItem";
import FieldNameStringField from "./FieldNameStringField";
import FieldTypeMenu from "./FieldTypeMenu";
import UploadFormControl from "./FormControl";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Select from "@mui/material/Select";
import TableCell from "./TableCell";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import {
  Fields,
  getTypeOfField,
  ColumnFieldMap,
} from "../../../stores/models/ImportModel";
import { FIELD_DATA } from "../../../stores/models/FieldTypes";
import { toTitleCase, match } from "../../../util/Util";
import { observer } from "mobx-react-lite";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import Badge from "@mui/material/Badge";

const useStyles = makeStyles()(() => ({
  fieldIconButton: {
    padding: 0,
  },
  fieldSelect: {
    width: "100%",
  },
  extendedSection: {
    paddingTop: 0,
    paddingBottom: 0,
  },
  extendedSectionCell: {
    padding: 0,
  },
}));

const CustomTableRow = withStyles<
  ElementProps<typeof TableRow>,
  { root: string }
>((theme, { open }) => ({
  root: {
    "&:hover": {
      backgroundColor: open ? "initial" : theme.palette.hover.tableRow,
    },
  },
}))(TableRow);

type ColumnFieldMapRowProps = {
  columnFieldMap: ColumnFieldMap,
  existingTemplate: boolean,
};

function Row({ columnFieldMap, existingTemplate }: ColumnFieldMapRowProps) {
  const [open, setOpen] = useState(false);
  const { classes } = useStyles({ open });

  const renderClosedFieldSelectLabel = (fieldSymbolKey: string) =>
    match<string, string>([
      [
        (f) => f === "CUSTOM",
        `Custom Field (${FIELD_DATA[columnFieldMap.chosenFieldType].label})`,
      ],
      [() => true, toTitleCase(fieldSymbolKey)],
    ])(fieldSymbolKey);

  const FieldSymbols: Array<$Values<typeof Fields>> = ((Object.values(
    columnFieldMap.fieldsByRecordType
  ): any): Array<$Values<typeof Fields>>);

  const onTypeChange = ({
    target: { value },
  }: {
    target: { value: string, ... },
    ...
  }) => {
    const field = Symbol.for(value);
    columnFieldMap.updateField(field);
    columnFieldMap.setChosenFieldType(
      getTypeOfField(field) ?? columnFieldMap.fieldType
    );
    setOpen(field === Fields.custom);
    // handle auto-un/selection
    const selectionToggleNeeded =
      (value === "IGNORE" && columnFieldMap.selected) ||
      (value !== "IGNORE" && !columnFieldMap.selected);
    if (selectionToggleNeeded) columnFieldMap.toggleSelected();
  };

  return (
    <>
      <CustomTableRow className={classes.tableRow} open={open}>
        <TableCell padding="checkbox">
          <Checkbox
            checked={columnFieldMap.selected}
            onChange={() => columnFieldMap.toggleSelected()}
            color="default"
            name={`Select mapping for ${columnFieldMap.fieldName}`}
            data-test-id={columnFieldMap.fieldName}
          />
        </TableCell>
        <TableCell padding="none" align="left" width="70%">
          {columnFieldMap.fieldName}
          {columnFieldMap.columnName !== columnFieldMap.fieldName && (
            <Typography
              color="textSecondary"
              component="span"
              variant="caption"
            >
              {" "}
              ({columnFieldMap.columnName})
            </Typography>
          )}
        </TableCell>
        <TableCell width={1}>
          <IconButton disabled className={classes.fieldIconButton}>
            {FIELD_DATA[columnFieldMap.chosenFieldType].icon}
          </IconButton>
        </TableCell>
        <TableCell padding="none" align="left" width="30%">
          <Select
            variant="standard"
            className={classes.fieldSelect}
            name="field"
            value={Symbol.keyFor(columnFieldMap.field)}
            onChange={onTypeChange}
            renderValue={renderClosedFieldSelectLabel}
          >
            {FieldSymbols.map((f: $Values<typeof Fields>, i: number) => (
              <FieldMenuItem
                key={i}
                value={Symbol.keyFor(f)}
                field={f}
                currentField={columnFieldMap.field}
                typeIsCompatibleWithField={columnFieldMap.isCompatibleWithField(
                  f
                )}
              />
            ))}
          </Select>
        </TableCell>
        <TableCell padding="none" align="left" width={1}>
          <CustomTooltip title="Custom details">
            <IconButton
              onClick={() => setOpen(!open)}
              disabled={
                existingTemplate || columnFieldMap.field !== Fields.custom
              }
            >
              <Badge
                badgeContent={columnFieldMap.valid ? "" : "!"}
                color="error"
                invisible={columnFieldMap.valid}
              >
                <ExpandCollapseIcon open={open} />
              </Badge>
            </IconButton>
          </CustomTooltip>
        </TableCell>
      </CustomTableRow>
      {!existingTemplate && (
        <TableRow className={classes.extendedSection}>
          <TableCell
            borderless={!open}
            colSpan={5}
            className={classes.extendedSectionCell}
          >
            <Collapse in={open} unmountOnExit>
              <Grid container direction="row" spacing={0}>
                <Grid item xs={12} sm={4} md={5}>
                  <Box p={1}>
                    <UploadFormControl label="Custom Field Name" error={false}>
                      <FieldNameStringField columnFieldMap={columnFieldMap} />
                    </UploadFormControl>
                  </Box>
                </Grid>
                <Grid item xs={12} sm={8} md={7}>
                  <FieldTypeMenu columnFieldMap={columnFieldMap} />
                </Grid>
              </Grid>
            </Collapse>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

export default (observer(Row): ComponentType<ColumnFieldMapRowProps>);
