import React, { useEffect } from "react";
import { makeStyles } from "tss-react/mui";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import { Clear, FilterList } from "@mui/icons-material";
import Autocomplete from "@mui/material/Autocomplete";
import { stableSort } from "../../util/table";
import Grid from "@mui/material/Grid";
import DateField2 from "../../components/Inputs/DateField";
import { truncateIsoTimestamp } from "../../util/conversions";

const useStyles = makeStyles()(() => ({
  button: {
    marginTop: "0.4em",
    marginBottom: "0.7em",
    boxShadow: "none",
    "&:hover": {
      boxShadow: "none",
    },
    "&:focus": {
      boxShadow: "none",
    },
  },
  buttonMargin: {
    marginLeft: "0.65em",
  },
}));

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
}) {
  const { classes } = useStyles();
  const [valid, setValid] = React.useState({});

  useEffect(resetValidity, []);

  function resetValidity() {
    setValid(
      Object.keys(filter).reduce((acc, key) => {
        acc[key] = true;
        return acc;
      }, {})
    );
  }

  function handleClear() {
    setFilter(
      Object.entries(filter).reduce(
        (acc, [key, config]) => ({
          ...acc,
          [key]: {
            ...config,
            value: "",
          },
        }),
        {}
      )
    );
    setFilterMultiReq(
      Object.entries(filterMultiReq).reduce(
        (acc, [key, config]) => ({
          ...acc,
          [key]: {
            ...config,
            value: "",
          },
        }),
        {}
      )
    );
    setFilterSpecial(
      Object.entries(filterSpecial).reduce(
        (acc, [key, config]) => ({
          ...acc,
          [key]: {
            ...config,
            value: config.defaultValue,
          },
        }),
        {}
      )
    );
    setFilterCounter(filterCounter + 1);
    resetValidity();
  }

  function handleFilterChange(event, key, config) {
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

  function DateField({ name, config }) {
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
          const dateValue = date
            ? truncateIsoTimestamp(date, "date").orElse(null)
            : null;

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

  function SelectField({ name, config, enumObj, ...rest }) {
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
        <Grid item xs={2}>
          <SelectField name="animal_type" config={filterSpecial.animal_type} />
        </Grid>

        <Grid item xs={2}>
          <SelectField
            multiple={true}
            name="animal_state"
            config={filterSpecial.animal_state}
          />
        </Grid>

        <Grid item xs={2}>
          <SelectField name="sex" config={filterSpecial.sex} />
        </Grid>

        <Grid item xs={2}>
          <DateField
            name="birth_date_from"
            config={filterSpecial.birth_date_from}
          />
        </Grid>
        <Grid item xs={2}>
          <DateField
            name="birth_date_to"
            config={filterSpecial.birth_date_to}
          />
        </Grid>
        <Grid item xs={2}>
          <DateField
            name="sacrifice_date_from"
            config={filterSpecial.sacrifice_date_from}
          />
        </Grid>
        <Grid item xs={2}>
          <DateField
            name="sacrifice_date_to"
            config={filterSpecial.sacrifice_date_to}
          />
        </Grid>

        {Object.entries(filter).map(([key, config]) => (
          <Grid item xs={2} key={key}>
            <TextField
              className={classes.input}
              label={config.label}
              variant="outlined"
              value={config.value}
              inputProps={
                config.type === "number" ? { inputMode: "numeric" } : {}
              }
              error={!valid[key]}
              helperText={!valid[key] ? "Should be an integer" : null}
              onChange={(event) => handleFilterChange(event, key, config)}
              fullWidth
            />
          </Grid>
        ))}

        <Grid item xs={2}>
          <SelectField name="building_id" config={filterSpecial.building_id} />
        </Grid>

        {Object.entries(filterMultiReq).map(([key, config]) => (
          <Grid item key={key} xs={2}>
            <FormControl fullWidth>
              <Autocomplete
                options={stableSort(Object.values(config.enumObj), (a, b) =>
                  a.label.localeCompare(b.label)
                )}
                renderInput={(props) => (
                  <TextField
                    variant="outlined"
                    {...props}
                    label={config.label}
                  />
                )}
                getOptionLabel={(option) => {
                  if (typeof option === "string") return option; // this is for when nothing is selected
                  return option.label;
                }}
                className={classes.input}
                label={config.label}
                loading={Object.values(config.enumObj).length === 0}
                margin="dense"
                value={config.value}
                onOpen={() => onOptionsFilterChange(key, "*")}
                onInputChange={(event, input, reason) => {
                  if (reason === "input")
                    onOptionsFilterChange(key, `*${input}*`);
                }}
                onChange={(event, value) => {
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
        endIcon={<Clear />}
        className={classes.button}
        onClick={handleClear}
      >
        Clear
      </Button>
      <Button
        variant="contained"
        color="primary"
        endIcon={<FilterList />}
        className={`${classes.button} ${classes.buttonMargin}`}
        onClick={() => setFilterCounter(filterCounter + 1)}
      >
        Filter
      </Button>
    </>
  );
}
