import FormControl from "@mui/material/FormControl";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import type { ComponentType, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import AddButton from "../../../../components/AddButton";
import DateField from "../../../../components/Inputs/DateField";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import RemoveButton from "../../../../components/RemoveButton";
import { newGeoLocation } from "../../../../stores/definitions/GeoLocation";
import type { IdentifierField } from "../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import GeoLocationModel from "../../../../stores/models/GeoLocationModel";
import { RECOMMENDED_FIELDS_LABELS, subFields, subFieldsForNew } from "../../../../stores/models/IdentifierModel";
import { capitaliseJustFirstChar } from "../../../../util/Util";
import GeoLocationField from "./GeoLocationField";

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
const MultipleInputHandler = ({ field, activeResult, editable }: MultipleInputArgs): ReactNode => {
  const { t } = useTranslation("inventory");
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

  const handleUpdateValue = (index: number, key: string, newValue: string | Date): void => {
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

  const RecommendedField = observer(({ v, i }: RecommendedFieldArgs): ReactNode => {
    return (
      <>
        <Grid
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
                  data-test-id={`IdentifierRecommendedField-${field.key}-${i}`}
                />
              ) : (
                <TextField
                  sx={{
                    marginBottom:
                      // @ts-expect-error - subFields accepts field.value[i]
                      subFields(field.value[i]).length > 0 ? "10px" : "0px",
                  }}
                  size="small"
                  variant="standard"
                  fullWidth
                  id={`IdentifierRecommendedField-${field.key}-${i}`}
                  disabled={false}
                  value={v.value ?? ""}
                  placeholder={t("fields.identifiers.multipleInput.enterNewValue", { itemLabel })}
                  onChange={({ target: { value } }) => {
                    handleUpdateValue(i, "value", value);
                  }}
                  error={(editable && isDuplicate(String(v.value))) || isEmpty(String(v.value))}
                  helperText={
                    editable && isEmpty(String(v.value))
                      ? t("fields.identifiers.multipleInput.enterValueOrRemove")
                      : editable && isDuplicate(String(v.value))
                        ? t("fields.identifiers.multipleInput.duplicateValue")
                        : null
                  }
                  slotProps={{
                    inputLabel: { shrink: true },
                  }}
                />
              )}
              {
                // @ts-expect-error - subFields accepts field.value[i]
                subFields(field.value[i]).length > 0 &&
                  // @ts-expect-error - subFields accepts field.value[i]
                  subFields(field.value[i]).map((subField) => (
                    <InputWrapper
                      label={RECOMMENDED_FIELDS_LABELS[subField.key as keyof typeof RECOMMENDED_FIELDS_LABELS]}
                      key={subField.key}
                    >
                      <Grid
                        container
                        direction="row"
                        spacing={1}
                        sx={{
                          justifyContent: "space-between",
                          width: "95%",
                          m: 1,
                        }}
                      >
                        <Grid sx={{ flexGrow: 1 }}>
                          <TextField
                            size="small"
                            variant="standard"
                            fullWidth
                            id={`IdentifierRecommendedSubField-${subField.key}-${i}`}
                            disabled={false}
                            value={subField.value ?? ""}
                            placeholder={t("fields.identifiers.multipleInput.enterValueFor", {
                              fieldLabel:
                                RECOMMENDED_FIELDS_LABELS[subField.key as keyof typeof RECOMMENDED_FIELDS_LABELS],
                            })}
                            onChange={({ target: { value } }) => {
                              handleUpdateValue(i, subField.key, value);
                            }}
                            /* value is optional for most subFields, not all */
                            error={isEmpty(String(subField.value)) && isRequired(subField.key)}
                            helperText={
                              isEmpty(String(subField.value)) && isRequired(subField.key)
                                ? t("fields.identifiers.multipleInput.valueRequired")
                                : null
                            }
                            slotProps={{
                              inputLabel: { shrink: true },
                            }}
                          />
                        </Grid>
                      </Grid>
                    </InputWrapper>
                  ))
              }
            </FormControl>
          ) : (
            <>
              <Grid>{v.value instanceof Date ? v.value.toISOString().split("T")[0] : String(v.value)}</Grid>
              {
                // @ts-expect-error - subFields accepts field.value[i]
                subFields(field.value[i]).length > 0 &&
                  // @ts-expect-error - subFields accepts field.value[i]
                  subFields(field.value[i]).map((sf) => (
                    <Grid container direction="row" key={sf.key} spacing={1} sx={{ margin: "8px" }}>
                      <Grid sx={{ minWidth: "150px" }}>
                        {RECOMMENDED_FIELDS_LABELS[sf.key as keyof typeof RECOMMENDED_FIELDS_LABELS]}
                        {":"}
                      </Grid>
                      <Grid>
                        {sf.value ? (
                          String(sf.value)
                        ) : (
                          <Typography variant="inherit" component="em" sx={{ color: "#949494" }}>
                            {t("fields.identifiers.wrapper.none")}
                          </Typography>
                        )}
                      </Grid>
                    </Grid>
                  ))
              }
            </>
          )}
        </Grid>
        {field.options && (
          <Grid sx={{ minWidth: "135px" }}>
            {editable ? (
              <FormControl>
                <Select
                  variant="standard"
                  data-test-id={`${field.key}-option-selector`}
                  // @ts-expect-error - field.value[i].type exists
                  value={field.value[i].type}
                  onChange={({ target: { value } }) => handleUpdateValue(i, "type", value as string)}
                  inputProps={{
                    "aria-label": field.selectLabelLabel ?? "",
                  }}
                >
                  {field.options.map((option) => (
                    <MenuItem key={option.value} value={option.value} data-test-id={`field-option-${option.value}`}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            ) : (
              // biome-ignore lint/suspicious/noExplicitAny: field.value is an untyped array (see @ts-expect-error usages elsewhere in this file)
              capitaliseJustFirstChar((field.value as any)[i].type.toLowerCase())
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
              ? t("fields.identifiers.multipleInput.addNew", { itemLabel })
              : t("fields.identifiers.multipleInput.addEditFirst", { itemLabel })
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
            spacing={1}
            sx={{
              justifyContent: "space-between",
              flexWrap: "nowrap",
              width: "100%",
              m: 0.5,
            }}
            key={i}
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
            <Grid sx={{ marginRight: "6px" }}>
              <RemoveButton
                disabled={!editable}
                title={
                  editable
                    ? t("fields.identifiers.multipleInput.removeThis", { itemLabel })
                    : t("fields.identifiers.multipleInput.removeEditFirst", { itemLabel })
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

export default observer(MultipleInputHandler) as ComponentType<MultipleInputArgs>;
