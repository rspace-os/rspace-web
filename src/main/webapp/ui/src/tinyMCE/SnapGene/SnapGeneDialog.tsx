import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import React from "react";
import { useTranslation } from "react-i18next";
import DnaPreview from "./DnaPreview";
import EnzymeTable from "./EnzymeTable";
import FastaView from "./FastaView";
import OrfTable from "./OrfTable";

declare const $: JQueryStatic;

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    "aria-controls": `simple-tabpanel-${index}`,
  };
}

export default function SnapGeneDialog({ id }: { id: string | number }) {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = React.useState(true);
  const [tab, setTab] = React.useState(0);
  const [disabled, setDisabled] = React.useState(false);
  const [clicked, setClicked] = React.useState<Record<number, number>>({
    0: 0,
    1: 0,
    2: 0,
    3: 0,
  });

  const handleClose = () => {
    setOpen(false);
    $(".snapgene-dialog").remove(); // clean up after dialog is closed
  };

  const switchTab = (_e: React.SyntheticEvent, value: number) => {
    setTab(value);
  };

  const handleApply = () => {
    setClicked({
      ...clicked,
      [tab]: clicked[tab] + 1,
    });
  };

  return (
    <Dialog open={open} onClose={handleClose} aria-labelledby="form-dialog-title" fullWidth={true} maxWidth="xl">
      <DialogTitle id="form-dialog-title">{t("tinymce.snapGene.dialogTitle")}</DialogTitle>
      <DialogContent>
        <Grid container spacing={2}>
          <Grid size={2}>
            <Tabs orientation="vertical" variant="scrollable" value={tab} onChange={switchTab}>
              <Tab label={t("tinymce.snapGene.tabDnaPreview")} {...a11yProps(0)} />
              <Tab label={t("tinymce.snapGene.enzymeSites")} {...a11yProps(1)} />
              <Tab label={t("tinymce.snapGene.viewAsFasta")} {...a11yProps(2)} />
              <Tab label={t("tinymce.snapGene.orfTable")} {...a11yProps(3)} />
            </Tabs>
          </Grid>
          {tab === 0 && <DnaPreview id={id} clicked={clicked["0"]} setDisabled={(d) => setDisabled(d)} />}
          {tab === 1 && <EnzymeTable id={id} clicked={clicked["1"]} setDisabled={(d: boolean) => setDisabled(d)} />}
          {tab === 2 && <FastaView id={id} clicked={clicked["2"]} setDisabled={(d) => setDisabled(d)} />}
          {tab === 3 && <OrfTable id={id} clicked={clicked["3"]} setDisabled={(d: boolean) => setDisabled(d)} />}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          {t("common:actions.close")}
        </Button>
        <Button onClick={handleApply} color="primary" variant="outlined" disabled={disabled}>
          {t("tinymce.snapGene.applySettings")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
