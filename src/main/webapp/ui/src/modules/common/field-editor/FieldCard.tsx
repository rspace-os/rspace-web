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

/** Below this card width, in pixels, the actions and type go on a row below the name. */
const NARROW_CARD = 480;

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
  /** More switches for the field, shown on one wrapping row with Mandatory. */
  switches?: ReactNode;
  /** The field's position in the list, from 0, and the number of fields. */
  position: number;
  count: number;
  onMove: (newPosition: number) => void;
  canDelete: boolean;
  onDelete: () => void;
  /** Replaces the plain delete button, for hosts that ask how to delete. */
  deleteControl?: ReactNode;
  editable: boolean;
  /**
   * When the field is shown, usually a `FieldDisplayCondition`, under the name.
   * It stays in one place in both modes, so it keeps its state while the card
   * is briefly read-only, for example during a save.
   */
  displayCondition?: ReactNode;
  /** Settings that only some field types or products have. */
  children?: ReactNode;
  /** Give the type menu a row of its own, for narrow layouts. */
  columnLayout?: boolean;
};

/**
 * The parts of a field editor that every product shares: name, type,
 * mandatory, move and delete. Products add their own settings as children.
 * Move and delete are compact icon buttons beside the name, and the switches
 * share one row, so a simple field takes little height.
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
  switches,
  position,
  count,
  onMove,
  canDelete,
  onDelete,
  deleteControl,
  editable,
  displayCondition,
  children,
  columnLayout = false,
}: FieldCardProps<T>): ReactNode {
  const { t } = useTranslation("common");
  const nameId = useId();
  const typeLabel = typeOptions.find((option) => option.value === type)?.label ?? type;
  const displayName = name === "" ? t("fieldEditor.card.unnamed") : name;

  return (
    <Paper
      variant="outlined"
      role="region"
      aria-labelledby={`${nameId}-region`}
      // the header lays itself out by the card's width, which in a side panel is much narrower than the window
      sx={{ px: 2, py: 1.5, containerType: "inline-size" }}
    >
      {/* the name input's value cannot label the region, so a hidden copy of the name does */}
      {editable && (
        <span id={`${nameId}-region`} hidden>
          {displayName}
        </span>
      )}
      <Stack spacing={1.5}>
        {/* on a narrow card the actions go below the name, on one row with the type */}
        <Stack
          direction="row"
          spacing={1.5}
          useFlexGap
          sx={(theme) => ({
            justifyContent: "space-between",
            alignItems: "flex-start",
            [theme.containerQueries.down(NARROW_CARD)]: { flexDirection: "column", alignItems: "stretch" },
          })}
        >
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
              sx={{ flexGrow: 1, minWidth: 0 }}
            />
          ) : (
            <Typography variant="h6" component="h4" id={`${nameId}-region`} sx={{ overflowWrap: "anywhere" }}>
              {displayName}
            </Typography>
          )}
          <Stack
            spacing={0.5}
            useFlexGap
            sx={(theme) => ({
              alignItems: "flex-end",
              [theme.containerQueries.down(NARROW_CARD)]: {
                flexDirection: "row-reverse",
                flexWrap: "wrap",
                justifyContent: "space-between",
                alignItems: "center",
              },
            })}
          >
            {editable && (
              <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                <MoveButtons position={position} count={count} onMove={onMove} fieldName={displayName} />
                {canDelete &&
                  (deleteControl ?? (
                    <IconButton
                      onClick={onDelete}
                      aria-label={t("fieldEditor.card.delete", { field: displayName })}
                      title={t("fieldEditor.card.delete", { field: displayName })}
                    >
                      <DeleteOutlinedIcon />
                    </IconButton>
                  ))}
              </Stack>
            )}
            {!(editable && canChangeType) && (
              <Typography variant="subtitle1" component="div" sx={{ whiteSpace: "nowrap" }}>
                {typeLabel}
              </Typography>
            )}
          </Stack>
        </Stack>
        {displayCondition}
        <Stack direction="row" useFlexGap sx={{ flexWrap: "wrap", columnGap: 2, rowGap: 1, alignItems: "flex-start" }}>
          <Box>
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
          {switches}
          {editable && canChangeType && (
            <Box sx={{ flex: columnLayout ? "1 1 100%" : "1 1 240px" }}>
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
      </Stack>
    </Paper>
  );
}
