import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useEffect, useState } from "react";
import FormField from "../../../../components/Inputs/FormField";
import type { ExtraField, ExtraFieldType } from "../../../../stores/definitions/ExtraField";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { match } from "../../../../util/Util";
import { pick } from "../../../../util/unsafeUtils";
import { DATACITE_RELATION_TYPES, isValidDataCiteRelationType } from "../Link/dataciteRelationTypes";
import { isInventoryGlobalId, supportsVersionPin } from "../Link/iconForGlobalId";
import LinkEditor from "../Link/LinkEditor";
import { validateTarget } from "../Link/linkTarget";
import { checkLinkTargetExists } from "../Link/linkTargetExists";
import useLinkTargetSummary from "../Link/useLinkTargetSummary";

type UpdateFieldArgs = {
  extraField: ExtraField;
  index: number;
  record: InventoryRecord;
};

type LinkState = {
  relationType: string;
  targetGlobalId: string;
  targetName: string;
  versionPin: number | null;
};

function emptyLinkState(): LinkState {
  return {
    relationType: "",
    targetGlobalId: "",
    targetName: "",
    versionPin: null,
  };
}

function linkStateFromExtraField(extraField: ExtraField): LinkState {
  const link = extraField.link;
  return link
    ? {
        relationType: link.relationType,
        targetGlobalId: link.targetGlobalId,
        targetName: "",
        versionPin: link.versionPin ?? null,
      }
    : emptyLinkState();
}

