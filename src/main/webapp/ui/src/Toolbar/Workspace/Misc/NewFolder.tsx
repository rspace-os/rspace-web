import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormHelperText from "@mui/material/FormHelperText";
import TextField from "@mui/material/TextField";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AnalyticsContext from "../../../stores/contexts/Analytics";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const workspaceSettings: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const reloadFileTreeBrowser: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getAndDisplayWorkspaceResults: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const getValidationErrorString: any;

export default function NewNotebook() {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const [navigateAfterCreate, setNavigateAfterCreate] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const { trackEvent } = React.useContext(AnalyticsContext);

  useEffect(() => {
    $(document).on("click", "#createFolder", () => {
      setOpen(true);
    });
  }, []);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function validateForm(e: any) {
    e.preventDefault();

    if (name.trim().length === 0) {
      setError(t("toolbar.newFolder.validation.nameRequired"));
    } else {
      setError(null);
      handleSubmit();
    }
  }

  function handleSubmit() {
    setOpen(false);
    RS.blockPage(t("toolbar.newFolder.creating"));

    const bodyFormData = new FormData();
    bodyFormData.set("folderNameField", name);
    setName("");

    axios({
      method: "post",
      url: `/workspace/ajax/create_folder/${workspaceSettings.parentFolderId}`,
      data: bodyFormData,
      config: { headers: { "Content-Type": "multipart/form-data" } },
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    } as any)
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .then((response: any) => {
        if (response.data) {
          reloadFileTreeBrowser();
          if (navigateAfterCreate) {
            workspaceSettings.parentFolderId = response.data.data;
          }
          getAndDisplayWorkspaceResults(`/workspace/ajax/view/${workspaceSettings.parentFolderId}`, workspaceSettings);
        } else {
          // @ts-expect-error pragmatic jsx->tsx conversion: `e` is out of scope here in the original JS
          RS.apprise(getValidationErrorString(response.errorMsg, ";", true), true, e.target);
          RS.unblockPage();
        }
        trackEvent("user:create:folder:workspace");
      })
      .catch((error) => {
        RS.unblockPage();
        RS.ajaxFailed("Display", false, error);
      });
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const focusUsernameInputField = (input: any) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth>
      <DialogTitle id="form-dialog-title">{t("toolbar.newFolder.title")}</DialogTitle>
      <DialogContent>
        <form onSubmit={validateForm}>
          <FormControl error={error != null} fullWidth>
            <TextField
              variant="standard"
              inputRef={focusUsernameInputField}
              margin="dense"
              placeholder={t("toolbar.newFolder.placeholder")}
              fullWidth
              value={name}
              onChange={(e) => setName(e.target.value)}
              data-test-id="new-folder-name"
              slotProps={{
                htmlInput: { "aria-label": t("toolbar.newFolder.nameAria") },
              }}
            />
            <FormHelperText id="component-error-text">{error}</FormHelperText>
          </FormControl>
          <FormControlLabel
            control={
              <Checkbox
                checked={navigateAfterCreate}
                onChange={(e) => setNavigateAfterCreate(e.target.value !== "true")}
                value={navigateAfterCreate}
                color="primary"
                data-test-id="new-folder-navigate"
                slotProps={{
                  input: { "aria-label": t("toolbar.newFolder.navigateAfterCreate") },
                }}
              />
            }
            label={t("toolbar.newFolder.navigateAfterCreate")}
          />
        </form>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)} sx={{ color: "grey" }} data-test-id="new-folder-cancel">
          {t("common:actions.cancel")}
        </Button>
        <Button onClick={validateForm} color="primary" data-test-id="new-folder-submit">
          {t("common:actions.create")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
