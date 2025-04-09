import React, { useId } from "react";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import Grid from "@mui/material/Grid";
import FormControlLabel from "@mui/material/FormControlLabel";
import Checkbox from "@mui/material/Checkbox";
import { type PageSize } from "./common";

export type WordExportDetails = {
  exportFormat: "WORD";
  exportName: string;
  pageSize: PageSize;
  defaultPageSize: PageSize;
  setPageSizeAsDefault: boolean;
};

export type WordExportDetailsArgs = {
  exportDetails: WordExportDetails;
  updateExportDetails: <T extends keyof WordExportDetails>(
    key: T,
    value: WordExportDetails[T]
  ) => void;
};

type WordExportArgs = WordExportDetailsArgs & {
  validations: {
    submitAttempt: boolean;
    inputValidations: {
      exportName: boolean;
    };
  };
};

export default function WordExport({
  exportDetails: {
    exportName,
    pageSize,
    setPageSizeAsDefault,
    defaultPageSize,
  },
  updateExportDetails,
  validations,
}: WordExportArgs): React.ReactNode {
  const pageSizeId = useId();
  return (
    <Grid container spacing={1}>
      <Grid item xs={12}>
        <TextField
          variant="standard"
          fullWidth
          error={
            validations.submitAttempt &&
            !validations.inputValidations.exportName
          }
          label="Name your file"
          value={exportName}
          onChange={({ target: { value } }) =>
            updateExportDetails("exportName", value)
          }
          margin="normal"
          data-test-id="word-title"
        />
      </Grid>
      <Grid item container>
        <Grid item xs={5}>
          <InputLabel htmlFor={pageSizeId}>Page format: </InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={pageSize}
            onChange={({ target: { value } }) =>
              updateExportDetails("pageSize", value as PageSize)
            }
            inputProps={{ id: pageSizeId }}
            data-test-id="word-size"
          >
            <MenuItem value={"A4"} data-test-id="word-size-a4">
              A4
            </MenuItem>
            <MenuItem value={"LETTER"} data-test-id="word-size-letter">
              Letter
            </MenuItem>
          </Select>
          {pageSize !== defaultPageSize && (
            <FormControlLabel
              control={
                <Checkbox
                  onChange={({ target: { checked } }) => {
                    updateExportDetails("setPageSizeAsDefault", checked);
                  }}
                  color="primary"
                  checked={setPageSizeAsDefault}
                  data-test-id="set-size-default"
                />
              }
              label={`Set ${pageSize} as default.`}
            />
          )}
        </Grid>
      </Grid>
    </Grid>
  );
}
