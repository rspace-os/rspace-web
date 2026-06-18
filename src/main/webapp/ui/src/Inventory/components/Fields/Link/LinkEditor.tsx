import HistoryIcon from "@mui/icons-material/History";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import FormHelperText from "@mui/material/FormHelperText";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import ElnRecordPicker from "./ElnRecordPicker";
import { iconForGlobalId } from "./iconForGlobalId";
import LinkTargetBrowser from "./LinkTargetBrowser";
import VersionLockDialog from "./VersionLockDialog";

export interface LinkEditorProps {
  /** Current relation type (controlled). */
  relationType: string;
  onRelationTypeChange: (value: string) => void;
  /** Allowed relation types: a constrained list or the full DataCite set. */
  relationOptions: ReadonlyArray<string>;
  /**
   * When true the relation field accepts free text (extra-field links allow any
   * DataCite type); when false it is a constrained pick from `relationOptions`
   * (template-field links restrict to the template's allowed set).
   */
  relationFreeSolo: boolean;
  relationLabel: string;
  relationError?: boolean;
  relationHelperText?: string;

  /** Current target Global ID (controlled). */
  targetGlobalId: string;
  /** Optional display name shown in the target chip ("GID — name"). */
  targetName?: string;
  /** Called for picks, typed edits, and clearing (globalId/name are "" when cleared). */
  onTargetChange: (globalId: string, name: string) => void;
  targetError: boolean;
  targetHelperText: string;

  /** A separate error shown under the target field (e.g. "select both …"). */
  validationMessage?: string;

  versionPin: number | null;
  onVersionPinChange: (versionPin: number | null) => void;
  /** Whether the version-pin affordance is enabled (caller owns target validity). */
  canPinVersion: boolean;
}

/**
 * The shared link-editor UI used by both the extra-field editor (UpdateField)
 * and the template-field editor (LinkFieldValue): the relation-type field, the
 * target chip + Browse Inventory/ELN buttons + Target Global ID field, the
 * version pill and version-pin control, and the three picker/version dialogs.
 *
 * Controlled and presentational: it owns only the open-state of its three
 * dialogs and a neutral vertical layout. All staged values, validation, the
 * commit buttons, and the commit logic stay with each caller, which differ
 * (constrained vs free-solo relations, a target name, Box vs Grid placement,
 * and different model-commit calls).
 */
export default function LinkEditor({
  relationType,
  onRelationTypeChange,
  relationOptions,
  relationFreeSolo,
  relationLabel,
  relationError,
  relationHelperText,
  targetGlobalId,
  targetName,
  onTargetChange,
  targetError,
  targetHelperText,
  validationMessage,
  versionPin,
  onVersionPinChange,
  canPinVersion,
}: LinkEditorProps): React.ReactElement {
  const [browserOpen, setBrowserOpen] = useState(false);
  const [elnOpen, setElnOpen] = useState(false);
  const [versionDialogOpen, setVersionDialogOpen] = useState(false);

  return (
    <Box>
      <Autocomplete
        freeSolo={relationFreeSolo}
        options={relationOptions}
        value={relationFreeSolo ? relationType : relationType === "" ? null : relationType}
        onChange={relationFreeSolo ? undefined : (_event, value) => onRelationTypeChange(value ?? "")}
        onInputChange={relationFreeSolo ? (_event, value) => onRelationTypeChange(value) : undefined}
        renderInput={(params) => {
          const { slotProps, ...textFieldProps } = params;
          return (
            <TextField
              {...textFieldProps}
              variant="standard"
              label={relationLabel}
              error={relationError}
              helperText={relationHelperText}
              slotProps={{
                ...slotProps,
                htmlInput: {
                  ...slotProps.htmlInput,
                  "aria-label": relationLabel,
                },
              }}
            />
          );
        }}
      />
      <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: "wrap", alignItems: "center" }}>
        {targetGlobalId
          ? (() => {
              const iconData = iconForGlobalId(targetGlobalId);
              return (
                <Chip
                  // Match the committed (non-edit) LinkField pill. size="small"
                  // gives the same geometry; the pl restores the left padding
                  // the accented theme strips from deletable chips
                  // (`&.MuiChip-deletable { padding: 0 }`). Without it the type
                  // icon — which gets no MuiChip-icon margin because
                  // RecordTypeIcon wraps it in a tooltip — sits flush against
                  // the left edge instead of the non-edit pill's 4px
                  // (spacing(0.5)). The selector is repeated to out-specify the
                  // theme's two-class `.MuiChip-deletable` rule. The cancel
                  // button just widens the chip.
                  size="small"
                  sx={{ "&.MuiChip-deletable": { pl: 0.5 } }}
                  icon={iconData ? <RecordTypeIcon record={iconData} aria-hidden /> : undefined}
                  label={targetName ? `${targetGlobalId} — ${targetName}` : targetGlobalId}
                  onDelete={() => onTargetChange("", "")}
                  data-test-id="LinkTarget-globalId"
                />
              );
            })()
          : null}
        <Button size="small" variant="outlined" aria-label="Browse Inventory" onClick={() => setBrowserOpen(true)}>
          Browse Inventory
        </Button>
        <Button size="small" variant="outlined" aria-label="Browse ELN" onClick={() => setElnOpen(true)}>
          Browse ELN
        </Button>
      </Stack>
      <TextField
        label="Target Global ID"
        value={targetGlobalId}
        onChange={(e) => onTargetChange(e.target.value, "")}
        fullWidth
        size="small"
        variant="standard"
        sx={{ mt: 1 }}
        helperText={targetHelperText}
        error={targetError}
        slotProps={{ htmlInput: { "aria-label": "Target Global ID" } }}
      />
      {validationMessage ? <FormHelperText error>{validationMessage}</FormHelperText> : null}
      <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: "center" }}>
        <Chip
          size="small"
          variant="outlined"
          label={versionPin != null ? `Pinned to v${versionPin}` : "Latest"}
          data-test-id="LinkEditor-version"
        />
        <IconButton
          size="small"
          aria-label={targetGlobalId ? `Pin version for ${targetGlobalId}` : "Pin version"}
          disabled={!canPinVersion}
          onClick={() => setVersionDialogOpen(true)}
        >
          <HistoryIcon fontSize="small" />
        </IconButton>
      </Stack>

      <LinkTargetBrowser
        open={browserOpen}
        onCancel={() => setBrowserOpen(false)}
        onPick={(target) => {
          onTargetChange(target.globalId, target.name);
          setBrowserOpen(false);
        }}
      />
      <ElnRecordPicker
        open={elnOpen}
        onCancel={() => setElnOpen(false)}
        onPick={(target) => {
          onTargetChange(target.globalId, target.name);
          setElnOpen(false);
        }}
      />
      <VersionLockDialog
        open={versionDialogOpen}
        globalId={targetGlobalId}
        currentVersionPin={versionPin}
        onConfirm={(pin) => {
          setVersionDialogOpen(false);
          onVersionPinChange(pin);
        }}
        onCancel={() => setVersionDialogOpen(false)}
      />
    </Box>
  );
}