export default function UpdateField({ extraField, index, record }: UpdateFieldArgs): React.ReactNode {
  const [fieldState, setFieldState] = useState<{
    name: string;
    type: ExtraFieldType | "";
  }>({ name: "", type: "" });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [linkState, setLinkState] = useState<LinkState>(emptyLinkState());
  // set when Apply finds the typed target does not resolve on the server
  const [targetExistenceError, setTargetExistenceError] = useState<string | null>(null);
  const [checkingTarget, setCheckingTarget] = useState(false);

  useEffect(() => {
    if (extraField) {
      setFieldState({
        name: extraField.name,
        type: extraField.type,
      });
      setLinkState(linkStateFromExtraField(extraField));
    }
  }, [extraField]);

  // Mid-edit values live only in this editor and are committed on Apply.
  // The record-level Save is greyed out while the editor is open
  // (ExtraFieldModel.isValid rejects an editing field), so nothing typed here
  // can be lost to a premature Save, and the model's pre-edit state stays
  // intact for Cancel. Syncing staged values into the model as they were
  // typed (the previous approach) made canSubmit's "something changed" check
  // compare equal values, greying out Apply on a fully-filled new field.

  const sourceGlobalId = record.globalId ?? "";
  const isLink = fieldState.type === "Link";
  const relationValid = !isLink || isValidDataCiteRelationType(linkState.relationType);
  const targetValidity = isLink ? validateTarget(linkState.targetGlobalId, sourceGlobalId) : { ok: true, reason: "" };
  const linkValid = relationValid && targetValidity.ok;
  // surface the relation error on the relation field once the user has begun entering the link
  const showRelationError =
    isLink && !relationValid && (linkState.relationType !== "" || linkState.targetGlobalId !== "");
  // the target error shows for any non-empty invalid target, and also when an
  // existing link's target has been removed ("Target Global ID is required")
  const showTargetError = isLink && !targetValidity.ok && (linkState.targetGlobalId !== "" || !extraField.initial);

  const initialLink = linkStateFromExtraField(extraField);
  const linkChanged =
    extraField.type === "Link" &&
    (linkState.relationType !== initialLink.relationType ||
      linkState.targetGlobalId !== initialLink.targetGlobalId ||
      linkState.versionPin !== initialLink.versionPin);

  // Surface an invalid in-progress edit of an existing link (e.g. a removed
  // target) on the model, so the record-level Save is blocked with an error
  // instead of silently reverting to the stored link. New fields are covered
  // by the live model sync above plus the model's own link validation. Only
  // Link fields are touched: Number fields manage invalidInput themselves.
  const dirtyAndInvalid = !extraField.initial && isLink && linkChanged && !linkValid;
  useEffect(() => {
    if (extraField.type !== "Link") return;
    extraField.setInvalidInput(dirtyAndInvalid);
  }, [dirtyAndInvalid, extraField]);

  // A no-access committed target (an unshared ELN item) cannot be
  // version-pinned, so the clock stays greyed while editing it. Keyed on the
  // committed target, so it clears after a successful Apply commits a
  // different, readable target and is reimposed on the next edit after Cancel.
  // Inventory targets keep a limited-read view, so they are never "no access".
  const committedTarget = extraField.link?.targetGlobalId ?? "";
  const committedSummary = useLinkTargetSummary(committedTarget);
  const committedNoAccess = committedSummary?.readable === false && !isInventoryGlobalId(committedTarget);

  const canSubmit =
    !errorMessage &&
    !targetExistenceError &&
    !checkingTarget &&
    fieldState.name !== "" &&
    fieldState.type !== "" &&
    (fieldState.type !== "Link" || linkValid) &&
    (fieldState.name !== extraField.name || fieldState.type !== extraField.type || linkChanged);

  const handleNameChange = ({ target: { value } }: { target: { value: string } }) => {
    setFieldState({
      ...fieldState,
      name: value,
    });
    setErrorMessage(
      match<void, string | null>([
        [() => value === "", "Name should not be empty."],
        [() => value.length > 255, "Name must be no longer than 255 characters."],
        [
          () => {
            const unchangedName = value === extraField.name;
            return extraField.owner.fieldNamesInUse.filter((n) => n === value).length > Number(unchangedName);
          },
          "You either already have a field with that name or that name is not permitted.",
        ],
        [() => true, null],
      ])(),
    );
  };

  const handleTypeChange = ({ target: { value } }: { target: { value: string } }) => {
    setFieldState({
      ...fieldState,
      type: value as ExtraFieldType,
    });
    if (value === "Link" && !linkState.targetGlobalId) {
      setLinkState(emptyLinkState());
    }
  };

  // a version pin belongs to a specific target, so retargeting reverts to Latest
  const versionPinFor = (targetGlobalId: string): number | null =>
    targetGlobalId === linkState.targetGlobalId ? linkState.versionPin : null;

  // single handler for picks, typed edits, and clearing; globalId/name are "" when cleared
  const handleTargetChange = (globalId: string, name: string) => {
    setTargetExistenceError(null);
    setLinkState({
      ...linkState,
      targetGlobalId: globalId,
      targetName: name,
      versionPin: versionPinFor(globalId),
    });
  };

  const update = async () => {
    if (fieldState.type === "Link") {
      // a structurally-valid Global ID must also resolve to a real, readable
      // record; check (re)targeted links against the server before committing
      if (linkState.targetGlobalId !== initialLink.targetGlobalId) {
        setCheckingTarget(true);
        const exists = await checkLinkTargetExists(linkState.targetGlobalId);
        setCheckingTarget(false);
        if (!exists) {
          setTargetExistenceError(
            `${linkState.targetGlobalId} does not exist, or you do not have permission to view it.`,
          );
          return;
        }
      }
      extraField.setInvalidInput(false);
      record.updateExtraField(extraField.name, {
        name: fieldState.name,
        type: fieldState.type,
        link: {
          relationType: linkState.relationType,
          targetGlobalId: linkState.targetGlobalId,
          versionPin: linkState.versionPin,
        },
      });
    } else {
      record.updateExtraField(extraField.name, {
        name: fieldState.name,
        type: fieldState.type,
      });
    }
  };

  const discardChanges = () => {
    extraField.setInvalidInput(false);
    record.updateExtraField(
      extraField.name,
      pick("name", "type")(extraField) as {
        name: typeof extraField.name;
        type: typeof extraField.type;
      },
    );
  };

  if (!extraField) return null;

  return (
    <Grid
      container
      spacing={1}
      role="group"
      aria-label={extraField.id === null ? "New extra field" : `Editing extra field with name ${extraField.name}`}
    >
      <Grid size={{ md: 7, xs: 12 }}>
        <FormField
          value={fieldState.name}
          label="Field name"
          renderInput={(props) => <TextField {...props} variant="standard" onChange={handleNameChange} />}
          maxLength={255}
          error={Boolean(errorMessage)}
          helperText={errorMessage}
        />
      </Grid>
      <Grid size={{ md: 5, xs: 12 }}>
        <FormField
          value={fieldState.type}
          label="Field type"
          disabled={Boolean(extraField.id)}
          renderInput={(props) => (
            <Select
              {...props}
              sx={{ mt: 3 }}
              variant="standard"
              onChange={handleTypeChange}
              SelectDisplayProps={
                {
                  "data-testid": "FieldTypeSelect",
                } as React.HTMLAttributes<HTMLDivElement>
              }
            >
              <MenuItem value="Text">Text</MenuItem>
              <MenuItem value="Number">Number</MenuItem>
              <MenuItem value="Link">Link</MenuItem>
            </Select>
          )}
        />
      </Grid>

      {fieldState.type === "Link" && (
        <Grid size={12}>
          <LinkEditor
            relationType={linkState.relationType}
            onRelationTypeChange={(value) => setLinkState({ ...linkState, relationType: value })}
            relationOptions={[...DATACITE_RELATION_TYPES]}
            relationFreeSolo
            relationLabel="Relation type"
            relationError={showRelationError}
            relationHelperText={showRelationError ? "Pick a DataCite relation type" : ""}
            targetGlobalId={linkState.targetGlobalId}
            targetName={linkState.targetName}
            onTargetChange={handleTargetChange}
            targetError={Boolean(targetExistenceError) || showTargetError}
            targetHelperText={
              targetExistenceError ??
              (showTargetError ? targetValidity.reason : "Paste a Global ID, or use the Browse buttons above.")
            }
            versionPin={linkState.versionPin}
            onVersionPinChange={(versionPin) => setLinkState({ ...linkState, versionPin })}
            canPinVersion={
              linkState.targetGlobalId !== "" &&
              targetValidity.ok &&
              supportsVersionPin(linkState.targetGlobalId) &&
              !committedNoAccess
            }
          />
        </Grid>
      )}

      <Grid size={12}>
        <Button
          color="callToAction"
          disableElevation
          variant="contained"
          aria-label="Update field"
          onClick={() => {
            void update();
          }}
          disabled={!canSubmit}
          data-test-id={"ApplyOrUpdateButton"}
        >
          {extraField.initial ? "Apply" : "Update"}
        </Button>
        <Button
          style={{ marginLeft: "10px" }}
          disableElevation
          variant="text"
          aria-label="Cancel update"
          onClick={() => (extraField.initial ? record.removeExtraField(null, index) : discardChanges())}
          data-test-id={"DiscardOrCancelButton"}
        >
          {extraField.initial ? "Discard" : "Cancel"}
        </Button>
      </Grid>
    </Grid>
  );
}
