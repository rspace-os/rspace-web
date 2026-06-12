import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import type React from "react";
// biome-ignore lint/style/useImportType: initial biome migration
import { type PageSize } from "./common";

export type PdfExportDetails = {
  exportFormat: "PDF";
  exportName: string;
  provenance: boolean;
  comments: boolean;
  annotations: boolean;
  restartPageNumberPerDoc: boolean;
  pageSize: PageSize;
  defaultPageSize: PageSize;
  dateType: "EXP" | "NEW" | "UPD";
  includeFooterAtEndOnly: boolean;
  setPageSizeAsDefault: boolean;
  includeFieldLastModifiedDate: boolean;
};

export type PdfExportDetailsArgs = {
  exportDetails: PdfExportDetails;
  updateExportDetails: <T extends keyof PdfExportDetails>(key: T, value: PdfExportDetails[T]) => void;
};

type PdfExportArgs = PdfExportDetailsArgs & {
  validations: {
    submitAttempt: boolean;
    inputValidations: {
      exportName: boolean;
    };
  };
};

const checkboxes = {
  provenance: "Include provenance information",
  comments: "Include comments",
  annotations: "Include image annotations",
  includeFieldLastModifiedDate: "Include last modified dates for fields",
  restartPageNumberPerDoc: "Restart page numbering for each document",
  includeFooterAtEndOnly: "Insert date footer at file end only",
};

export default function PdfExport({
  exportDetails: { exportName, pageSize, defaultPageSize, setPageSizeAsDefault, dateType, ...rest },
  validations,
  updateExportDetails,
}: PdfExportArgs): React.ReactNode {
  return (
    <Stack spacing={2}>
      <TextField
        variant="standard"
        error={validations.submitAttempt && !validations.inputValidations.exportName}
        id="name"
        label="File name *"
        helperText="Name your file"
        value={exportName}
        onChange={({ target: { value } }) => updateExportDetails("exportName", value)}
        margin="normal"
        fullWidth
        data-test-id="pdf-name"
      />
      <Grid container>
        <Grid sx={{ mt: 2 }} size={5}>
          <InputLabel htmlFor="pageSize">Page format: </InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={pageSize}
            onChange={({ target: { value } }) => updateExportDetails("pageSize", value)}
            inputProps={{ name: "pageSize", id: "pageSize" }}
            data-test-id="pdf-size"
          >
            <MenuItem value={"A4"} data-test-id="size-a4">
              A4
            </MenuItem>
            <MenuItem value={"LETTER"} data-test-id="size-letter">
              Letter
            </MenuItem>
          </Select>
          {pageSize !== defaultPageSize && (
            <FormControlLabel
              control={
                <Checkbox
                  onChange={({ target: { checked } }) => updateExportDetails("setPageSizeAsDefault", checked)}
                  color="primary"
                  checked={setPageSizeAsDefault}
                  data-test-id="set-size-default"
                />
              }
              label={`Set ${pageSize} as default`}
            />
          )}
        </Grid>
        <Grid sx={{ mt: 2 }} size={2}></Grid>
        <Grid sx={{ mt: 2 }} size={5}>
          <InputLabel htmlFor="dateType">Date on page footer</InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={dateType}
            onChange={({ target: { value } }) => updateExportDetails("dateType", value)}
            inputProps={{ name: "dateType", id: "dateType" }}
            data-test-id="date-type"
          >
            <MenuItem value={"EXP"} data-test-id="date-type-exp">
              Exported
            </MenuItem>
            <MenuItem value={"NEW"} data-test-id="date-type-new">
              Created
            </MenuItem>
            <MenuItem value={"UPD"} data-test-id="ddate-type-upd">
              Last modified date
            </MenuItem>
          </Select>
        </Grid>
      </Grid>
      <Stack>
        {(Object.keys(checkboxes) as Array<keyof typeof checkboxes>).map((k) => (
          <FormControlLabel
            key={k}
            control={
              <Switch
                checked={rest[k]}
                onChange={({ target: { checked } }) => updateExportDetails(k, checked)}
                color="primary"
                data-test-id={k}
                slotProps={{ input: { role: "checkbox" } }}
              />
            }
            label={checkboxes[k]}
          />
        ))}
      </Stack>
    </Stack>
  );
}
