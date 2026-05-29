import React, { useState, useEffect } from "react";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Button from "@mui/material/Button";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import { match } from "../../../../util/Util";
import {
  type ExtraField,
  type ExtraFieldType,
  type ExtraInventoryLink,
} from "../../../../stores/definitions/ExtraField";
import { pick } from "../../../../util/unsafeUtils";
import FormField from "../../../../components/Inputs/FormField";
import {
  DATACITE_RELATION_TYPES,
  isValidDataCiteRelationType,
} from "../Link/dataciteRelationTypes";
import LinkTargetBrowser from "../Link/LinkTargetBrowser";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import { iconForInventoryGlobalId } from "../Link/iconForGlobalId";

const INVENTORY_TARGET_PREFIXES = new Set(["SA", "SS", "IC", "IN"]);
const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v(\d+))?$/;

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
  const link = (
    extraField as ExtraField & { link?: ExtraInventoryLink | null }
  ).link;
  return link
    ? {
        relationType: link.relationType,
        targetGlobalId: link.targetGlobalId,
        targetName: "",
        versionPin: link.versionPin ?? null,
      }
    : emptyLinkState();
}

function isSelfLink(sourceGlobalId: string, targetGlobalId: string): boolean {
  const source = GLOBAL_ID_PATTERN.exec(sourceGlobalId);
  const target = GLOBAL_ID_PATTERN.exec(targetGlobalId);
  return Boolean(
    source && target && source[1] === target[1] && source[2] === target[2],
  );
}

function validateLink(
  link: LinkState,
  sourceGlobalId: string,
): { ok: boolean; reason: string } {
  if (!isValidDataCiteRelationType(link.relationType))
    return { ok: false, reason: "Pick a DataCite relation type" };
  const match = GLOBAL_ID_PATTERN.exec(link.targetGlobalId);
  if (!match) return { ok: false, reason: "Target Global ID is required" };
  if (!INVENTORY_TARGET_PREFIXES.has(match[1]))
    return { ok: false, reason: "Target must be an Inventory record" };
  if (isSelfLink(sourceGlobalId, link.targetGlobalId))
    return { ok: false, reason: "An item cannot link to itself" };
  return { ok: true, reason: "" };
}

