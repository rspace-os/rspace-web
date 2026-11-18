import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { type ReactNode, useId } from "react";
import { useTranslation } from "react-i18next";
import FieldTypeSelect, { type FieldTypeOption } from "./FieldTypeSelect";
import MoveButtons from "./MoveButtons";

type FieldCardProps<T extends string> = {
  name: string;
  onNameChange: (name: string) => void;
  /** Shown under the name; the host decides what makes a name invalid. */
  nameError?: string | null;
  /** Shows a character count against this limit under the name. */
  nameMaxLength?: number;
  type: T;
  typeOptions: ReadonlyArray<FieldTypeOption<T>>;
  onTypeChange: (type: T) => void;
  /** False once the type is fixed, for example after the field is saved. */
  canChangeType: boolean;
  mandatory: boolean;
  onMandatoryChange: (mandatory: boolean) => void;
  /** The field's position in the list, from 0, and the number of fields. */
  position: number;
  count: number;
  onMove: (newPosition: number) => void;
  canDelete: boolean;
  onDelete: () => void;
  /** Replaces the plain delete button, for hosts that ask how to delete. */
  deleteControl?: ReactNode;
  editable: boolean;
  /** The "Shown when..." summary, when the field has a display rule. */
  summary?: ReactNode;
  /** Settings that only some field types or products have. */
  children?: ReactNode;
  /** Stack the settings in a column, for narrow layouts. */
  columnLayout?: boolean;
};

/**
 * The parts of a field editor that every product shares: name, type,
 * mandatory, move and delete. Products add their own settings as children.
 */
export default function FieldCard<T extends string>({
  name,
  onNameChange,
  nameError = null,
  nameMaxLength,
  type,
  typeOptions,
  onTypeChange,
  canChangeType,
  mandatory,
  onMandatoryChange,
  position,
  count,
  onMove,
  canDelete,
  onDelete,
  deleteControl,
  editable,
  summary,
  children,
  columnLayout = false,
}: FieldCardProps<T>): ReactNode {
  const { t } = useTranslation("common");
  const nameId = useId();
  const typeLabel = typeOptions.find((option) => option.value === type)?.label ?? type;
  const displayName = name === "" ? t("fieldEditor.card.unnamed") : name;

  return (
    <Paper variant="outlined" role="region" aria-labelledby={`${nameId}-region`} sx={{ p: 2 }}>
      {/* the name input's value cannot label the region, so a hidden copy of the name does */}
      {editable && (
        <span id={`${nameId}-region`} hidden>
          {displayName}
        </span>
      )}
      <Stack spacing={2}>
        <Stack direction="row" spacing={1.5} sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          {editable ? (
            <TextField
              variant="standard"
              id={nameId}
              name="name"
              label={t("fieldEditor.card.name")}
              value={name}
              onChange={({ target: { value } }) => onNameChange(value)}
              error={Boolean(nameError)}
              helperText={
                nameError ??
                (nameMaxLength === undefined
                  ? undefined
                  : t("fieldEditor.card.nameLength", { length: name.length, max: nameMaxLength }))
              }
              sx={{ flexGrow: 1 }}
            />
          ) : (
            <Typography variant="h6" component="h4" id={`${nameId}-region`} sx={{ overflowWrap: "anywhere" }}>
              {displayName}
            </Typography>
          )}
          <Stack sx={{ alignItems: "flex-end" }} spacing={0.75}>
            {editable &&
              canDelete &&
              (deleteControl ?? (
                <IconButton
                  onClick={onDelete}
                  aria-label={t("fieldEditor.card.delete", { field: displayName })}
                  title={t("fieldEditor.card.delete", { field: displayName })}
                >
                  <DeleteOutlinedIcon />
                </IconButton>
              ))}
            {!(editable && canChangeType) && (
              <Typography variant="subtitle1" component="div" sx={{ whiteSpace: "nowrap" }}>
                {typeLabel}
              </Typography>
            )}
          </Stack>
        </Stack>
        {summary}
        <Stack
          direction={columnLayout ? "column" : { xs: "column", lg: "row" }}
          spacing={2}
          sx={{ alignItems: columnLayout ? "stretch" : { xs: "stretch", lg: "flex-start" } }}
        >
          <Box sx={{ minWidth: 160 }}>
            {editable ? (
              <FormControlLabel
                control={
                  <Switch checked={mandatory} onChange={({ target: { checked } }) => onMandatoryChange(checked)} />
                }
                label={t("fieldEditor.card.mandatory")}
              />
            ) : (
              <Typography variant="body2">
                {mandatory ? t("fieldEditor.card.mandatoryYes") : t("fieldEditor.card.mandatoryNo")}
              </Typography>
            )}
          </Box>
          {editable && canChangeType && (
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="subtitle2" component="div" sx={{ mb: 0.5 }}>
                {t("fieldEditor.card.type")}
              </Typography>
              <FieldTypeSelect
                value={type}
                options={typeOptions}
                onChange={onTypeChange}
                label={t("fieldEditor.card.changeType", { type: typeLabel })}
              />
            </Box>
          )}
        </Stack>
        {children}
        {editable && <MoveButtons position={position} count={count} onMove={onMove} fieldName={displayName} />}
      </Stack>
    </Paper>
  );
}
