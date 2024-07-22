//@flow

import React, { type Node } from "react";
import Switch from "@mui/material/Switch";
import Grid from "@mui/material/Grid";
import FormControlLabel from "@mui/material/FormControlLabel";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Checkbox from "@mui/material/Checkbox";
import { type PageSize } from "./FormatSpecificOptions";

export type PdfExportDetails = {|
  exportFormat: "PDF",
  exportName: string,
  provenance: boolean,
  comments: boolean,
  annotations: boolean,
  restartPageNumberPerDoc: boolean,
  pageSize: PageSize,
  defaultPageSize: PageSize,
  dateType: "EXP" | "NEW" | "UPD",
  includeFooter: boolean,
  setPageSizeAsDefault: boolean,
  includeFieldLastModifiedDate: boolean,
|};

export type PdfExportDetailsArgs = {|
  exportDetails: PdfExportDetails,
  updateExportDetails: <T: $Keys<PdfExportDetails>>(
    T,
    PdfExportDetails[T]
  ) => void,
|};

type PdfExportArgs = {|
  ...PdfExportDetailsArgs,
  validations: {|
    submitAttempt: boolean,
    inputValidations: {|
      exportName: boolean,
    |},
  |},
|};

const checkboxes = {
  provenance: "Include provenance information",
  comments: "Include comments",
  annotations: "Include image annotations",
  includeFieldLastModifiedDate: "Include last modified dates for fields",
  restartPageNumberPerDoc: "Restart page numbering for each document",
  includeFooter: "Insert date footer at file end only",
};

export default function PdfExport({
  exportDetails: {
    exportName,
    pageSize,
    defaultPageSize,
    setPageSizeAsDefault,
    dateType,
    ...rest
  },
  validations,
  updateExportDetails,
}: PdfExportArgs): Node {
  return (
    <Grid container direction="column" spacing={2}>
      <Grid item>
        <TextField
          variant="standard"
          error={
            validations.submitAttempt &&
            !validations.inputValidations.exportName
          }
          id="name"
          label="File name *"
          helperText="Name your file"
          value={exportName}
          onChange={({ target: { value } }) =>
            updateExportDetails("exportName", value)
          }
          margin="normal"
          fullWidth
          data-test-id="pdf-name"
        />
      </Grid>
      <Grid item container>
        <Grid item xs={5} mt={2}>
          <InputLabel htmlFor="pageSize">Page format: </InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={pageSize}
            onChange={({ target: { value } }) =>
              updateExportDetails("pageSize", value)
            }
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
                  onChange={({ target: { checked } }) =>
                    updateExportDetails("setPageSizeAsDefault", checked)
                  }
                  color="primary"
                  checked={setPageSizeAsDefault}
                  data-test-id="set-size-default"
                />
              }
              label={`Set ${pageSize} as default`}
            />
          )}
        </Grid>
        <Grid item xs={2} mt={2}></Grid>
        <Grid item xs={5} mt={2}>
          <InputLabel htmlFor="dateType">Date on page footer</InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={dateType}
            onChange={({ target: { value } }) =>
              updateExportDetails("dateType", value)
            }
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
      <Grid item container direction="column">
        {Object.keys(checkboxes).map((k) => (
          <Grid item key={k}>
            <FormControlLabel
              control={
                <Switch
                  checked={rest[k]}
                  onChange={({ target: { checked } }) =>
                    updateExportDetails(k, checked)
                  }
                  color="primary"
                  data-test-id={k}
                />
              }
              label={checkboxes[k]}
            />
          </Grid>
        ))}
      </Grid>
    </Grid>
  );
}
