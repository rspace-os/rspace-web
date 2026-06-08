import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import Autocomplete from "@mui/material/Autocomplete";
import MuiTextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Chip from "@mui/material/Chip";
import FormHelperText from "@mui/material/FormHelperText";
import { type Field, type FieldLink } from "../../../../stores/definitions/Field";
import { DATACITE_RELATION_TYPES } from "../../../components/Fields/Link/dataciteRelationTypes";
import LinkTargetBrowser from "../../../components/Fields/Link/LinkTargetBrowser";
import ElnRecordPicker from "../../../components/Fields/Link/ElnRecordPicker";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import { iconForGlobalId } from "../../../components/Fields/Link/iconForGlobalId";

type LinkFieldValueArgs = {
  field: Field;
  /** The Global ID of the sample owning this field, used to forbid self-links. */
  sourceGlobalId: string;
  disabled: boolean;
  onChange: () => void;
};

const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v\d+)?$/;

/** True when target points at the same item as source, ignoring any version suffix. */
function isSelfLink(sourceGlobalId: string, targetGlobalId: string): boolean {
  const source = GLOBAL_ID_PATTERN.exec(sourceGlobalId);
  const target = GLOBAL_ID_PATTERN.exec(targetGlobalId);
  return Boolean(
    source && target && source[1] === target[1] && source[2] === target[2],
  );
}

/**
 * Editor for a sample's inherited Link field value: pick a relationship type (constrained to the
 * template field's allowed set, or any DataCite type when the whitelist is empty) and a target,
 * reusing the Inventory/ELN target pickers from the extra-field Link UI.
 *
 * Edits are staged locally and only written to the field model on "Apply" (or reverted on
 * "Discard"), mirroring the extra-field Link editor so the two link editors behave consistently.
 */
function LinkFieldValue({
  field,
  sourceGlobalId,
  disabled,
  onChange,
}: LinkFieldValueArgs): React.ReactNode {
  const [browserOpen, setBrowserOpen] = useState(false);
  const [elnOpen, setElnOpen] = useState(false);

  const committedRelationType = field.link?.relationType ?? "";
  const committedTargetGlobalId = field.link?.targetGlobalId ?? "";

  // staged (uncommitted) edits; committed to the field model only on Apply
  const [stagedRelationType, setStagedRelationType] = useState<string>(
    committedRelationType,
  );
  const [stagedTargetGlobalId, setStagedTargetGlobalId] = useState<string>(
    committedTargetGlobalId,
  );

  // re-sync staged state when the committed link changes (record switch, post-save round-trip)
  useEffect(() => {
    setStagedRelationType(committedRelationType);
    setStagedTargetGlobalId(committedTargetGlobalId);
  }, [committedRelationType, committedTargetGlobalId]);

  const relationOptions =
    field.allowedRelationTypes.length > 0
      ? field.allowedRelationTypes
      : [...DATACITE_RELATION_TYPES];

  const changed =
    stagedRelationType !== committedRelationType ||
    stagedTargetGlobalId !== committedTargetGlobalId;
  const bothEmpty = stagedRelationType === "" && stagedTargetGlobalId === "";
  const bothSet = stagedRelationType !== "" && stagedTargetGlobalId !== "";
  const selfLinked = bothSet && isSelfLink(sourceGlobalId, stagedTargetGlobalId);
  // a stageable value is either fully cleared (removing the link) or a complete, non-self link
  const canApply = changed && (bothEmpty || (bothSet && !selfLinked));

  const validationMessage = selfLinked
    ? "An item cannot link to itself."
    : changed && !bothEmpty && !bothSet
      ? "Select both a relationship type and a target before applying."
      : "";

  const apply = (): void => {
    const nextLink: FieldLink | null = bothEmpty
      ? null
      : {
          relationType: stagedRelationType,
          targetGlobalId: stagedTargetGlobalId,
          versionPin: field.link?.versionPin ?? null,
        };
    field.setAttributesDirty({ link: nextLink });
    field.setError(false);
    onChange();
  };

  const discard = (): void => {
    setStagedRelationType(committedRelationType);
    setStagedTargetGlobalId(committedTargetGlobalId);
  };

  return (
    <Box>
      <Autocomplete
        disabled={disabled}
        options={relationOptions}
        value={stagedRelationType === "" ? null : stagedRelationType}
        onChange={(_event, value) => setStagedRelationType(value ?? "")}
        renderInput={(params) => (
          <MuiTextField
            {...params}
            variant="standard"
            label="Relationship type"
            inputProps={{
              ...params.inputProps,
              "aria-label": "Relationship type",
            }}
          />
        )}
      />
      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        sx={{ mt: 1, flexWrap: "wrap" }}
      >
        {stagedTargetGlobalId
          ? (() => {
              const iconData = iconForGlobalId(stagedTargetGlobalId);
              return (
                <Chip
                  icon={
                    iconData ? (
                      <RecordTypeIcon record={iconData} aria-hidden />
                    ) : undefined
                  }
                  label={stagedTargetGlobalId}
                  onDelete={
                    disabled ? undefined : () => setStagedTargetGlobalId("")
                  }
                  data-test-id="LinkTarget-globalId"
                />
              );
            })()
          : null}
        {!disabled && (
          <>
            <Button
              size="small"
              variant="outlined"
              aria-label="Browse Inventory"
              onClick={() => setBrowserOpen(true)}
            >
              Browse Inventory
            </Button>
            <Button
              size="small"
              variant="outlined"
              aria-label="Browse ELN"
              onClick={() => setElnOpen(true)}
            >
              Browse ELN
            </Button>
          </>
        )}
      </Stack>
      {validationMessage && (
        <FormHelperText error>{validationMessage}</FormHelperText>
      )}
      {!disabled && (
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <Button
            color="callToAction"
            disableElevation
            variant="contained"
            aria-label="Apply link"
            onClick={apply}
            disabled={!canApply}
            data-test-id="ApplyLinkButton"
          >
            Apply
          </Button>
          <Button
            variant="text"
            aria-label="Discard link changes"
            onClick={discard}
            disabled={!changed}
            data-test-id="DiscardLinkButton"
          >
            Discard
          </Button>
        </Stack>
      )}
      <LinkTargetBrowser
        open={browserOpen}
        onCancel={() => setBrowserOpen(false)}
        onPick={(target) => {
          setStagedTargetGlobalId(target.globalId);
          setBrowserOpen(false);
        }}
      />
      <ElnRecordPicker
        open={elnOpen}
        onCancel={() => setElnOpen(false)}
        onPick={(target) => {
          setStagedTargetGlobalId(target.globalId);
          setElnOpen(false);
        }}
      />
    </Box>
  );
}

export default observer(LinkFieldValue);
