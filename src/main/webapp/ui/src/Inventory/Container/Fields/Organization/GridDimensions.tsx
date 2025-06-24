import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import NumberField from "../../../../components/Inputs/NumberField";
import React, { useState } from "react";
import useStores from "../../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import FormHelperText from "@mui/material/FormHelperText";
import ContainerModel from "../../../../stores/models/ContainerModel";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import Select, { SelectChangeEvent } from "@mui/material/Select";
import Divider from "@mui/material/Divider";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import { mapObject } from "../../../../util/Util";

const minGridSize = 1;
const maxGridSize = 24;

const errorMessage = `Must be at least ${minGridSize}, and not more than ${maxGridSize}.`;

type CommonSizeIndex = string;
type CommonSize = {
  rows: number | null;
  cols: number | null;
  name: string;
};

// the order of these key/value pairs is significant
const commonSizes: Record<CommonSizeIndex, CommonSize> = {
  custom: { rows: null, cols: null, name: "Custom" },
  "64-place-freezer-box": { rows: 8, cols: 8, name: "64 place freezer box" },
  "81-place-freezer-box": { rows: 9, cols: 9, name: "81 place freezer box" },
  "100-place-freezer-box": {
    rows: 10,
    cols: 10,
    name: "100 place freezer box",
  },
  "169-place-freezer-box": {
    rows: 13,
    cols: 13,
    name: "169 place freezer box",
  },
  "196-place-freezer-box": {
    rows: 14,
    cols: 14,
    name: "196 place freezer box",
  },
  "6-well-plate": { rows: 2, cols: 3, name: "6 well plate" },
  "12-well-plate": { rows: 3, cols: 4, name: "12 well plate" },
  "24-well-plate": { rows: 4, cols: 6, name: "24 well plate" },
  "96-well-plate": { rows: 8, cols: 12, name: "96 well plate" },
};

function GridDimensions(): React.ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();

  const [validColumns, setValidColumns] = useState(true);
  const [validRows, setValidRows] = useState(true);
  const [commonSize, setCommonSize] = useState<CommonSizeIndex>("custom");

  if (!activeResult || !(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const gridLayout = activeResult.gridLayout;
  if (activeResult.cType !== "GRID" || !gridLayout)
    throw new Error("Container must be a Grid Container");

  const [columnsNumber, setColumnsNumber] = useState<number>(1);

  const handleChangeColumns = ({
    target,
  }: {
    target: { value: string; checkValidity: () => boolean };
  }) => {
    setCommonSize("custom");
    setColumnsNumber(parseInt(target.value, 10));
    if (!target.checkValidity() || target.value === "") {
      setValidColumns(false);
      activeResult.setAttributesDirty({
        gridLayout: { ...gridLayout, columnsNumber: "" },
      });
    } else {
      setValidColumns(true);
      const newNumber = parseInt(target.value, 10);
      activeResult.setAttributesDirty({
        gridLayout: {
          ...gridLayout,
          columnsNumber: newNumber,
        },
      });
    }
  };

  const [rowsNumber, setRowsNumber] = useState(1);

  const handleChangeRows = ({
    target,
  }: {
    target: { value: string; checkValidity: () => boolean };
  }) => {
    setCommonSize("custom");
    setRowsNumber(parseInt(target.value, 10));
    if (!target.checkValidity() || target.value === "") {
      setValidRows(false);
      activeResult.setAttributesDirty({
        gridLayout: { ...gridLayout, rowsNumber: "" },
      });
    } else {
      setValidRows(true);
      const newNumber = parseInt(target.value, 10);
      activeResult.setAttributesDirty({
        gridLayout: {
          ...gridLayout,
          rowsNumber: newNumber,
        },
      });
    }
  };

  const handleChooseCommonSize = (
    event: SelectChangeEvent<CommonSizeIndex>
  ) => {
    const value = event.target.value;
    const size = commonSizes[value];
    setCommonSize(value);
    setValidColumns(true);
    setValidRows(true);
    if (size.rows) setRowsNumber(size.rows);
    if (size.cols) setColumnsNumber(size.cols);
    activeResult.setAttributesDirty({
      gridLayout: {
        ...gridLayout,
        columnsNumber: size.cols,
        rowsNumber: size.rows,
      },
    });
  };

  const editable = activeResult.isFieldEditable("organization");
  return (
    <InputWrapper label="Grid Dimensions">
      <Box mt={1} mb={0.5}>
        <Typography variant="body2">Choose a common size</Typography>
      </Box>

      <Grid item xs={12} sm={8} md={8} xl={6}>
        <FormControl fullWidth size="small">
          <Select value={commonSize} label="" onChange={handleChooseCommonSize}>
            {Object.values(
              mapObject(
                (value, size) => (
                  <MenuItem value={value} key={value}>
                    {size.name}
                  </MenuItem>
                ),
                commonSizes
              )
            )}
          </Select>
        </FormControl>
      </Grid>

      <Box mt={1} mb={0.5}>
        <Typography variant="body2">Or specify the dimensions</Typography>
      </Box>

      <Grid container spacing={1}>
        <Grid item xs={6} sm={5} md={6} lg={4} xl={3}>
          <NumberField
            fullWidth
            disabled={!editable}
            value={rowsNumber}
            onChange={handleChangeRows}
            size="small"
            variant="outlined"
            error={!validRows}
            ariaLabel="rows"
            inputProps={{
              max: maxGridSize,
              min: minGridSize,
              step: 1,
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">Rows</InputAdornment>
              ),
            }}
          />
          <FormHelperText component="div" error>
            {!validRows ? errorMessage : null}
          </FormHelperText>
        </Grid>

        <Grid item xs={6} sm={5} md={6} lg={4} xl={3}>
          <NumberField
            fullWidth
            disabled={!editable}
            value={columnsNumber}
            onChange={handleChangeColumns}
            size="small"
            variant="outlined"
            error={!validColumns}
            ariaLabel="columns"
            inputProps={{
              max: maxGridSize,
              min: minGridSize,
              step: 1,
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">Columns</InputAdornment>
              ),
            }}
          />
          <FormHelperText component="div" error>
            {!validColumns ? errorMessage : null}
          </FormHelperText>
        </Grid>
      </Grid>
      <Divider orientation="horizontal" sx={{ mt: 1.5, mb: 1 }} />
    </InputWrapper>
  );
}

export default observer(GridDimensions);
