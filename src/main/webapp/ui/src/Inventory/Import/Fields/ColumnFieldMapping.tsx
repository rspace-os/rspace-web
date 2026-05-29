import React from "react";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Alert, { type AlertColor } from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Row from "./ColumnFieldMapRow";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "./TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import OverlayLoadingSpinner from "../../components/OverlayLoadingSpinner";
import Box from "@mui/material/Box";
import { ColumnFieldMap } from "../../../stores/models/ImportModel";
import { type ImportRecordType } from "../../../stores/stores/ImportStore";
import { type URL } from "../../../util/types";
import { Link } from "react-router-dom";

type AlertSpec = {
  key: string;
  show: boolean;
  severity: AlertColor;
  title?: React.ReactNode;
  content: React.ReactNode;
};

function AlertList({ specs }: { specs: ReadonlyArray<AlertSpec> }) {
  return (
    <>
      {specs
        .filter((s) => s.show)
        .map((s) => (
          <Alert key={s.key} severity={s.severity}>
            {s.title ? <AlertTitle>{s.title}</AlertTitle> : null}
            {s.content}
          </Alert>
        ))}
    </>
  );
}

type SimpleBottomHeadCellArgs = React.ComponentProps<typeof TableCell> & {
  children: React.ReactNode;
  colSpan?: number;
};

function SimpleBottomHeadCell({
  children,
  colSpan,
  ...rest
}: SimpleBottomHeadCellArgs): React.ReactNode {
  return (
    <TableCell
      padding="none"
      align="left"
      variant="head"
      sx={{ fontWeight: 600 }}
      colSpan={colSpan}
      {...rest}
    >
      {children}
    </TableCell>
  );
}

type MappingArgs = {
  onTypeSelect: (type: ImportRecordType) => URL;
};

function ColumnFieldMapping({ onTypeSelect }: MappingArgs): React.ReactNode {
  const { importStore, unitStore } = useStores();

  const importData = importStore.importData;
  if (!importData) return null;

  const labelByRecordType = importData.byRecordType("label") || "records";
  const label =
    typeof labelByRecordType === "string" ? labelByRecordType : "records";

  const mappingsByRecordType = importData.byRecordType("mappings") as
    | ColumnFieldMap[]
    | undefined;

  const numSelected: number | undefined = mappingsByRecordType?.filter(
    (m: ColumnFieldMap) => m.selected,
  ).length;

  const rowCount: number = mappingsByRecordType?.length ?? 0;
  const hasRows = rowCount > 0;

  const matchExistingTemplate = importData.importMatchesExistingTemplate;
  const unitLabel =
    unitStore.getUnit(importData.templateInfo?.defaultUnitId || 3)?.label ??
    "ml";

  const alertsBeforeTable: ReadonlyArray<AlertSpec> = [
    {
      key: "template-mismatch",
      show:
        importData.isSamplesImport &&
        importData.nameFieldIsSelected &&
        !importData.createNewTemplate &&
        !matchExistingTemplate?.matches,
      severity: "error",
      title:
        "The columns of the CSV file do not match the selected template. Please edit the fields of the template or the supplied CSV file.",
      content: matchExistingTemplate?.matches
        ? ""
        : matchExistingTemplate?.reason ?? "",
    },
    {
      key: "name-required-info",
      show: hasRows && !importData.nameFieldIsSelected,
      severity: "info",
      content: `You must select one column to convert to the Name of the ${label}.`,
    },
    {
      key: "quantity-conversion",
      show:
        hasRows &&
        importData.isSamplesImport &&
        !importData.quantityFieldIsSelected,
      severity: "info",
      content: `Quantity conversion is not set. All imported ${label} will have a total quantity of 1 ${unitLabel}.`,
    },
    {
      key: "unconverted",
      show: hasRows && importData.unconvertedFieldIsSelected,
      severity: "info",
      content:
        "You have one or more columns selected without conversion. The columns' data will not be used.",
    },
    {
      key: "subsample-parent-required",
      show:
        hasRows &&
        importData.isSubSamplesImport &&
        !importData.anyParentSamplesFieldIsSelected,
      severity: "info",
      content: "You must select one column that refers to a Sample.",
    },
    {
      key: "parent-sample-import-id",
      show:
        hasRows &&
        importData.isSubSamplesImport &&
        importData.parentSamplesImportIdUndefined,
      severity: "info",
      content: (
        <>
          RSpace cannot find Parent Sample Import IDs for {label}. Please ensure
          you are importing a{" "}
          <Link to={onTypeSelect("SAMPLES")}>Samples CSV</Link> with mapped
          &quot;Import ID&quot;.
        </>
      ),
    },
    {
      key: "parent-container-import-id",
      show: hasRows && importData.parentContainersImportIdUndefined,
      severity: "info",
      content: (
        <>
          RSpace cannot find Parent Containers Import IDs for {label}. Please
          ensure you are importing a{" "}
          <Link to={onTypeSelect("CONTAINERS")}>Containers CSV</Link> with mapped
          &quot;Import ID&quot;, or unselect the &quot;Parent Container Import
          ID&quot; conversion.
        </>
      ),
    },
  ];

  const alertsAfterTable: ReadonlyArray<AlertSpec> = [
    {
      key: "no-csv",
      show: rowCount < 1,
      severity: "info",
      content:
        "Column conversion is only available once a CSV file has been selected.",
    },
    {
      key: "name-required-error",
      show:
        Boolean(mappingsByRecordType) &&
        !importData.nameFieldIsSelected &&
        importStore.isCurrentImportState("nameRequired"),
      severity: "error",
      content: `It is required that a column be mapped to 'Name', as all ${label} must have a name.`,
    },
  ];

  return (
    <Stack spacing={1}>
      <AlertList specs={alertsBeforeTable} />
      <Box sx={{ position: "relative" }}>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell variant="head" padding="checkbox">
                    <Checkbox
                      indeterminate={
                        (numSelected ?? 0) > 0 &&
                        (numSelected ?? 0) < (rowCount ?? 0)
                      }
                      checked={numSelected === rowCount}
                      disabled={rowCount === 0}
                      onChange={() => importData.toggleSelection()}
                      color="default"
                    />
                  </TableCell>
                  <SimpleBottomHeadCell>Found CSV Columns</SimpleBottomHeadCell>
                  <SimpleBottomHeadCell colSpan={3}>
                    Convert To
                  </SimpleBottomHeadCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {mappingsByRecordType?.map((m: ColumnFieldMap, i: number) => (
                  <Row
                    key={i}
                    columnFieldMap={m}
                    existingTemplate={!importData.createNewTemplate}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {importData.isSamplesImport &&
            !importData.createNewTemplate &&
            importData.template?.loading && <OverlayLoadingSpinner />}
        </Box>
      <AlertList specs={alertsAfterTable} />
    </Stack>
  );
}

export default observer(ColumnFieldMapping);
