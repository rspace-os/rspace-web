import { faFolder } from "@fortawesome/free-solid-svg-icons/faFolder";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { useTranslation } from "react-i18next";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function SimpleSearchScopeDialog(props: any) {
  const { t } = useTranslation("workspace");

  return (
    <Dialog open={props.open}>
      <DialogTitle>{t("toolbar.simpleSearch.scopeDialog.title")}</DialogTitle>
      <DialogContent>
        <DialogContentText>{t("toolbar.simpleSearch.scopeDialog.selectedRecords")}</DialogContentText>
        {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
        {props.selectedRecords.map((r: any) => (
          <Chip
            sx={{ marginRight: "10px" }}
            key={r}
            icon={<FontAwesomeIcon icon={faFolder} style={{ padding: "10px" }} />}
            label={r}
            clickable
            variant="outlined"
            onDelete={() => props.removeRecord(r)}
            deleteIcon={<FontAwesomeIcon icon={faTimes} style={{ padding: "10px" }} />}
          />
        ))}
        <DialogContentText sx={{ marginTop: "1.5em" }}>
          {t("toolbar.simpleSearch.scopeDialog.instructions")}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={props.searchEverywhere}
          color="primary"
          variant={props.selectedRecords.length ? "text" : "contained"}
          data-test-id="s-search-scoped-everywhere"
        >
          {t("toolbar.simpleSearch.scopeDialog.searchEverywhere")}
        </Button>
        <Button
          onClick={props.submit}
          color="primary"
          disabled={!props.selectedRecords.length}
          variant="contained"
          autoFocus
          data-test-id="s-search-scoped-within"
        >
          {t("toolbar.simpleSearch.scopeDialog.withinSelected")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
