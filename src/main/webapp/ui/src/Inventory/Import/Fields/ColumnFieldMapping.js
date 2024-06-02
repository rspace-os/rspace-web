// @flow

import React, { type Node, type ComponentType } from "react";
import Checkbox from "@mui/material/Checkbox";
import Grid from "@mui/material/Grid";
import HelpTextAlert from "../../../components/HelpTextAlert";
import Row from "./ColumnFieldMapRow";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "./TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import { withStyles } from "Styles";
import OverlayLoadingSpinner from "../../components/OverlayLoadingSpinner";
import RelativeBox from "../../../components/RelativeBox";
import ImportModel from "../../../stores/models/ImportModel";
import { type ImportRecordType } from "../../../stores/stores/ImportStore";
import { type URL } from "../../../util/types";
import { Link } from "react-router-dom";

const MatchTemplateAlert = ({ importData }: {| importData: ImportModel |}) => {
  const matchExistingTemplate = importData.importMatchesExistingTemplate;
  return (
    <HelpTextAlert
      severity="error"
      condition={
        importData.isSamplesImport &&
        importData.nameFieldIsSelected &&
        !importData.createNewTemplate &&
        !matchExistingTemplate?.matches
      }
      title="The columns of the CSV file do not match the selected template. Please edit the fields of the template or the supplied CSV file."
      text={
        matchExistingTemplate && !matchExistingTemplate.matches
          ? matchExistingTemplate.reason
          : ""
      }
    />
  );
};

const NameMappingAlert = ({
  importData,
  rowCount,
}: {|
  importData: ImportModel,
  rowCount: number,
|}) => {
  const labelByRecordType = importData.byRecordType("label") || "records";
  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        !importData.nameFieldIsSelected
      }
      text={`You must select one column to convert to the Name of the ${labelByRecordType}.`}
    />
  );
};

const QuantityConversionAlert = ({
  importData,
}: {|
  importData: ImportModel,
|}) => {
  const { unitStore } = useStores();
  const mappingsByRecordType = importData.byRecordType("mappings");
  const rowCount: ?number = mappingsByRecordType?.length;
  const labelByRecordType = importData.byRecordType("label") || "records";
  const unitLabel = unitStore.getUnit(
    importData.templateInfo?.defaultUnitId || 3
  )?.label;

  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        importData.isSamplesImport &&
        !importData.quantityFieldIsSelected
      }
      text={`Quantity conversion is not set. All imported ${labelByRecordType} will have a total quantity of 1 ${
        unitLabel || "ml"
      }.`}
    />
  );
};

const WithoutConversionAlert = ({
  importData,
  rowCount,
}: {|
  importData: ImportModel,
  rowCount: number,
|}) => {
  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        importData.unconvertedFieldIsSelected
      }
      text="You have one or more columns selected without conversion. The columns' data will not be used."
    />
  );
};

const SampleReferenceAlert = ({
  importData,
  rowCount,
}: {|
  importData: ImportModel,
  rowCount: number,
|}) => {
  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        importData.isSubSamplesImport &&
        !importData.anyParentSamplesFieldIsSelected
      }
      text={`You must select one column that refers to a Sample.`}
    />
  );
};

const ParentSampleAlert = ({
  importData,
  rowCount,
  onTypeSelect,
}: {|
  importData: ImportModel,
  rowCount: number,
  onTypeSelect: (ImportRecordType) => URL,
|}) => {
  const labelByRecordType = importData.byRecordType("label") || "records";
  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        importData.isSubSamplesImport &&
        importData.parentSamplesImportIdUndefined
      }
      text={
        <>
          RSpace cannot find Parent Sample Import IDs for {labelByRecordType}.
          Please ensure you are importing a{" "}
          <Link to={onTypeSelect("SAMPLES")}>Samples CSV</Link> with mapped
          &quot;Import ID&quot;.
        </>
      }
    />
  );
};

const ParentContainerAlert = ({
  importData,
  rowCount,
  onTypeSelect,
}: {|
  importData: ImportModel,
  rowCount: number,
  onTypeSelect: (ImportRecordType) => URL,
|}) => {
  const labelByRecordType = importData.byRecordType("label") || "records";
  return (
    <HelpTextAlert
      severity="info"
      condition={
        typeof rowCount === "number" &&
        rowCount > 0 &&
        importData.parentContainersImportIdUndefined
      }
      text={
        <>
          RSpace cannot find Parent Containers Import IDs for{" "}
          {labelByRecordType}. Please ensure you are importing a{" "}
          <Link to={onTypeSelect("CONTAINERS")}>Containers CSV</Link> with
          mapped &quot;Import ID&quot;, or unselect the &quot;Parent Container
          Import ID&quot; conversion.
        </>
      }
    />
  );
};

const SimpleBottomHeadCell = withStyles<
  {| children: Node, colSpan?: number |},
  { root: string }
>({
  root: {
    fontWeight: "600",
  },
})(
  ({
    children,
    classes,
    colSpan,
  }: {
    children: Node,
    classes: {},
    colSpan?: number,
  }) => (
    <TableCell
      padding="none"
      align="left"
      variant="head"
      classes={classes}
      colSpan={colSpan}
    >
      {children}
    </TableCell>
  )
);

type MappingArgs = {|
  onTypeSelect: (ImportRecordType) => URL,
|};

function ColumnFieldMapping({ onTypeSelect }: MappingArgs): Node {
  const { importStore } = useStores();

  const importData = importStore.importData;
  if (!importData) return null;

  const labelByRecordType = importData.byRecordType("label") || "records";

  const mappingsByRecordType = importData.byRecordType("mappings");

  const numSelected: ?number = mappingsByRecordType?.filter(
    (m) => m.selected
  ).length;

  const rowCount: number = mappingsByRecordType.length;

  return (
    <Grid container direction="column" spacing={1}>
      <MatchTemplateAlert importData={importData} />
      <NameMappingAlert importData={importData} rowCount={rowCount} />
      <QuantityConversionAlert importData={importData} />
      <WithoutConversionAlert importData={importData} rowCount={rowCount} />
      <SampleReferenceAlert importData={importData} rowCount={rowCount} />
      <ParentSampleAlert
        importData={importData}
        rowCount={rowCount}
        onTypeSelect={onTypeSelect}
      />
      <ParentContainerAlert
        importData={importData}
        rowCount={rowCount}
        onTypeSelect={onTypeSelect}
      />
      <Grid item>
        <RelativeBox>
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
                {mappingsByRecordType?.map((m, i) => (
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
        </RelativeBox>
      </Grid>
      <HelpTextAlert
        severity="info"
        condition={
          typeof mappingsByRecordType?.length === "number" &&
          mappingsByRecordType.length < 1
        }
        text="Column conversion is only available once a CSV file has been selected."
      />
      <HelpTextAlert
        severity="error"
        condition={
          Boolean(mappingsByRecordType) &&
          !importData.nameFieldIsSelected &&
          importStore.isCurrentImportState("nameRequired")
        }
        text={`It is required that a column be mapped to 'Name', as all ${labelByRecordType} must have a name.`}
      />
    </Grid>
  );
}

export default (observer(ColumnFieldMapping): ComponentType<MappingArgs>);
