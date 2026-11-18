import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { usableOptions } from "@/modules/common/field-editor/ruleEditing";
import {
  DATE_FORMATS,
  type EditorField,
  type FieldOfType,
  type FieldProblems,
  type FieldSettings,
  TIME_FORMATS,
} from "./editorModel";
import TextDefaultEditor from "./TextDefaultEditor";
import { useProblemMessage } from "./useProblemMessage";

type SettingsProps<T extends EditorField["type"]> = {
  field: FieldOfType<T>;
  /** Applies new settings of the same type. */
  onChange: (settings: Partial<Extract<FieldSettings, { type: T }>>) => void;
  problems: FieldProblems;
  editable: boolean;
};

function Toggle({
  label,
  checked,
  onChange,
  editable,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  editable: boolean;
}): ReactNode {
  return (
    <FormControlLabel
      control={<Switch checked={checked} disabled={!editable} onChange={({ target }) => onChange(target.checked)} />}
      label={label}
    />
  );
}

function NumberSettings({ field, onChange, problems, editable }: SettingsProps<"Number">): ReactNode {
  const { t } = useTranslation("workspace");
  const message = useProblemMessage();
  const input = (name: "decimalPlaces" | "min" | "max" | "defaultValue", label: string) => (
    <TextField
      size="small"
      label={label}
      value={field[name]}
      disabled={!editable}
      onChange={({ target }) => onChange({ [name]: target.value })}
      error={Boolean(problems[name])}
      helperText={message(problems[name])}
      slotProps={{ htmlInput: { inputMode: name === "decimalPlaces" ? "numeric" : "decimal" } }}
    />
  );
  return (
    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} useFlexGap sx={{ flexWrap: "wrap" }}>
      {input("decimalPlaces", t("formFieldEditor.settings.decimalPlaces"))}
      {input("min", t("formFieldEditor.settings.minimum"))}
      {input("max", t("formFieldEditor.settings.maximum"))}
      {input("defaultValue", t("formFieldEditor.settings.defaultValue"))}
    </Stack>
  );
}

function StringSettings({ field, onChange, editable }: SettingsProps<"String">): ReactNode {
  const { t } = useTranslation("workspace");
  return (
    <Stack spacing={1.5}>
      <Toggle
        label={t("formFieldEditor.settings.password")}
        checked={field.password}
        onChange={(password) => onChange({ password })}
        editable={editable}
      />
      <TextField
        size="small"
        label={t("formFieldEditor.settings.defaultValue")}
        value={field.defaultValue}
        disabled={!editable}
        onChange={({ target }) => onChange({ defaultValue: target.value })}
      />
    </Stack>
  );
}

function RadioSettings({ field, onChange, editable }: SettingsProps<"Radio">): ReactNode {
  const { t } = useTranslation("workspace");
  const options = usableOptions(field.options);
  return (
    <Stack spacing={1.5}>
      <Stack direction={{ xs: "column", md: "row" }} spacing={1.5}>
        <Toggle
          label={t("formFieldEditor.settings.showAsPickList")}
          checked={field.showAsPickList}
          onChange={(showAsPickList) => onChange({ showAsPickList })}
          editable={editable}
        />
        <Toggle
          label={t("formFieldEditor.settings.sortAlphabetic")}
          checked={field.sortAlphabetic}
          onChange={(sortAlphabetic) => onChange({ sortAlphabetic })}
          editable={editable}
        />
      </Stack>
      <TextField
        select
        size="small"
        label={t("formFieldEditor.settings.defaultOption")}
        value={options.includes(field.defaultValue) ? field.defaultValue : ""}
        disabled={!editable}
        onChange={({ target }) => onChange({ defaultValue: target.value })}
        sx={{ maxWidth: 360 }}
      >
        <MenuItem value="">
          <em>{t("formFieldEditor.settings.noDefault")}</em>
        </MenuItem>
        {options.map((option) => (
          <MenuItem key={option} value={option}>
            {option}
          </MenuItem>
        ))}
      </TextField>
    </Stack>
  );
}

