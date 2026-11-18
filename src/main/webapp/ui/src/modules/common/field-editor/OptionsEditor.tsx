import AddIcon from "@mui/icons-material/Add";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { type ReactNode, useId } from "react";
import { useTranslation } from "react-i18next";
import { isBlankOption } from "./ruleEditing";

export type EditableOption = {
  readonly value: string;
  /** False for an option that can only be removed, such as a saved option or one a display rule uses. */
  readonly editable: boolean;
  /** Why the option cannot be edited, shown under it. */
  readonly lockedReason?: string;
};

type OptionsEditorProps = {
  options: ReadonlyArray<EditableOption>;
  onOptionChange: (index: number, value: string) => void;
  onOptionRemove: (index: number) => void;
  onOptionAdd: () => void;
  disabled?: boolean;
};

/** Lists the options of a radio or choice field, flagging blank and repeated ones. */
export default function OptionsEditor({
  options,
  onOptionChange,
  onOptionRemove,
  onOptionAdd,
  disabled = false,
}: OptionsEditorProps): ReactNode {
  const { t } = useTranslation("common");
  const id = useId();
  const errorFor = (value: string, index: number): string | undefined => {
    if (isBlankOption(value)) return t("fieldEditor.options.blank");
    if (options.findIndex((option) => option.value === value) !== index) return t("fieldEditor.options.duplicate");
    return undefined;
  };

  return (
    <Box role="group" aria-labelledby={`${id}-label`}>
      <Typography variant="subtitle2" component="div" id={`${id}-label`}>
        {t("fieldEditor.options.title")}
      </Typography>
      {options.length === 0 && (
        <Typography variant="body2" color="error">
          {t("fieldEditor.options.required")}
        </Typography>
      )}
      <Stack spacing={1} sx={{ mt: 1 }}>
        {options.map((option, index) => {
          const label = t("fieldEditor.options.optionLabel", { position: index + 1 });
          const error = errorFor(option.value, index);
          return (
            <Stack key={index} direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
              <TextField
                size="small"
                id={`${id}-${index}`}
                label={label}
                value={option.value}
                disabled={disabled}
                slotProps={{ htmlInput: { readOnly: !option.editable } }}
                onChange={({ target: { value } }) => onOptionChange(index, value)}
                error={Boolean(error)}
                helperText={error ?? (option.editable ? undefined : option.lockedReason)}
                sx={{ flexGrow: 1 }}
              />
              {!disabled && (
                <IconButton
                  onClick={() => onOptionRemove(index)}
                  aria-label={t("fieldEditor.options.remove", { option: option.value || label })}
                  title={t("fieldEditor.options.remove", { option: option.value || label })}
                >
                  <DeleteOutlinedIcon />
                </IconButton>
              )}
            </Stack>
          );
        })}
      </Stack>
      {!disabled && (
        <Button variant="outlined" startIcon={<AddIcon />} onClick={onOptionAdd} sx={{ mt: 1 }}>
          {t("fieldEditor.options.add")}
        </Button>
      )}
    </Box>
  );
}
