import React, { type ReactNode, type ComponentType } from "react";
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
import GeoLocationModel from "../../../../stores/models/GeoLocationModel";

export const isEmpty = (v: string): boolean => v === "";

type MultipleInputArgs = {
  field: IdentifierField;
  activeResult: InventoryRecord;
  editable: boolean;
};

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
}: MultipleInputArgs): ReactNode => {
  const itemLabel: string = field.key.slice(0, field.key.length - 1);

  const isDuplicate = (v: string): boolean =>
    // @ts-expect-error - field.value is an array
    field.value.filter((val) => val === v).length > 1;

  const isRequired = (k: string): boolean => k === "freeType";

  const newItem = () => {
    return field.key === "Geolocations"
      ? new GeoLocationModel({
          ...newGeoLocation,
        })
      : {
          value: field.key === "Dates" ? new Date() : "",
          ...(field.options ? { type: field.options[0].value } : null),
          // @ts-expect-error - computed property
          ...(subFieldsForNew[field.key] ?? null),
        };
  };

  const handleAdd = (): void => {
    if (!field.handler) throw new Error("Cannot add a new entry");
    // @ts-expect-error - field.value is an array
    field.handler(field.value.concat(newItem()));
  };

  const doUpdateIdentifiers = (): void => {
    if (field.handler) field.handler(field.value);
    /* setAttributesDirty on item */
    activeResult.updateIdentifiers();
  };

  const handleRemove = (index: number): void => {
    runInAction(() => {
      // @ts-expect-error - field.value is an array
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
      // @ts-expect-error - field.value is an array
      field.value[index][key] = newValue;
    });
    doUpdateIdentifiers();
  };

  type RecommendedFieldArgs = {
    v: IdentifierField;
    i: number;
  };

  const RecommendedField = observer(
    ({ v, i }: RecommendedFieldArgs): ReactNode => {
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
                    // @ts-ignore - using datatestid instead of data-testid to maintain backward compatibility
                    datatestid={`IdentifierRecommendedField-${field.key}-${i}`}
                  />
                ) : (
                  <TextField
                    style={{
                      marginBottom:
                        // @ts-expect-error - subFields accepts field.value[i]
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
                    error={
                      (editable && isDuplicate(String(v.value))) ||
                      isEmpty(String(v.value))
                    }
                    helperText={
                      editable && isEmpty(String(v.value))
                        ? "Enter a value (or remove entry)"
                        : editable && isDuplicate(String(v.value))
                        ? "This value is a duplicate. Please enter a unique one."
                        : null
                    }
                  />
                )}
                {
                  // @ts-expect-error - subFields accepts field.value[i]
                  subFields(field.value[i]).length > 0 &&
                    // @ts-expect-error - subFields accepts field.value[i]
                    subFields(field.value[i]).map((subField) => (
                      <InputWrapper
                        label={
                          RECOMMENDED_FIELDS_LABELS[
                            subField.key as keyof typeof RECOMMENDED_FIELDS_LABELS
                          ]
                        }
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
                                RECOMMENDED_FIELDS_LABELS[
                                  subField.key as keyof typeof RECOMMENDED_FIELDS_LABELS
                                ]
                              }`}
                              onChange={({ target: { value } }) => {
                                handleUpdateValue(i, subField.key, value);
                              }}
                              /* value is optional for most subFields, not all */
                              error={
                                isEmpty(String(subField.value)) &&
                                isRequired(subField.key)
                              }
                              helperText={
                                isEmpty(String(subField.value)) &&
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
                    : String(v.value)}
                </Grid>
                {
                  // @ts-expect-error - subFields accepts field.value[i]
                  subFields(field.value[i]).length > 0 &&
                    // @ts-expect-error - subFields accepts field.value[i]
                    subFields(field.value[i]).map((sf) => (
                      <Grid
                        container
                        direction="row"
                        key={sf.key}
                        spacing={1}
                        sx={{ margin: "8px" }}
                      >
                        <Grid item sx={{ minWidth: "150px" }}>
                          <>
                            {
                              RECOMMENDED_FIELDS_LABELS[
                                sf.key as keyof typeof RECOMMENDED_FIELDS_LABELS
                              ]
                            }
                            :
                          </>
                        </Grid>
                        <Grid item>
                          {sf.value ? (
                            <>{String(sf.value)}</>
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
                    // @ts-expect-error - field.value[i].type exists
                    value={field.value[i].type}
                    onChange={({ target: { value } }) =>
                      handleUpdateValue(i, "type", value as string)
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
                    // @ts-expect-error - field.value[i].type exists
                    capitaliseJustFirstChar(field.value[i].type.toLowerCase())
                  }
                </>
              )}
            </Grid>
          )}
        </>
      );
    }
  );

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
        // @ts-expect-error - field.value is an array
        field.value.map((v: unknown, i: number) => (
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
                // @ts-expect-error - v is a GeoLocationModel
                geoLocation={v}
                i={i}
                editable={editable}
                handleUpdateValue={handleUpdateValue}
                doUpdateIdentifiers={doUpdateIdentifiers}
              />
            ) : (
              // @ts-expect-error - v is a field
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

export default observer(
  MultipleInputHandler
) as ComponentType<MultipleInputArgs>;
