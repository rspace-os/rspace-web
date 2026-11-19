import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import DOMPurify from "dompurify";
import { type ReactNode, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import StyledTinyMceEditor from "@/components/Inputs/StyledTinyMceEditor";

type TextDefaultEditorProps = {
  fieldName: string;
  value: string;
  onChange: (value: string) => void;
  editable: boolean;
};

/**
 * The default content of a text field: a preview, and a dialog with the same
 * formatting toolbar as the legacy field editor's TinyMCE. One editor at a
 * time keeps a form with many text fields light.
 */
export default function TextDefaultEditor({ fieldName, value, onChange, editable }: TextDefaultEditorProps): ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const id = useId();
  const [draft, setDraft] = useState<string | null>(null);
  const clean = DOMPurify.sanitize(value);

  return (
    <Box>
      <Typography variant="subtitle2" component="div" id={`${id}-label`}>
        {t("formFieldEditor.settings.defaultText")}
      </Typography>
      {clean === "" ? (
        <Typography variant="body2" color="text.secondary">
          {t("formFieldEditor.settings.noDefaultText")}
        </Typography>
      ) : (
        <Box
          aria-labelledby={`${id}-label`}
          role="group"
          // it scrolls, so keyboard users must be able to reach it
          tabIndex={0}
          sx={{ border: 1, borderColor: "divider", borderRadius: 1, px: 1.5, maxHeight: 200, overflow: "auto" }}
          // sanitised above
          dangerouslySetInnerHTML={{ __html: clean }}
        />
      )}
      {editable && (
        <Button variant="outlined" sx={{ mt: 1 }} onClick={() => setDraft(value)}>
          {t("formFieldEditor.settings.editDefaultText", { field: fieldName })}
        </Button>
      )}
      <Dialog
        open={draft !== null}
        onClose={() => setDraft(null)}
        maxWidth="md"
        fullWidth
        aria-labelledby={`${id}-title`}
      >
        <DialogTitle id={`${id}-title`}>
          {t("formFieldEditor.settings.defaultTextOf", { field: fieldName })}
        </DialogTitle>
        <DialogContent>
          {draft !== null && (
            <StyledTinyMceEditor
              initialValue={value}
              onEditorChange={(content) => setDraft(content)}
              init={{
                menubar: false,
                plugins: ["autolink", "lists", "link", "charmap", "autoresize"],
                toolbar: "bold italic underline strikethrough | alignleft aligncenter alignright alignjustify",
                iframe_aria_text: t("formFieldEditor.settings.defaultTextOf", {
                  field: fieldName,
                }),
              }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDraft(null)}>{t("common:actions.cancel")}</Button>
          <Button
            variant="contained"
            color="callToAction"
            onClick={() => {
              if (draft !== null) onChange(DOMPurify.sanitize(draft));
              setDraft(null);
            }}
          >
            {t("common:actions.done")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
