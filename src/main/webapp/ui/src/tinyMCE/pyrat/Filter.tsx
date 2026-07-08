import ClearIcon from "@mui/icons-material/Clear";
import FilterListIcon from "@mui/icons-material/FilterList";
import Autocomplete from "@mui/material/Autocomplete";
import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import Grid from "@mui/material/Grid";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import TextField from "@mui/material/TextField";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import DateField2 from "../../components/Inputs/DateField";
import { truncateIsoTimestamp } from "../../stores/definitions/Units";

export default function Filter({
  filter,
  setFilter,
  filterMultiReq,
  setFilterMultiReq,
  filterSpecial,
  setFilterSpecial,
  filterCounter,
  setFilterCounter,

  // With autocomplete fields, the user has the ability to filter the available
  // options, triggering this event.
  onOptionsFilterChange,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  [key: string]: any;
}) {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [valid, setValid] = React.useState<Record<string, any>>({});
  const { t } = useTranslation(["apps", "common"]);

  useEffect(resetValidity, []);

  function resetValidity() {
    setValid(
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      Object.keys(filter).reduce((acc: Record<string, any>, key: string) => {
        acc[key] = true;
        return acc;
      }, {}),
    );
  }

  function handleClear() {
    setFilter(
      Object.entries(filter).reduce(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        (acc: Record<string, any>, [key, config]: [string, any]) => ({
          // biome-ignore lint/performance/noAccumulatingSpread: initial biome migration
          ...acc,
          [key]: {
            ...config,
            value: "",
          },
        }),
        {},
      ),
    );
    setFilterMultiReq(
      Object.entries(filterMultiReq).reduce(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        (acc: Record<string, any>, [key, config]: [string, any]) => ({
          // biome-ignore lint/performance/noAccumulatingSpread: initial biome migration
          ...acc,
          [key]: {
            ...config,
            value: "",
          },
        }),
        {},
      ),
    );
    setFilterSpecial(
      Object.entries(filterSpecial).reduce(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        (acc: Record<string, any>, [key, config]: [string, any]) => ({
          // biome-ignore lint/performance/noAccumulatingSpread: initial biome migration
          ...acc,
          [key]: {
            ...config,
            value: config.defaultValue,
          },
        }),
        {},
      ),
    );
    setFilterCounter(filterCounter + 1);
    resetValidity();
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function handleFilterChange(event: any, key: any, config: any) {
    const number = event.target.value;
    let isValid = true;

    if (config.type === "number") {
      isValid = /^[0-9]*$/.test(number);
      setValid({ ...valid, [key]: isValid });
    }

    if (isValid) {
      setFilter({
        ...filter,
        [key]: {
          label: config.label,
          type: config.type,
          value: number,
        },
      });
    }
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function DateField({ name, config }: { name: any; config: any }) {
    return (
      <DateField2
        value={config.value}
        label={config.label}
        variant="outlined"
        disableWidthLimit
        // return (
        //   <DatePicker
        //     className={classes.input}
        //     label={config.label}
        //     inputVariant="outlined"
        //     margin="dense"
        //     format="yyyy-MM-dd"
        //     clearable
        //     value={config.value}
        onChange={({ target: { value: date } }) => {
          const dateValue = date ? truncateIsoTimestamp(date, "date").orElse(null) : null;

          // If current date field is '...-from', we will automatically set the '...-to'
          // field as well, to reduce the time it takes to find the to date
          if (name.endsWith("from")) {
            const toName = name.replace("from", "to");

            setFilterSpecial({
              ...filterSpecial,
              [toName]: {
                ...filterSpecial[toName],
                value: dateValue,
              },
              [name]: {
                ...filterSpecial[name],
                value: dateValue,
              },
            });
          } else {
            setFilterSpecial({
              ...filterSpecial,
              [name]: {
                ...filterSpecial[name],
                value: dateValue,
              },
            });
          }
        }}
      />
    );
  }

  function SelectField({
    name,
    config,
    enumObj,
    ...rest
  }: {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    name?: any;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    config?: any;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    enumObj?: any;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    [key: string]: any;
  }) {
    return (
      <FormControl fullWidth>
        <InputLabel>{config.label}</InputLabel>
        <Select
          value={config.value}
          onChange={(event) =>
            setFilterSpecial({
              ...filterSpecial,
              [name]: {
                ...filterSpecial[name],
                value: event.target.value,
              },
            })
          }
          label={config.label}
          {...rest}
        >
          {Object.keys(config.enumObj).map((value) => (
            <MenuItem key={value} value={config.enumObj[value]}>
              {value}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
    );
  }

  return (
    <>
      <Grid container direction="row" spacing={1}>
        <Grid size={2}>
          <SelectField name="animal_type" config={filterSpecial.animal_type} />
        </Grid>

        <Grid size={2}>
          <SelectField multiple={true} name="animal_state" config={filterSpecial.animal_state} />
        </Grid>

        <Grid size={2}>
          <SelectField name="sex" config={filterSpecial.sex} />
        </Grid>

        <Grid size={2}>
          <DateField name="birth_date_from" config={filterSpecial.birth_date_from} />
        </Grid>
        <Grid size={2}>
          <DateField name="birth_date_to" config={filterSpecial.birth_date_to} />
        </Grid>
        <Grid size={2}>
          <DateField name="sacrifice_date_from" config={filterSpecial.sacrifice_date_from} />
        </Grid>
        <Grid size={2}>
          <DateField name="sacrifice_date_to" config={filterSpecial.sacrifice_date_to} />
        </Grid>

        {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
        {Object.entries(filter).map(([key, config]: [string, any]) => (
          <Grid key={key} size={2}>
            <TextField
              label={config.label}
              variant="outlined"
              value={config.value}
              error={!valid[key]}
              helperText={!valid[key] ? t("pyrat.filter.integerValidation") : null}
              onChange={(event) => handleFilterChange(event, key, config)}
              fullWidth
              slotProps={{
                htmlInput: config.type === "number" ? { inputMode: "numeric" } : {},
              }}
            />
          </Grid>
        ))}

        <Grid size={2}>
          <SelectField name="building_id" config={filterSpecial.building_id} />
        </Grid>

        {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
        {Object.entries(filterMultiReq).map(([key, config]: [string, any]) => (
          <Grid key={key} size={2}>
            <FormControl fullWidth>
              <Autocomplete
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                options={Object.values(config.enumObj).toSorted((a: any, b: any) => a.label.localeCompare(b.label))}
                renderInput={(props) => <TextField variant="outlined" {...props} label={config.label} />}
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                getOptionLabel={(option: any) => {
                  if (typeof option === "string") return option; // this is for when nothing is selected
                  return option.label;
                }}
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                {...({ label: config.label, margin: "dense" } as any)}
                loading={Object.values(config.enumObj).length === 0}
                value={config.value}
                onOpen={() => onOptionsFilterChange(key, "*")}
                onInputChange={(_event, input, reason) => {
                  if (reason === "input") onOptionsFilterChange(key, `*${input}*`);
                }}
                onChange={(_event, value) => {
                  setFilterMultiReq({
                    ...filterMultiReq,
                    [key]: {
                      ...filterMultiReq[key],
                      value,
                    },
                  });
                }}
                fullWidth
              />
            </FormControl>
          </Grid>
        ))}
      </Grid>
      <Button
        variant="contained"
        endIcon={<ClearIcon />}
        sx={{
          mt: "0.4em",
          mb: "0.7em",
          boxShadow: "none",
          "&:hover": {
            boxShadow: "none",
          },
          "&:focus": {
            boxShadow: "none",
          },
        }}
        onClick={handleClear}
      >
        {t("common:actions.clear")}
      </Button>
      <Button
        variant="contained"
        color="primary"
        endIcon={<FilterListIcon />}
        sx={{
          mt: "0.4em",
          mb: "0.7em",
          ml: "0.65em",
          boxShadow: "none",
          "&:hover": {
            boxShadow: "none",
          },
          "&:focus": {
            boxShadow: "none",
          },
        }}
        onClick={() => setFilterCounter(filterCounter + 1)}
      >
        {t("pyrat.filter.label")}
      </Button>
    </>
  );
}
