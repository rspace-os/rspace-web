"use strict";
import React from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Grid from "@mui/material/Grid";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import DnaPreview from "./dnaPreview";
import EnzymeTable from "./enzymeTable";
import FastaView from "./fastaView";
import OrfTable from "./orfTable";
import { createRoot } from "react-dom/client";

function a11yProps(index) {
  return {
    id: `simple-tab-${index}`,
    "aria-controls": `simple-tabpanel-${index}`,
  };
}

export default function SnapGeneDialog(props) {
  const [open, setOpen] = React.useState(true);
  const [tab, setTab] = React.useState(0);
  const [disabled, setDisabled] = React.useState(false);
  const [clicked, setClicked] = React.useState({
    0: 0,
    1: 0,
    2: 0,
    3: 0,
  });

  const handleClose = () => {
    setOpen(false);
    $(".snapgene-dialog").remove(); // clean up after dialog is closed
    RS.showHelpButton();
  };

  const switchTab = (e, value) => {
    setTab(value);
  };

  const handleApply = () => {
    setClicked({
      ...clicked,
      [tab]: clicked[tab] + 1,
    });
  };

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby="form-dialog-title"
      fullWidth={true}
      maxWidth="xl"
    >
      <DialogTitle id="form-dialog-title">SnapGene</DialogTitle>
      <DialogContent>
        <Grid container spacing={2}>
          <Grid item xs={2}>
            <Tabs
              orientation="vertical"
              variant="scrollable"
              value={tab}
              onChange={switchTab}
            >
              <Tab label="DNA preview" {...a11yProps(0)} />
              <Tab label="Enzyme sites" {...a11yProps(1)} />
              <Tab label="View as FASTA" {...a11yProps(2)} />
              <Tab label="ORF table" {...a11yProps(3)} />
            </Tabs>
          </Grid>
          {tab == 0 && (
            <DnaPreview
              id={props.id}
              clicked={clicked["0"]}
              setDisabled={(d) => setDisabled(d)}
            />
          )}
          {tab == 1 && (
            <EnzymeTable
              id={props.id}
              clicked={clicked["1"]}
              setDisabled={(d) => setDisabled(d)}
            />
          )}
          {tab == 2 && (
            <FastaView
              id={props.id}
              clicked={clicked["2"]}
              setDisabled={(d) => setDisabled(d)}
            />
          )}
          {tab == 3 && (
            <OrfTable
              id={props.id}
              clicked={clicked["3"]}
              setDisabled={(d) => setDisabled(d)}
            />
          )}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          Close
        </Button>
        <Button
          onClick={handleApply}
          color="primary"
          variant="outlined"
          disabled={disabled}
        >
          Apply Settings
        </Button>
      </DialogActions>
    </Dialog>
  );
}

$(document).on("click", ".snapGenePanel .previewActionLink", function (e) {
  e.preventDefault();
  let target_id = $(e.target).parent().parent()[0].getAttribute("bioid");

  renderDialog(target_id);
  RS.hideHelpButton();
});

$(document).on("click", ".imageThumbnail", function (e) {
  if (window.localStorage.getItem("snapgene-available") === "true") {
    let target = $(e.target).parent();

    if (RS.dnaFiles.includes(target.data("extension"))) {
      renderDialog(target.data("id"));
    }
  }
});

// detect iframe load and render elements
document.addEventListener("open-dna-info", function (e) {
  e.preventDefault();
  renderDialog(e.detail);
});

function renderDialog(target_id) {
  $(document.body).append("<span class='snapgene-dialog'></span>");
  let container = $(".snapgene-dialog")[0];
  const root = createRoot(container);
  root.render(<SnapGeneDialog id={target_id} />);
}
