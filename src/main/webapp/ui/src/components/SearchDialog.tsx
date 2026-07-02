import SettingsOverscanIcon from "@mui/icons-material/SettingsOverscan";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Grow from "@mui/material/Grow";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import docLinks from "../assets/DocLinks";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import SubmitSpinnerButton from "./SubmitSpinnerButton";

type TextAreaDialogArgs = {
  query: string;
  onSubmit: () => void;
  setQuery: (event: { target: { value: string } }) => void;
  visible: boolean;
};

export default function TextAreaDialog({ onSubmit, setQuery, query, visible }: TextAreaDialogArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const [dialogOpen, setDialogOpen] = useState(false);
  const onClose = () => setDialogOpen(false);
  return visible ? (
    <>
      <Grow in={visible}>
        <IconButtonWithTooltip
          title={t("searchDialog.expandField")}
          icon={<SettingsOverscanIcon />}
          onClick={() => {
            setDialogOpen(true);
          }}
          size="small"
        />
      </Grow>
      <Dialog open={dialogOpen} onClose={onClose}>
        <DialogTitle>{t("searchDialog.query")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <TextField
              onChange={setQuery}
              onKeyPress={(e) => {
                /*
                 * Prevent the enter key because whilst we are giving more
                 * vertical space for the text to wrap, the search query must
                 * still be a single line of text.
                 */
                if (e.key === "Enter") e.preventDefault();
              }}
              value={query}
              multiline
              fullWidth
              rows={6}
            />
            <Box>
              <DialogContentText>
                <TransRichText ns="common" i18nKey="searchDialog.luceneTip" />
              </DialogContentText>
              <DialogContentText>
                <TransRichText
                  ns="common"
                  i18nKey="searchDialog.moreInfo"
                  components={{
                    luceneLink: <Link href={docLinks.luceneSyntax} rel="noreferrer" target="_blank" />,
                  }}
                />
              </DialogContentText>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuery({ target: { value: "" } })}>{t("actions.clear")}</Button>
          <Button onClick={onClose}>{t("actions.close")}</Button>
          <SubmitSpinnerButton
            loading={false}
            disabled={false}
            onClick={() => {
              onSubmit();
              onClose();
            }}
            label={t("actions.search")}
          />
        </DialogActions>
      </Dialog>
    </>
  ) : null;
}
