import ChecklistIcon from "@mui/icons-material/Checklist";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import PrintIcon from "@mui/icons-material/Print";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Menu from "@mui/material/Menu";
import Stack from "@mui/material/Stack";
import { darken, lighten, useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import TransRichText from "@/modules/common/i18n/TransRichText";
import docLinks from "../../../assets/DocLinks";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { useLandmark } from "../../../components/LandmarksContext";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import TitledBox from "../../../components/TitledBox";
import RsSet from "../../../util/set";
import Main from "../../Main";
import { type Identifier, useIdentifiers, useIdentifiersRefresh } from "../../useIdentifiers";
import IgsnTable from "./IgsnTable";
import PrintDialog from "./PrintDialog";

/**
 * The IGSN Management page allows users to view, bulk register, print, and
 * otherwise manage IGSN IDs.
 */
export default function IgsnManagementPage({
  selectedIgsns,
  setSelectedIgsns,
}: {
  selectedIgsns: RsSet<Identifier>;
  setSelectedIgsns: (newSelectedIgsns: RsSet<Identifier>) => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { refreshListing } = useIdentifiersRefresh();
  const { bulkRegister, deleteIdentifiers } = useIdentifiers();
  const mainContentRef = useLandmark(t("igsnManagement.landmark"));
  const [bulkRegisterDialogOpen, setBulkRegisterDialogOpen] = React.useState(false);
  const [numberOfNewIdentifiers, setNumberOfNewIdentifiers] = React.useState(1);
  const [registeringInProgress, setRegisteringInProgress] = React.useState(false);
  const [actionsAnchorEl, setActionsAnchorEl] = React.useState<HTMLElement | null>(null);
  const theme = useTheme();
  const [printDialogOpen, setPrintDialogOpen] = React.useState(false);
  return (
    <Main
      sx={{
        overflowY: "auto",
        p: 2,
      }}
      ref={mainContentRef}
      role="main"
      aria-label={t("igsnManagement.landmark")}
    >
      <VisuallyHiddenHeading variant="h2">{t("igsnManagement.pageTitle")}</VisuallyHiddenHeading>
      <HeadingContext level={3}>
        <Stack spacing={2}>
          <TitledBox title={t("igsnManagement.sections.ids")} border>
            <Typography>
              <TransRichText
                i18nKey="inventory:igsnManagement.idsDescription"
                components={{
                  a: <Link target="_blank" rel="noreferrer" href={docLinks.igsnIdentifiers} />,
                }}
              />
            </Typography>
          </TitledBox>
          <TitledBox title={t("igsnManagement.sections.register")} border>
            <Stack
              spacing={2}
              sx={{
                alignItems: "flex-start",
              }}
            >
              <Typography>
                <TransRichText
                  i18nKey="inventory:igsnManagement.registerDescription"
                  components={{
                    cite: <cite />,
                  }}
                />
              </Typography>
              <Typography>{t("igsnManagement.bulkRegister.summary")}</Typography>
              <Button
                variant="contained"
                color="primary"
                disableElevation
                onClick={() => setBulkRegisterDialogOpen(true)}
              >
                {t("igsnManagement.bulkRegister.button")}
              </Button>
              <Dialog open={bulkRegisterDialogOpen} onClose={() => setBulkRegisterDialogOpen(false)}>
                <DialogTitle>{t("igsnManagement.bulkRegister.dialogTitle")}</DialogTitle>
                <DialogContent>
                  <Stack spacing={3}>
                    <Typography>{t("igsnManagement.bulkRegister.createdMessage")}</Typography>
                    <TextField
                      label={t("igsnManagement.bulkRegister.numberLabel")}
                      type="number"
                      value={numberOfNewIdentifiers}
                      onChange={(e) => setNumberOfNewIdentifiers(Number(e.target.value))}
                      fullWidth
                      sx={{
                        mt: 1,
                      }}
                      error={numberOfNewIdentifiers < 1 || numberOfNewIdentifiers > 100}
                      slotProps={{
                        htmlInput: {
                          min: 1,
                          max: 100,
                        },
                      }}
                    />
                  </Stack>
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setBulkRegisterDialogOpen(false)}>{t("common:actions.cancel")}</Button>
                  <SubmitSpinnerButton
                    onClick={() => {
                      void (async () => {
                        setRegisteringInProgress(true);
                        try {
                          await bulkRegister({
                            count: numberOfNewIdentifiers,
                          });
                          if (refreshListing) void refreshListing();
                          setBulkRegisterDialogOpen(false);
                        } finally {
                          setRegisteringInProgress(false);
                        }
                      })();
                    }}
                    disabled={registeringInProgress}
                    loading={registeringInProgress}
                    label={t("igsnManagement.bulkRegister.register")}
                  />
                </DialogActions>
              </Dialog>
            </Stack>
          </TitledBox>
          <TitledBox title={t("igsnManagement.sections.manage")} border>
            <Stack
              spacing={0.5}
              sx={{
                alignItems: "flex-start",
              }}
            >
              <Typography>
                <TransRichText
                  i18nKey="inventory:igsnManagement.manageDescription"
                  components={{
                    cite: <cite />,
                    strong: <strong />,
                  }}
                />
              </Typography>
              <Box
                sx={{
                  height: 12,
                }}
              ></Box>
              <Stack direction="row">
                <Button
                  variant="contained"
                  color="callToAction"
                  size="small"
                  disableElevation
                  startIcon={<ChecklistIcon />}
                  aria-label={t("igsnManagement.actions.label")}
                  aria-haspopup="menu"
                  aria-expanded={false}
                  id="actions-menu"
                  disabled={selectedIgsns.size === 0}
                  onClick={(event) => {
                    setActionsAnchorEl(event.currentTarget);
                  }}
                >
                  {t("igsnManagement.actions.button")}
                </Button>
                <Menu
                  anchorEl={actionsAnchorEl}
                  open={Boolean(actionsAnchorEl)}
                  onClose={() => setActionsAnchorEl(null)}
                  slotProps={{
                    list: {
                      "aria-labelledby": "actions-menu",
                      disablePadding: true,
                    },
                  }}
                >
                  <AccentMenuItem
                    title={t("igsnManagement.actions.print.title")}
                    subheader={t("igsnManagement.actions.print.subheader")}
                    onClick={() => {
                      setPrintDialogOpen(true);
                    }}
                    avatar={<PrintIcon />}
                    compact
                  />
                  <PrintDialog
                    showPrintDialog={printDialogOpen}
                    onClose={() => {
                      setPrintDialogOpen(false);
                      setActionsAnchorEl(null);
                    }}
                    itemsToPrint={[...selectedIgsns]}
                  />
                  <AccentMenuItem
                    title={t("igsnManagement.actions.delete.title")}
                    subheader={t("igsnManagement.actions.delete.subheader")}
                    onClick={() => {
                      void deleteIdentifiers(selectedIgsns).then(() => {
                        if (refreshListing) void refreshListing();
                        setSelectedIgsns(new RsSet([]));
                      });
                      setActionsAnchorEl(null);
                    }}
                    backgroundColor={lighten(theme.palette.error.light, 0.5)}
                    foregroundColor={darken(theme.palette.error.dark, 0.3)}
                    avatar={<DeleteOutlineOutlinedIcon />}
                    compact
                  />
                </Menu>
              </Stack>
              <Box sx={{ width: "100%" }}>
                <IgsnTable selectedIgsns={selectedIgsns} setSelectedIgsns={setSelectedIgsns} />
              </Box>
            </Stack>
          </TitledBox>
        </Stack>
      </HeadingContext>
    </Main>
  );
}
