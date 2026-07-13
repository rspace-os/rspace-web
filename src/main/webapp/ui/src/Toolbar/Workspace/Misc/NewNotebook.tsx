import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import TextField from "@mui/material/TextField";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import AnalyticsContext from "../../../stores/contexts/Analytics";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const workspaceSettings: any;

export default function NewNotebook() {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const { trackEvent } = React.useContext(AnalyticsContext);

  useEffect(() => {
    $(document).on("click", "#createNotebook", () => {
      setOpen(true);
    });
  }, []);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function validateForm(e: any) {
    e.preventDefault();
    if (name.trim().length === 0 || name.match(/\//)) {
      setError(t("toolbar.newNotebook.validation.nameRequired"));
    } else {
      setError(null);
      handleSubmit();
    }
  }

  function handleSubmit() {
    setLoading(true);
    const form = $("<form></form>");
    form.attr("method", "POST");
    form.attr("action", `/workspace/create_notebook/${workspaceSettings.parentFolderId}`);
    form.append($(`<input name="notebookNameField" value="${name}"/>`).attr("type", "hidden"));
    $("body").append(form);
    form.submit();
    trackEvent("user:create:notebook:workspace");
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const focusUsernameInputField = (input: any) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <Dialog open={open} onClose={() => setOpen(false)} aria-labelledby="form-dialog-title" fullWidth>
      <DialogTitle id="form-dialog-title">{t("toolbar.newNotebook.title")}</DialogTitle>
      <DialogContent>
        <form onSubmit={validateForm}>
          <FormControl error={error != null} fullWidth>
            <TextField
              variant="standard"
              inputRef={focusUsernameInputField}
              margin="dense"
              placeholder={t("toolbar.newNotebook.name")}
              fullWidth
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-describedby="component-error-text"
              data-test-id="new-notebook-name"
              slotProps={{
                htmlInput: { "aria-label": t("toolbar.newNotebook.name") },
              }}
            />
            <FormHelperText id="component-error-text">{error}</FormHelperText>
          </FormControl>
        </form>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)} sx={{ color: "grey" }} data-test-id="new-notebook-cancel">
          {t("common:actions.cancel")}
        </Button>
        <SubmitSpinnerButton
          label={t("common:actions.create")}
          loading={loading}
          disabled={loading}
          onClick={validateForm}
        />
      </DialogActions>
    </Dialog>
  );
}
