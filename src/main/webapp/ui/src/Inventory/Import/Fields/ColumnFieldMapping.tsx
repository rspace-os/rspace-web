import React from "react";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Alert from "@mui/material/Alert";
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
import ImportModel, {
  ColumnFieldMap,
} from "../../../stores/models/ImportModel";
import { type ImportRecordType } from "../../../stores/stores/ImportStore";
import { type URL } from "../../../util/types";
import { Link } from "react-router-dom";

type QuantityConversionAlertArgs = {
  importData: ImportModel;
};

const QuantityConversionAlert = ({
  importData,
}: QuantityConversionAlertArgs) => {
  const { unitStore } = useStores();
  const mappingsByRecordType = importData.byRecordType("mappings") as
    | ColumnFieldMap[]
    | undefined;
  const rowCount: number | undefined = mappingsByRecordType?.length;
  const labelByRecordType = importData.byRecordType("label") || "records";
  const label =
    typeof labelByRecordType === "string" ? labelByRecordType : "records";
  const unitLabel = unitStore.getUnit(
    importData.templateInfo?.defaultUnitId || 3,
  )?.label;

  return typeof rowCount === "number" &&
    rowCount > 0 &&
    importData.isSamplesImport &&
    !importData.quantityFieldIsSelected ? (
    <Alert severity="info">
      {`Quantity conversion is not set. All imported ${label} will have a total quantity of 1 ${
        unitLabel || "ml"
      }.`}
    </Alert>
  ) : null;
};

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
  const { importStore } = useStores();

  const importData = importStore.importData;
  if (!importData) return null;

  const labelByRecordType = importData.byRecordType("label") || "records";

  const mappingsByRecordType = importData.byRecordType("mappings") as
    | ColumnFieldMap[]
    | undefined;

  const numSelected: number | undefined = mappingsByRecordType?.filter(
    (m: ColumnFieldMap) => m.selected,
  ).length;

  const rowCount: number = mappingsByRecordType?.length ?? 0;

  const label =
    typeof labelByRecordType === "string" ? labelByRecordType : "records";
  const matchExistingTemplate = importData.importMatchesExistingTemplate;

  return (
    <Stack spacing={1}>
      {importData.isSamplesImport &&
      importData.nameFieldIsSelected &&
      !importData.createNewTemplate &&
      !matchExistingTemplate?.matches ? (
        <Alert severity="error">
          <AlertTitle>
            The columns of the CSV file do not match the selected template.
            Please edit the fields of the template or the supplied CSV file.
          </AlertTitle>
          {matchExistingTemplate && !matchExistingTemplate.matches
            ? matchExistingTemplate.reason
            : ""}
        </Alert>
      ) : null}
      {typeof rowCount === "number" &&
      rowCount > 0 &&
      !importData.nameFieldIsSelected ? (
        <Alert severity="info">
          {`You must select one column to convert to the Name of the ${label}.`}
        </Alert>
      ) : null}
      <QuantityConversionAlert importData={importData} />
      {typeof rowCount === "number" &&
      rowCount > 0 &&
      importData.unconvertedFieldIsSelected ? (
        <Alert severity="info">
          {"You have one or more columns selected without conversion. The columns' data will not be used."}
        </Alert>
      ) : null}
      {typeof rowCount === "number" &&
      rowCount > 0 &&
      importData.isSubSamplesImport &&
      !importData.anyParentSamplesFieldIsSelected ? (
        <Alert severity="info">
          {`You must select one column that refers to a Sample.`}
        </Alert>
      ) : null}
      {typeof rowCount === "number" &&
      rowCount > 0 &&
      importData.isSubSamplesImport &&
      importData.parentSamplesImportIdUndefined ? (
        <Alert severity="info">
          RSpace cannot find Parent Sample Import IDs for {label}. Please ensure
          you are importing a{" "}
          <Link to={onTypeSelect("SAMPLES")}>Samples CSV</Link> with mapped
          &quot;Import ID&quot;.
        </Alert>
      ) : null}
      {typeof rowCount === "number" &&
      rowCount > 0 &&
      importData.parentContainersImportIdUndefined ? (
        <Alert severity="info">
          RSpace cannot find Parent Containers Import IDs for {label}. Please
          ensure you are importing a{" "}
          <Link to={onTypeSelect("CONTAINERS")}>Containers CSV</Link> with mapped
          &quot;Import ID&quot;, or unselect the &quot;Parent Container Import
          ID&quot; conversion.
        </Alert>
      ) : null}
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
      {typeof mappingsByRecordType?.length === "number" &&
      mappingsByRecordType.length < 1 ? (
        <Alert severity="info">
          {"Column conversion is only available once a CSV file has been selected."}
        </Alert>
      ) : null}
      {Boolean(mappingsByRecordType) &&
      !importData.nameFieldIsSelected &&
      importStore.isCurrentImportState("nameRequired") ? (
        <Alert severity="error">
          {`It is required that a column be mapped to 'Name', as all ${
            typeof labelByRecordType === "string" ? labelByRecordType : "records"
          } must have a name.`}
        </Alert>
      ) : null}
    </Stack>
  );
}

export default observer(ColumnFieldMapping);
