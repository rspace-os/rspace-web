import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import DnaPreview from "./DnaPreview";
import EnzymeTable from "./EnzymeTable";
import FastaView from "./FastaView";
import OrfTable from "./OrfTable";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const RS: any;

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    "aria-controls": `simple-tabpanel-${index}`,
  };
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function SnapGeneDialog(props: any) {
  const { t } = useTranslation(["apps", "common"]);
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
      <DialogTitle id="form-dialog-title">{"SnapGene"}</DialogTitle>
      <DialogContent>
        <Grid container spacing={2}>
          <Grid size={2}>
            <Tabs orientation="vertical" variant="scrollable" value={tab} onChange={switchTab}>
              <Tab label={t("tinyMce.snapGene.tabDnaPreview")} {...a11yProps(0)} />
              <Tab label={t("tinyMce.snapGene.enzymeSites")} {...a11yProps(1)} />
              <Tab label={t("tinyMce.snapGene.viewAsFasta")} {...a11yProps(2)} />
              <Tab label={t("tinyMce.snapGene.orfTable")} {...a11yProps(3)} />
            </Tabs>
          </Grid>
          {tab === 0 && <DnaPreview id={props.id} clicked={clicked["0"]} setDisabled={(d) => setDisabled(d)} />}
          {tab === 1 && (
            <EnzymeTable id={props.id} clicked={clicked["1"]} setDisabled={(d: boolean) => setDisabled(d)} />
          )}
          {tab === 2 && <FastaView id={props.id} clicked={clicked["2"]} setDisabled={(d) => setDisabled(d)} />}
          {tab === 3 && <OrfTable id={props.id} clicked={clicked["3"]} setDisabled={(d: boolean) => setDisabled(d)} />}
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          {t("common:actions.close")}
        </Button>
        <Button onClick={handleApply} color="primary" variant="outlined" disabled={disabled}>
          {t("tinyMce.snapGene.applySettings")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
$(document).on("click", ".snapGenePanel .previewActionLink", (e: any) => {
  e.preventDefault();
  const target_id = $(e.target).parent().parent()[0].getAttribute("bioid");

  renderDialog(target_id);
});

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
$(document).on("click", ".imageThumbnail", (e: any) => {
  if (window.localStorage.getItem("snapgene-available") === "true") {
    const target = $(e.target).parent();

    if (RS.dnaFiles.includes(target.data("extension"))) {
      renderDialog(target.data("id"));
    }
  }
});

// detect iframe load and render elements
document.addEventListener("open-dna-info", (e) => {
  e.preventDefault();
  renderDialog((e as CustomEvent).detail);
});

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function renderDialog(target_id: any) {
  $(document.body).append("<span class='snapgene-dialog'></span>");
  const container = $(".snapgene-dialog")[0];
  const root = createRoot(container);
  root.render(
    <I18nRoot namespaces={["apps", "common"]}>
      <MuiCssLayerProvider>
        <SnapGeneDialog id={target_id} />
      </MuiCssLayerProvider>
    </I18nRoot>,
  );
}
