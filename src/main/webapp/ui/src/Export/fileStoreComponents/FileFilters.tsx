import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { Trans, useTranslation } from "react-i18next";

type FoundLinksListingArgs = {
  maxFileSizeInMB: number | string;
  setMaxFileSizeInMB: (maxFileSizeInMB: number | string) => void;

  // comma separated string of extensions
  excludedFileExtensions: string;
  setExcludedFileExtensions: (excludedFileExtensions: string) => void;
};

/**
 * This component allows the user to filter the files from the filestore that
 * will be included in the export, either based on their size or their
 * extension.
 */
export default function FileFilters({
  maxFileSizeInMB,
  setMaxFileSizeInMB,
  excludedFileExtensions,
  setExcludedFileExtensions,
}: FoundLinksListingArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>{t("export.fileStore.filters.heading")}</h2>
      <p>
        {excludedFileExtensions === "" ? (
          <Trans i18nKey="export.fileStore.filters.summary" ns="workspace" values={{ sizeMB: maxFileSizeInMB }} />
        ) : (
          <Trans
            i18nKey="export.fileStore.filters.summaryWithExclusions"
            ns="workspace"
            values={{ sizeMB: maxFileSizeInMB, extensions: excludedFileExtensions }}
          />
        )}
      </p>
      <Button
        variant="outlined"
        color="primary"
        onClick={() => setOpen(true)}
        data-test-id="button-change-filters"
        fullWidth
      >
        {t("export.fileStore.filters.changeButton")}
      </Button>
      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>{t("export.fileStore.filters.dialogTitle")}</DialogTitle>
        <DialogContent>
          <Grid container>
            <Grid size={12}>
              <TextField
                variant="standard"
                label={t("export.fileStore.filters.sizeLimitLabel")}
                value={maxFileSizeInMB}
                onChange={({ target: { value } }) => setMaxFileSizeInMB(value)}
                type="number"
                margin="normal"
                data-test-id="file-size-limit"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
            </Grid>
            <Grid size={12}>
              <TextField
                variant="standard"
                label={t("export.fileStore.filters.excludeLabel")}
                value={excludedFileExtensions}
                onChange={({ target: { value } }) => setExcludedFileExtensions(value)}
                margin="normal"
                data-test-id="types-to-exclude"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} color="primary" data-test-id="button-ok-links">
            {t("common:actions.ok")}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
