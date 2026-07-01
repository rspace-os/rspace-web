import Alert, { type AlertColor } from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import TransRichText from "@/modules/common/i18n/TransRichText";
import type { ColumnFieldMap } from "../../../stores/models/ImportModel";
import type { ImportRecordType } from "../../../stores/stores/ImportStore";
import useStores from "../../../stores/use-stores";
import type { URL } from "../../../util/types";
import OverlayLoadingSpinner from "../../components/OverlayLoadingSpinner";
import Row from "./ColumnFieldMapRow";
import TableCell from "./TableCell";

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

function SimpleBottomHeadCell({ children, colSpan, ...rest }: SimpleBottomHeadCellArgs): React.ReactNode {
  return (
    <TableCell padding="none" align="left" variant="head" sx={{ fontWeight: 600 }} colSpan={colSpan} {...rest}>
      {children}
    </TableCell>
  );
}

type MappingArgs = {
  onTypeSelect: (type: ImportRecordType) => URL;
};

function ColumnFieldMapping({ onTypeSelect }: MappingArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { importStore, unitStore } = useStores();

  const importData = importStore.importData;
  if (!importData) return null;

  const labelByRecordType = importData.byRecordType("label") || "records";
  const label = typeof labelByRecordType === "string" ? labelByRecordType : "records";

  const mappingsByRecordType = importData.byRecordType("mappings") as ColumnFieldMap[] | undefined;

  const numSelected: number | undefined = mappingsByRecordType?.filter((m: ColumnFieldMap) => m.selected).length;

  const rowCount: number = mappingsByRecordType?.length ?? 0;
  const hasRows = rowCount > 0;

  const matchExistingTemplate = importData.importMatchesExistingTemplate;
  const unitLabel = unitStore.getUnit(importData.templateInfo?.defaultUnitId || 3)?.label ?? "ml";

  const alertsBeforeTable: ReadonlyArray<AlertSpec> = [
    {
      key: "template-mismatch",
      show:
        importData.isSamplesImport &&
        importData.nameFieldIsSelected &&
        !importData.createNewTemplate &&
        !matchExistingTemplate?.matches,
      severity: "error",
      title: t("import.columnMapping.templateMismatch"),
      content: matchExistingTemplate?.matches ? "" : (matchExistingTemplate?.reason ?? ""),
    },
    {
      key: "name-required-info",
      show: hasRows && !importData.nameFieldIsSelected,
      severity: "info",
      content: t("import.columnMapping.nameRequiredInfo", { recordType: label }),
    },
    {
      key: "quantity-conversion",
      show: hasRows && importData.isSamplesImport && !importData.quantityFieldIsSelected,
      severity: "info",
      content: t("import.columnMapping.quantityConversion", { recordType: label, unitLabel }),
    },
    {
      key: "unconverted",
      show: hasRows && importData.unconvertedFieldIsSelected,
      severity: "info",
      content: t("import.columnMapping.unconverted"),
    },
    {
      key: "subsample-parent-required",
      show: hasRows && importData.isSubSamplesImport && !importData.anyParentSamplesFieldIsSelected,
      severity: "info",
      content: t("import.columnMapping.parentSampleRequired"),
    },
    {
      key: "parent-sample-import-id",
      show: hasRows && importData.isSubSamplesImport && importData.parentSamplesImportIdUndefined,
      severity: "info",
      content: (
        <TransRichText
          ns="inventory"
          i18nKey="import.columnMapping.parentSampleImportIdMissing"
          values={{ label }}
          components={{
            link: <Link to={onTypeSelect("SAMPLES")} />,
          }}
        />
      ),
    },
    {
      key: "parent-container-import-id",
      show: hasRows && importData.parentContainersImportIdUndefined,
      severity: "info",
      content: (
        <TransRichText
          ns="inventory"
          i18nKey="import.columnMapping.parentContainerImportIdMissing"
          values={{ label }}
          components={{
            link: <Link to={onTypeSelect("CONTAINERS")} />,
          }}
        />
      ),
    },
  ];

  const alertsAfterTable: ReadonlyArray<AlertSpec> = [
    {
      key: "no-csv",
      show: rowCount < 1,
      severity: "info",
      content: t("import.columnMapping.noCsv"),
    },
    {
      key: "name-required-error",
      show:
        Boolean(mappingsByRecordType) &&
        !importData.nameFieldIsSelected &&
        importStore.isCurrentImportState("nameRequired"),
      severity: "error",
      content: t("import.columnMapping.nameRequiredError", { recordType: label }),
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
                    indeterminate={(numSelected ?? 0) > 0 && (numSelected ?? 0) < (rowCount ?? 0)}
                    checked={numSelected === rowCount}
                    disabled={rowCount === 0}
                    onChange={() => importData.toggleSelection()}
                    color="default"
                  />
                </TableCell>
                <SimpleBottomHeadCell>{t("import.columnMapping.columns.foundCsvColumns")}</SimpleBottomHeadCell>
                <SimpleBottomHeadCell colSpan={3}>{t("import.columnMapping.columns.convertTo")}</SimpleBottomHeadCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {mappingsByRecordType?.map((m: ColumnFieldMap, i: number) => (
                <Row key={i} columnFieldMap={m} existingTemplate={!importData.createNewTemplate} />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        {importData.isSamplesImport && !importData.createNewTemplate && importData.template?.loading && (
          <OverlayLoadingSpinner />
        )}
      </Box>
      <AlertList specs={alertsAfterTable} />
    </Stack>
  );
}

export default observer(ColumnFieldMapping);
