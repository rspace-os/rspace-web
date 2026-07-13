import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import type { PageSize } from "./common";

export type WordExportDetails = {
  exportFormat: "WORD";
  exportName: string;
  pageSize: PageSize;
  defaultPageSize: PageSize;
  setPageSizeAsDefault: boolean;
};

export type WordExportDetailsArgs = {
  exportDetails: WordExportDetails;
  updateExportDetails: <T extends keyof WordExportDetails>(key: T, value: WordExportDetails[T]) => void;
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
  exportDetails: { exportName, pageSize, setPageSizeAsDefault, defaultPageSize },
  updateExportDetails,
  validations,
}: WordExportArgs): React.ReactNode {
  const { t } = useTranslation("workspace");
  const pageSizeId = useId();
  return (
    <Grid container spacing={1}>
      <Grid size={12}>
        <TextField
          variant="standard"
          fullWidth
          error={validations.submitAttempt && !validations.inputValidations.exportName}
          label={t("export.format.word.name")}
          required
          value={exportName}
          onChange={({ target: { value } }) => updateExportDetails("exportName", value)}
          margin="normal"
          data-test-id="word-title"
        />
      </Grid>
      <Grid container>
        <Grid size={5}>
          <InputLabel htmlFor={pageSizeId}>{t("export.format.word.pageFormatLabel")}</InputLabel>
          <Select
            variant="standard"
            fullWidth
            value={pageSize}
            onChange={({ target: { value } }) => updateExportDetails("pageSize", value)}
            inputProps={{ id: pageSizeId }}
            data-test-id="word-size"
          >
            <MenuItem value={"A4"} data-test-id="word-size-a4">
              {t("export.format.word.pageSize.a4")}
            </MenuItem>
            <MenuItem value={"LETTER"} data-test-id="word-size-letter">
              {t("export.format.word.pageSize.letter")}
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
              label={t("export.format.word.setDefault", { pageSize })}
            />
          )}
        </Grid>
      </Grid>
    </Grid>
  );
}
