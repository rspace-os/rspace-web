//@flow

import React, { type Node, type ComponentType } from "react";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { type IdentifierField } from "../../../../stores/definitions/Identifier";
import {
  subFields,
  subFieldsForNew,
  RECOMMENDED_FIELDS_LABELS,
} from "../../../../stores/models/IdentifierModel";
import { newGeoLocation } from "../../../../stores/definitions/GeoLocation";
import GeoLocationField from "./GeoLocationField";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import AddButton from "../../../../components/AddButton";
import RemoveButton from "../../../../components/RemoveButton";
import DateField from "../../../../components/Inputs/DateField";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import { capitaliseJustFirstChar } from "../../../../util/Util";

export const isEmpty = (v: string): boolean => v === "";

type MultipleInputArgs = {|
  field: IdentifierField,
  activeResult: InventoryRecord,
  editable: boolean,
|};

/**
 * A component designed to handle the "easy" addition, editing and removal of values to an array
 * via multiple input fields.
 * If field has subFields in values, an additional array of text fields is rendered.
 * If field has options, an additional dropdown is rendered.
 */
const MultipleInputHandler = ({
  field,
  activeResult,
  editable,
}: MultipleInputArgs): Node => {
  const itemLabel: string = field.key.slice(0, field.key.length - 1);

  const isDuplicate = (v: string): boolean =>
    /* eslint-disable */
    // $FlowFixMe[incompatible-use]
    // $FlowFixMe[missing-local-annot]
    field.value.filter((val) => val === v).length > 1;
  /* eslint-enable */
  const isRequired = (k: string): boolean => k === "freeType";

  const newItem = () => {
    return field.key === "Geolocations"
      ? newGeoLocation
      : {
          value: field.key === "Dates" ? new Date() : "",
          ...(field.options ? { type: field.options[0].value } : null),
          // $FlowExpectedError[invalid-computed-prop]
          ...(subFieldsForNew[field.key] ?? null),
        };
  };

  const handleAdd = (): void => {
    if (!field.handler) throw new Error("Cannot add a new entry");
    // $FlowFixMe[incompatible-use]
    field.handler(field.value.concat(newItem()));
  };

  const doUpdateIdentifiers = (): void => {
    if (field.handler) field.handler(field.value);
    /* setAttributesDirty on item */
    activeResult.updateIdentifiers();
  };

  const handleRemove = (index: number): void => {
    runInAction(() => {
      // $FlowFixMe[incompatible-use]
      field.value.splice(index, 1);
    });
    doUpdateIdentifiers();
  };

  const handleUpdateValue = (
    index: number,
    key: string,
    newValue: string | Date
  ): void => {
    runInAction(() => {
      // $FlowFixMe[incompatible-use]
      field.value[index][key] = newValue;
    });
    doUpdateIdentifiers();
  };

  type RecommendedFieldArgs = {|
    v: IdentifierField,
    i: number,
  |};

  const RecommendedField = observer(({ v, i }: RecommendedFieldArgs): Node => {
    return (
      <>
        <Grid
          item
          sx={{
            flexGrow: 1,
            mb: 0.5,
            p: 1,
            border: "1px dotted grey",
            borderRadius: "4px",
          }}
        >
          {editable ? (
            // FormControl required to prevent warning (although may prevent flexGrow to work)
            <FormControl sx={{ width: "100%" }}>
              {v.value instanceof Date ? (
                <DateField
                  variant="outlined"
                  value={v.value.toString()}
                  disabled={false}
                  onChange={({ target: { value } }) => {
                    if (value) handleUpdateValue(i, "value", value);
                  }}
                  datatestid={`IdentifierRecommendedField-${field.key}-${i}`}
                />
              ) : (
                <TextField
                  style={{
                    marginBottom:
                      // $FlowFixMe[incompatible-use]
                      subFields(field.value[i]).length > 0 ? "10px" : "0px",
                  }}
                  InputLabelProps={{ shrink: true }}
                  size="small"
                  variant="standard"
                  fullWidth
                  id={`IdentifierRecommendedField-${field.key}-${i}`}
                  disabled={false}
                  value={v.value ?? ""}
                  placeholder={`Enter value for new ${itemLabel}`}
                  onChange={({ target: { value } }) => {
                    handleUpdateValue(i, "value", value);
                  }}
                  // $FlowFixMe[incompatible-call]
                  error={(editable && isDuplicate(v.value)) || isEmpty(v.value)}
                  helperText={
                    // $FlowFixMe[incompatible-call]
                    editable && isEmpty(v.value)
                      ? "Enter a value (or remove entry)"
                      : // $FlowFixMe[incompatible-call]
                      editable && isDuplicate(v.value)
                      ? "This value is a duplicate. Please enter a unique one."
                      : null
                  }
                />
              )}
              {/* subFields are optional - as well as most of their values - even if parent value is filled */}
              {/* a required exception is the Alternate Identifier type */}

              {
                //$FlowFixMe[incompatible-use]
                subFields(field.value[i]).length > 0 &&
                  //$FlowFixMe[incompatible-use]
                  subFields(field.value[i]).map((subField) => (
                    <InputWrapper
                      label={RECOMMENDED_FIELDS_LABELS[subField.key]}
                      key={subField.key}
                    >
                      <Grid
                        container
                        direction="row"
                        justifyContent="space-between"
                        spacing={1}
                        sx={{ width: "95%", m: 1 }}
                      >
                        <Grid item sx={{ flexGrow: 1 }}>
                          <TextField
                            InputLabelProps={{ shrink: true }}
                            size="small"
                            variant="standard"
                            fullWidth
                            id={`IdentifierRecommendedSubField-${subField.key}-${i}`}
                            disabled={false}
                            value={subField.value ?? ""}
                            placeholder={`Enter value for ${
                              RECOMMENDED_FIELDS_LABELS[subField.key]
                            }`}
                            onChange={({ target: { value } }) => {
                              handleUpdateValue(i, subField.key, value);
                            }}
                            /* value is optional for most subFields, not all */
                            error={
                              isEmpty(subField.value) &&
                              isRequired(subField.key)
                            }
                            helperText={
                              isEmpty(subField.value) &&
                              isRequired(subField.key)
                                ? "A value is required"
                                : null
                            }
                          />
                        </Grid>
                      </Grid>
                    </InputWrapper>
                  ))
              }
            </FormControl>
          ) : (
            <>
              <Grid item>
                {v.value instanceof Date
                  ? v.value.toISOString().split("T")[0]
                  : v.value}
              </Grid>
              {
                //$FlowFixMe[incompatible-use]
                subFields(field.value[i]).length > 0 &&
                  // $FlowFixMe[incompatible-use]
                  subFields(field.value[i]).map((sf) => (
                    <Grid
                      container
                      direction="row"
                      key={sf.key}
                      spacing={1}
                      sx={{ margin: "8px" }}
                    >
                      <Grid item sx={{ minWidth: "150px" }}>
                        <>{RECOMMENDED_FIELDS_LABELS[sf.key]}:</>
                      </Grid>
                      <Grid item>
                        {sf.value ? (
                          <>{sf.value}</>
                        ) : (
                          <em style={{ color: "#949494" }}>None</em>
                        )}
                      </Grid>
                    </Grid>
                  ))
              }
            </>
          )}
        </Grid>
        {field.options && (
          <Grid item sx={{ minWidth: "135px" }}>
            {editable ? (
              <FormControl>
                <Select
                  variant="standard"
                  data-test-id={`${field.key}-option-selector`}
                  // $FlowFixMe[incompatible-use]
                  value={field.value[i].type}
                  onChange={({ target: { value } }) =>
                    handleUpdateValue(i, "type", value)
                  }
                  inputProps={{
                    "aria-label": field.selectAriaLabel ?? "",
                  }}
                >
                  {field.options.map((option) => (
                    <MenuItem
                      key={option.value}
                      value={option.value}
                      data-test-id={`field-option-${option.value}`}
                    >
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            ) : (
              <>
                {
                  // $FlowFixMe[incompatible-use]
                  capitaliseJustFirstChar(field.value[i].type.toLowerCase())
                }
              </>
            )}
          </Grid>
        )}
      </>
    );
  });

  return (
    <InputWrapper
      label={field.key}
      actions={
        <AddButton
          disabled={!editable}
          title={
            editable
              ? `Add a new ${itemLabel}`
              : `To add a ${itemLabel}, press Edit first`
          }
          onClick={handleAdd}
        />
      }
    >
      {
        // $FlowFixMe[incompatible-use]
        field.value.map((v: mixed, i: number) => (
          <Grid
            container
            direction="row"
            justifyContent="space-between"
            spacing={1}
            sx={{ width: "100%", m: 0.5 }}
            key={i}
            wrap="nowrap"
          >
            {field.key === "Geolocations" ? (
              <GeoLocationField
                // $FlowFixMe[incompatible-type]
                geoLocation={v}
                i={i}
                editable={editable}
                handleUpdateValue={handleUpdateValue}
                doUpdateIdentifiers={doUpdateIdentifiers}
              />
            ) : (
              // $FlowFixMe[incompatible-type]
              <RecommendedField v={v} i={i} />
            )}
            <Grid item sx={{ marginRight: "6px" }}>
              <RemoveButton
                disabled={!editable}
                title={
                  editable
                    ? `Remove this ${itemLabel}`
                    : `To remove any ${itemLabel}, press Edit first`
                }
                onClick={() => handleRemove(i)}
              />
            </Grid>
          </Grid>
        ))
      }
    </InputWrapper>
  );
};

export default (observer(
  MultipleInputHandler
): ComponentType<MultipleInputArgs>);