export default function UpdateField({
  extraField,
  index,
  record,
}: UpdateFieldArgs): React.ReactNode {
  const [fieldState, setFieldState] = useState<{
    name: string;
    type: ExtraFieldType | "";
  }>({ name: "", type: "" });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [linkState, setLinkState] = useState<LinkState>(emptyLinkState());
  const [browserOpen, setBrowserOpen] = useState(false);

  useEffect(() => {
    if (extraField) {
      setFieldState({
        name: extraField.name,
        type: extraField.type,
      });
      setLinkState(linkStateFromExtraField(extraField));
    }
  }, [extraField]);

  const sourceGlobalId = record.globalId ?? "";
  const linkValidity =
    fieldState.type === "Link"
      ? validateLink(linkState, sourceGlobalId)
      : { ok: true, reason: "" };

  const initialLink = linkStateFromExtraField(extraField);
  const linkChanged =
    extraField.type === "Link" &&
    (linkState.relationType !== initialLink.relationType ||
      linkState.targetGlobalId !== initialLink.targetGlobalId ||
      linkState.versionPin !== initialLink.versionPin);

  const canSubmit =
    !errorMessage &&
    fieldState.name !== "" &&
    fieldState.type !== "" &&
    (fieldState.type !== "Link" || linkValidity.ok) &&
    (fieldState.name !== extraField.name ||
      fieldState.type !== extraField.type ||
      linkChanged);

  const handleNameChange = ({
    target: { value },
  }: {
    target: { value: string };
  }) => {
    setFieldState({
      ...fieldState,
      name: value,
    });
    setErrorMessage(
      match<void, string | null>([
        [() => value === "", "Name should not be empty."],
        [
          () => value.length > 255,
          "Name must be no longer than 255 characters.",
        ],
        [
          () => {
            const unchangedName = value === extraField.name;
            return (
              extraField.owner.fieldNamesInUse.filter((n) => n === value)
                .length > Number(unchangedName)
            );
          },
          "You either already have a field with that name or that name is not permitted.",
        ],
        [() => true, null],
      ])(),
    );
  };

  const handleTypeChange = ({
    target: { value },
  }: {
    target: { value: string };
  }) => {
    setFieldState({
      ...fieldState,
      type: value as ExtraFieldType,
    });
    if (value === "Link" && !linkState.targetGlobalId) {
      setLinkState(emptyLinkState());
    }
  };

  const handleBrowserPick = (target: { globalId: string; name: string }) => {
    setLinkState({
      ...linkState,
      targetGlobalId: target.globalId,
      targetName: target.name,
    });
    setBrowserOpen(false);
  };

  const update = () => {
    if (fieldState.type === "Link") {
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
      aria-label={
        extraField.id === null
          ? "New extra field"
          : `Editing extra field with name ${extraField.name}`
      }
    >
      <Grid item md={7} xs={12}>
        <FormField
          value={fieldState.name}
          label="Field name"
          renderInput={(props) => (
            <TextField
              {...props}
              variant="standard"
              onChange={handleNameChange}
            />
          )}
          maxLength={255}
          error={Boolean(errorMessage)}
          helperText={errorMessage}
        />
      </Grid>
      <Grid item md={5} xs={12}>
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
        <>
          <Grid item xs={12}>
            <Autocomplete
              freeSolo
              options={[...DATACITE_RELATION_TYPES]}
              value={linkState.relationType}
              onInputChange={(_event, value) =>
                setLinkState({ ...linkState, relationType: value })
              }
              renderInput={(params) => (
                <TextField
                  {...params}
                  variant="standard"
                  label="Relation type"
                  inputProps={{
                    ...params.inputProps,
                    "aria-label": "Relation type",
                  }}
                />
              )}
            />
          </Grid>
          <Grid item xs={12}>
            <Box>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
                {linkState.targetGlobalId ? (
                  (() => {
                    const iconData = iconForInventoryGlobalId(
                      linkState.targetGlobalId,
                    );
                    return (
                      <Chip
                        icon={
                          iconData ? (
                            <RecordTypeIcon record={iconData} aria-hidden />
                          ) : undefined
                        }
                        label={
                          linkState.targetName
                            ? `${linkState.targetGlobalId} — ${linkState.targetName}`
                            : linkState.targetGlobalId
                        }
                        onDelete={() =>
                          setLinkState({
                            ...linkState,
                            targetGlobalId: "",
                            targetName: "",
                          })
                        }
                        data-test-id="LinkTarget-globalId"
                      />
                    );
                  })()
                ) : null}
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => setBrowserOpen(true)}
                  aria-label={
                    linkState.targetGlobalId
                      ? "Change target"
                      : "Browse Inventory"
                  }
                >
                  {linkState.targetGlobalId ? "Change" : "Browse Inventory"}
                </Button>
              </Stack>
              <TextField
                label="Target Global ID"
                value={linkState.targetGlobalId}
                onChange={(e) =>
                  setLinkState({
                    ...linkState,
                    targetGlobalId: e.target.value,
                    targetName: "",
                  })
                }
                fullWidth
                size="small"
                variant="standard"
                helperText={
                  !linkValidity.ok && linkState.targetGlobalId !== ""
                    ? linkValidity.reason
                    : "Paste a Global ID, or use Browse Inventory above."
                }
                error={!linkValidity.ok && linkState.targetGlobalId !== ""}
              />
            </Box>
          </Grid>
        </>
      )}

      <Grid item md={12} xs={12}>
        <Button
          color="callToAction"
          disableElevation
          variant="contained"
          aria-label="Update field"
          onClick={update}
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
          onClick={() =>
            extraField.initial
              ? record.removeExtraField(null, index)
              : discardChanges()
          }
          data-test-id={"DiscardOrCancelButton"}
        >
          {extraField.initial ? "Discard" : "Cancel"}
        </Button>
      </Grid>

      <LinkTargetBrowser
        open={browserOpen}
        onCancel={() => setBrowserOpen(false)}
        onPick={handleBrowserPick}
      />
    </Grid>
  );
}
