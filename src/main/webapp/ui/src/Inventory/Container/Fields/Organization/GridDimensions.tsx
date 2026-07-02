import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import Typography from "@mui/material/Typography";
import { mapValues } from "es-toolkit";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import NumberField from "../../../../components/Inputs/NumberField";
import ContainerModel from "../../../../stores/models/ContainerModel";
import useStores from "../../../../stores/use-stores";

const minGridSize = 1;
const maxGridSize = 24;

type CommonSizeIndex = string;
type CommonSizeNameKey =
  | "container.fields.gridDimensions.commonSizes.custom"
  | "container.fields.gridDimensions.commonSizes.freezerBox64"
  | "container.fields.gridDimensions.commonSizes.freezerBox81"
  | "container.fields.gridDimensions.commonSizes.freezerBox100"
  | "container.fields.gridDimensions.commonSizes.freezerBox169"
  | "container.fields.gridDimensions.commonSizes.freezerBox196"
  | "container.fields.gridDimensions.commonSizes.wellPlate6"
  | "container.fields.gridDimensions.commonSizes.wellPlate12"
  | "container.fields.gridDimensions.commonSizes.wellPlate24"
  | "container.fields.gridDimensions.commonSizes.wellPlate96";
type CommonSize = {
  rows: number | null;
  cols: number | null;
  nameKey: CommonSizeNameKey;
};

// Declaration order is the menu display order: the most popular size (96 well
// plate) first, "Custom" last.
const commonSizes: Record<CommonSizeIndex, CommonSize> = {
  "96-well-plate": { rows: 8, cols: 12, nameKey: "container.fields.gridDimensions.commonSizes.wellPlate96" },
  "24-well-plate": { rows: 4, cols: 6, nameKey: "container.fields.gridDimensions.commonSizes.wellPlate24" },
  "12-well-plate": { rows: 3, cols: 4, nameKey: "container.fields.gridDimensions.commonSizes.wellPlate12" },
  "6-well-plate": { rows: 2, cols: 3, nameKey: "container.fields.gridDimensions.commonSizes.wellPlate6" },
  "196-place-freezer-box": {
    rows: 14,
    cols: 14,
    nameKey: "container.fields.gridDimensions.commonSizes.freezerBox196",
  },
  "169-place-freezer-box": {
    rows: 13,
    cols: 13,
    nameKey: "container.fields.gridDimensions.commonSizes.freezerBox169",
  },
  "100-place-freezer-box": {
    rows: 10,
    cols: 10,
    nameKey: "container.fields.gridDimensions.commonSizes.freezerBox100",
  },
  "81-place-freezer-box": { rows: 9, cols: 9, nameKey: "container.fields.gridDimensions.commonSizes.freezerBox81" },
  "64-place-freezer-box": { rows: 8, cols: 8, nameKey: "container.fields.gridDimensions.commonSizes.freezerBox64" },
  custom: { rows: null, cols: null, nameKey: "container.fields.gridDimensions.commonSizes.custom" },
};

function GridDimensions(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();

  const [validColumns, setValidColumns] = useState(true);
  const [validRows, setValidRows] = useState(true);
  const [commonSize, setCommonSize] = useState<CommonSizeIndex>("custom");

  if (!activeResult || !(activeResult instanceof ContainerModel)) throw new Error("ActiveResult must be a Container");
  const gridLayout = activeResult.gridLayout;
  if (activeResult.cType !== "GRID" || !gridLayout) throw new Error("Container must be a Grid Container");
  const errorMessage = t("container.fields.gridDimensions.error", { max: maxGridSize, min: minGridSize });

  const [columnsNumber, setColumnsNumber] = useState<number>(1);

  const handleChangeColumns = ({ target }: { target: { value: string; checkValidity: () => boolean } }) => {
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

  const handleChangeRows = ({ target }: { target: { value: string; checkValidity: () => boolean } }) => {
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

  const handleChooseCommonSize = (event: SelectChangeEvent<CommonSizeIndex>) => {
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
    <InputWrapper label={t("container.fields.gridDimensions.label")} flexWrap="nowrap">
      <Box sx={{ mt: 1, mb: 0.5 }}>
        <Typography variant="body2">{t("container.fields.gridDimensions.chooseCommonSize")}</Typography>
      </Box>
      <Grid
        size={{
          xs: 12,
          sm: 8,
          md: 8,
          xl: 6,
        }}
      >
        <FormControl fullWidth size="small">
          <Select value={commonSize} label="" onChange={handleChooseCommonSize}>
            {Object.values(
              mapValues(commonSizes, (size, value) => (
                <MenuItem value={value} key={value}>
                  {t(size.nameKey)}
                </MenuItem>
              )),
            )}
          </Select>
        </FormControl>
      </Grid>
      <Box sx={{ mt: 1, mb: 0.5 }}>
        <Typography variant="body2">{t("container.fields.gridDimensions.specifyDimensions")}</Typography>
      </Box>
      <Grid container spacing={1}>
        <Grid
          size={{
            xs: 6,
            sm: 5,
            md: 6,
            lg: 4,
            xl: 3,
          }}
        >
          <NumberField
            fullWidth
            disabled={!editable}
            value={rowsNumber}
            onChange={handleChangeRows}
            size="small"
            variant="outlined"
            error={!validRows}
            slotProps={{
              htmlInput: {
                "aria-label": t("container.fields.gridDimensions.rows"),
                max: maxGridSize,
                min: minGridSize,
                step: 1,
              },
              input: {
                startAdornment: (
                  <InputAdornment position="start">{t("container.fields.gridDimensions.rows")}</InputAdornment>
                ),
              },
            }}
          />
          <FormHelperText component="div" error>
            {!validRows ? errorMessage : null}
          </FormHelperText>
        </Grid>

        <Grid
          size={{
            xs: 6,
            sm: 5,
            md: 6,
            lg: 4,
            xl: 3,
          }}
        >
          <NumberField
            fullWidth
            disabled={!editable}
            value={columnsNumber}
            onChange={handleChangeColumns}
            size="small"
            variant="outlined"
            error={!validColumns}
            slotProps={{
              htmlInput: {
                "aria-label": t("container.fields.gridDimensions.columns"),
                max: maxGridSize,
                min: minGridSize,
                step: 1,
              },
              input: {
                startAdornment: (
                  <InputAdornment position="start">{t("container.fields.gridDimensions.columns")}</InputAdornment>
                ),
              },
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