function ChoiceSettings({ field, onChange, editable }: SettingsProps<"Choice">): ReactNode {
  const { t } = useTranslation("workspace");
  const options = usableOptions(field.options);
  return (
    <Stack spacing={1.5}>
      <Toggle
        label={t("formFieldEditor.settings.multiple")}
        checked={field.multiple}
        onChange={(multiple) => onChange({ multiple })}
        editable={editable}
      />
      {options.length > 0 && (
        <FormControl component="fieldset" variant="standard">
          <FormLabel component="legend">{t("formFieldEditor.settings.defaultOptions")}</FormLabel>
          <FormGroup row>
            {options.map((option) => (
              <FormControlLabel
                key={option}
                label={option}
                control={
                  <Checkbox
                    checked={field.defaults.includes(option)}
                    disabled={!editable}
                    onChange={({ target }) =>
                      onChange({
                        defaults: target.checked
                          ? options.filter((o) => o === option || field.defaults.includes(o))
                          : field.defaults.filter((o) => o !== option),
                      })
                    }
                  />
                }
              />
            ))}
          </FormGroup>
        </FormControl>
      )}
    </Stack>
  );
}

function BoundedSettings({
  field,
  onChange,
  problems,
  editable,
}: SettingsProps<"Date"> | SettingsProps<"Time">): ReactNode {
  const { t } = useTranslation("workspace");
  const message = useProblemMessage();
  const isDate = field.type === "Date";
  const formats: ReadonlyArray<{ value: string; label: string }> = isDate
    ? DATE_FORMATS.map((value) => ({ value, label: value }))
    : TIME_FORMATS.map((value) => ({
        value,
        label: value === "HH:mm" ? t("formFieldEditor.settings.time24Hour") : t("formFieldEditor.settings.time12Hour"),
      }));
  const input = (name: "min" | "max" | "defaultValue", label: string) => (
    <TextField
      size="small"
      type={isDate ? "date" : "time"}
      label={label}
      value={field[name]}
      disabled={!editable}
      onChange={({ target }) => onChange({ [name]: target.value })}
      error={Boolean(problems[name])}
      helperText={message(problems[name])}
      slotProps={{ inputLabel: { shrink: true } }}
    />
  );
  return (
    <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} useFlexGap sx={{ flexWrap: "wrap" }}>
      <TextField
        select
        size="small"
        label={isDate ? t("formFieldEditor.settings.dateFormat") : t("formFieldEditor.settings.timeFormat")}
        value={field.format}
        disabled={!editable}
        onChange={({ target }) => onChange({ format: target.value })}
        sx={{ minWidth: 180 }}
      >
        {formats.map(({ value, label }) => (
          <MenuItem key={value} value={value}>
            {label}
          </MenuItem>
        ))}
      </TextField>
      {input("min", t("formFieldEditor.settings.earliest"))}
      {input("max", t("formFieldEditor.settings.latest"))}
      {input("defaultValue", t("formFieldEditor.settings.defaultValue"))}
    </Stack>
  );
}

type ElnFieldSettingsProps = {
  field: EditorField;
  onChange: (settings: Partial<FieldSettings>) => void;
  problems: FieldProblems;
  editable: boolean;
};

/** The settings only ELN fields have, by type; options are edited by the shared options editor. */
export default function ElnFieldSettings({ field, onChange, problems, editable }: ElnFieldSettingsProps): ReactNode {
  const { t } = useTranslation("workspace");
  switch (field.type) {
    case "Number":
      return <NumberSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "String":
      return <StringSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "Text":
      return (
        <TextDefaultEditor
          fieldName={field.name}
          value={field.defaultValue}
          onChange={(defaultValue) => onChange({ defaultValue })}
          editable={editable}
        />
      );
    case "Radio":
      return <RadioSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "Choice":
      return <ChoiceSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "Date":
      return <BoundedSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "Time":
      return <BoundedSettings field={field} onChange={onChange} problems={problems} editable={editable} />;
    case "Uri":
    case "Reference":
    case "Attachment":
      return (
        <Typography variant="body2" color="text.secondary">
          {t("formFieldEditor.settings.apiOnlyType")}
        </Typography>
      );
  }
}
