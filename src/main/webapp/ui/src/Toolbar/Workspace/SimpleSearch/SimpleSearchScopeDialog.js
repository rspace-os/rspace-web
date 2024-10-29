import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faFilter, faSearch, faBars } from "@fortawesome/free-solid-svg-icons";
library.add(faFilter, faSearch, faBars);

export default function SimpleSearchScopeDialog(props) {
  return (
    <Dialog open={props.open}>
      <DialogTitle>Scope search within records</DialogTitle>
      <DialogContent>
        <DialogContentText>
          You have selected the records listed below.
        </DialogContentText>
        {props.selectedRecords.map((r) => (
          <Chip
            style={{ marginRight: "10px" }}
            key={r}
            icon={<FontAwesomeIcon icon="folder" style={{ padding: "10px" }} />}
            label={r}
            clickable
            variant="outlined"
            onDelete={() => props.removeRecord(r)}
            deleteIcon={
              <FontAwesomeIcon icon="times" style={{ padding: "10px" }} />
            }
          />
        ))}
        <DialogContentText style={{ marginTop: "1.5em" }}>
          Click "Within selected" if you wish to search within the specified
          records, or "Search everywhere" otherwise.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={props.searchEverywhere}
          color="primary"
          variant={props.selectedRecords.length ? "text" : "contained"}
          data-test-id="s-search-scoped-everywhere"
        >
          Search everywhere
        </Button>
        <Button
          onClick={props.submit}
          color="primary"
          disabled={!props.selectedRecords.length}
          variant="contained"
          autoFocus
          data-test-id="s-search-scoped-within"
        >
          Within selected
        </Button>
      </DialogActions>
    </Dialog>
  );
}
