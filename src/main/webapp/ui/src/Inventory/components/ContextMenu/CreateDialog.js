//@flow

import React, { type Node, type ComponentType } from "react";
import {
  type CreateFrom,
  type CreateOptionParameter,
  type InventoryRecord,
} from "../../../stores/definitions/InventoryRecord";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepContent from "@mui/material/StepContent";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import FormControlLabel from "@mui/material/FormControlLabel";
import RadioGroup from "@mui/material/RadioGroup";
import Radio from "@mui/material/Radio";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import {
  OptionHeading,
  OptionExplanation,
} from "../../../components/Inputs/RadioField";
import NumberField from "../../../components/Inputs/NumberField";
import InputAdornment from "@mui/material/InputAdornment";
import { observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import NoValue from "../../../components/NoValue";
import StringField from "../../../components/Inputs/StringField";
import { type Id } from "../../../stores/definitions/BaseRecord";
import Grid from "@mui/material/Grid";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import SearchView from "../../Search/SearchView";
import SearchContext from "../../../stores/contexts/Search";
import AlwaysNewWindowNavigationContext from "../../../components/AlwaysNewWindowNavigationContext";
import AlwaysNewFactory from "../../../stores/models/Factory/AlwaysNewFactory";
import {
  type AllowedSearchModules,
  type AllowedTypeFilters,
} from "../../../stores/definitions/Search";
import {
  type Container,
  cTypeToDefaultSearchView,
} from "../../../stores/definitions/Container";
import { menuIDs } from "../../../util/menuIDs";
import Search from "../../../stores/models/Search";

type CreateDialogProps = {|
  existingRecord: CreateFrom & InventoryRecord,
  open: boolean,
  onClose: () => void,
|};

const Name: ComponentType<{|
  id: string,
  state: { value: string },
|}> = observer(({ id, state }): Node => {
  return (
    <StringField
      id={id}
      value={state.value}
      onChange={({ target }) => {
        runInAction(() => {
          state.value = target.value;
        });
      }}
      variant="outlined"
    />
  );
});

const Fields: ComponentType<{|
  id: string,
  state: {
    copyFieldContent: $ReadOnlyArray<{|
      id: Id,
      name: string,
      content: string,
      hasContent: boolean,
      selected: boolean,
    |}>,
  },
|}> = observer(({ id: _id, state }): Node => {
  if (state.copyFieldContent.length === 0)
    return <NoValue label="No fields." />;
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell variant="head" padding="checkbox">
              <Checkbox
                indeterminate={
                  state.copyFieldContent.some(({ selected }) => selected) &&
                  !state.copyFieldContent.every(({ selected }) => selected)
                }
                checked={state.copyFieldContent.every(
                  ({ selected }) => selected
                )}
                onChange={({ target: { checked } }) => {
                  runInAction(() => {
                    state.copyFieldContent.forEach((f) => {
                      f.selected = checked;
                    });
                  });
                }}
              />
            </TableCell>
            <TableCell width="70%">Field</TableCell>
            <TableCell width="30%">Default Value</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {state.copyFieldContent.map((f) => (
            <TableRow key={f.id}>
              <TableCell variant="head" padding="checkbox">
                <Checkbox
                  checked={f.selected}
                  onChange={({ target: { checked } }) => {
                    runInAction(() => {
                      f.selected = checked;
                    });
                  }}
                  color="default"
                  disabled={!f.hasContent}
                />
              </TableCell>
              <TableCell>{f.name}</TableCell>
              <TableCell
                style={{
                  opacity: f.selected ? 1.0 : 0.3,
                }}
              >
                {f.content}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
});

const SplitCount: ComponentType<{|
  id: string,
  state: { copies: number, ... },
|}> = observer(({ id, state }): Node => {
  const MIN = 2;
  const MAX = 100;

  return (
    <Box>
      <FormControl>
        <NumberField
          id={id}
          name="copies"
          autoFocus
          value={state.copies}
          onChange={({ target }) => {
            runInAction(() => {
              state.copies = parseInt(target.value, 10);
            });
          }}
          variant="outlined"
          size="small"
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">Copies</InputAdornment>
            ),
          }}
          inputProps={{
            min: MIN,
            max: MAX,
            step: 1,
          }}
        />
      </FormControl>
    </Box>
  );
});

const LocationPicker: ComponentType<{|
  id: string,
  state: { container: Container },
|}> = observer(({ id: _id, state }): Node => {
  const [search] = React.useState<Search>(
    new Search({
      fetcherParams: {
        parentGlobalId: state.container.globalId,
      },
      uiConfig: {
        allowedSearchModules: (new Set([]): AllowedSearchModules),
        allowedTypeFilters: (new Set([]): AllowedTypeFilters),
        hideContentsOfChip: true,
        selectionLimit: 1,
        onlyAllowSelectingEmptyLocations: true,
      },
      factory: new AlwaysNewFactory(),
    })
  );

  // is there really no way to do this without a useEffect?
  React.useEffect(() => {
    void search.setSearchView(cTypeToDefaultSearchView(state.container.cType));
    runInAction(() => {
      search.alwaysFilterOut = () => true;
    });
  }, [state.container]);

  if (state.container.cType === "LIST") return null;
  return (
    <SearchContext.Provider
      value={{
        search,
        scopedResult: state.container,
        differentSearchForSettingActiveResult: search,
      }}
    >
      <AlwaysNewWindowNavigationContext>
        <SearchView contextMenuId={menuIDs.NONE} />
      </AlwaysNewWindowNavigationContext>
    </SearchContext.Provider>
  );
});

const ParameterField = observer(
  ({
    label,
    explanation,
    state,
    validState,
    activeStep,
    setActiveStep,
    showNextButton,
    ...rest
  }: {|
    ...CreateOptionParameter,
    activeStep: number,
    setActiveStep: (number) => void,
    showNextButton: boolean,
  |}) => {
    const fieldId = React.useId();
    return (
      <Step {...rest}>
        <StepLabel>
          <label
            htmlFor={fieldId}
            style={{ fontSize: "1.1em", letterSpacing: "0.04em" }}
          >
            {label}
          </label>
          <Typography variant="body2">{explanation}</Typography>
        </StepLabel>
        <StepContent>
          <Grid container direction="column" spacing={1}>
            <Grid item>
              {state.key === "split" && (
                <SplitCount id={fieldId} state={state} />
              )}
              {state.key === "name" && <Name id={fieldId} state={state} />}
              {state.key === "location" && (
                <LocationPicker id={fieldId} state={state} />
              )}
              {state.key === "fields" && <Fields id={fieldId} state={state} />}
            </Grid>
            <Grid item>
              <Stack spacing={1} direction="row">
                {showNextButton && (
                  <Button
                    color="primary"
                    variant="contained"
                    disableElevation
                    onClick={() => {
                      setActiveStep(activeStep + 1);
                    }}
                    disabled={!validState()}
                  >
                    Next
                  </Button>
                )}
                <Button
                  variant="outlined"
                  onClick={() => {
                    setActiveStep(activeStep - 1);
                  }}
                >
                  Back
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </StepContent>
      </Step>
    );
  }
);

function CreateDialog({
  existingRecord,
  open,
  onClose,
}: CreateDialogProps): Node {
  const [selectedCreateOptionIndex, setSelectedCreateOptionIndex] =
    React.useState<null | number>(null);
  const [activeStep, setActiveStep] = React.useState<number>(0);

  React.useEffect(() => {
    if (activeStep === 0) setSelectedCreateOptionIndex(null);
  }, [activeStep]);

  const handleSubmit = () => {
    void (async () => {
      if (!selectedCreateOptionIndex)
        throw new Error("Cannot submit until an option is chosen");
      await existingRecord.createOptions[selectedCreateOptionIndex].onSubmit();
      onClose();
    })();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        Create new items
        <HelpLinkIcon
          link={docLinks.createDialog}
          title="Info on creating new items."
        />
      </DialogTitle>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSubmit();
        }}
      >
        <DialogContent>
          <Stepper activeStep={activeStep} orientation="vertical">
            <Step>
              <StepLabel
                optional={
                  selectedCreateOptionIndex !== null && (
                    <Button
                      onClick={() => {
                        setSelectedCreateOptionIndex(null);
                        setActiveStep(0);
                      }}
                    >
                      Change
                    </Button>
                  )
                }
              >
                <span style={{ fontSize: "1.1em", letterSpacing: "0.04em" }}>
                  {selectedCreateOptionIndex
                    ? existingRecord.createOptions[selectedCreateOptionIndex]
                        .label
                    : "Create What?"}
                </span>
              </StepLabel>
              <StepContent>
                <FormControl>
                  <FormLabel sx={{ fontWeight: 400 }}>
                    What kind of record would you like to create from{" "}
                    <strong>{existingRecord.name}</strong>?
                  </FormLabel>
                  <RadioGroup
                    value={selectedCreateOptionIndex}
                    onChange={(_event, index) => {
                      setSelectedCreateOptionIndex(index);
                      setActiveStep(1);
                    }}
                  >
                    {existingRecord.createOptions.length === 0 && (
                      <NoValue label="No options available." />
                    )}
                    {existingRecord.createOptions.map(
                      ({ label, explanation, disabled }, index) => (
                        <FormControlLabel
                          key={index}
                          value={index}
                          control={
                            <Radio
                              sx={{
                                // align radio button with option heading
                                mb: "auto",
                                p: 0.5,
                                mr: 0.5,
                              }}
                            />
                          }
                          disabled={disabled}
                          label={
                            <>
                              <OptionHeading>{label}</OptionHeading>
                              <OptionExplanation>
                                {explanation}
                              </OptionExplanation>
                            </>
                          }
                          sx={{ mt: 2 }}
                        />
                      )
                    )}
                  </RadioGroup>
                </FormControl>
              </StepContent>
            </Step>
            {selectedCreateOptionIndex !== null &&
              existingRecord.createOptions[selectedCreateOptionIndex]
                .parameters &&
              existingRecord.createOptions[
                selectedCreateOptionIndex
              ].parameters.map(
                ({ label, explanation, state, validState }, index) => (
                  <ParameterField
                    label={label}
                    explanation={explanation}
                    state={state}
                    validState={validState}
                    activeStep={activeStep}
                    setActiveStep={setActiveStep}
                    showNextButton={
                      index <
                      (
                        existingRecord.createOptions[selectedCreateOptionIndex]
                          .parameters ?? []
                      ).length -
                        1
                    }
                    key={index}
                  />
                )
              )}
          </Stepper>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <SubmitSpinner
            label="Create"
            onClick={handleSubmit}
            disabled={
              !selectedCreateOptionIndex ||
              activeStep <
                (
                  existingRecord.createOptions[selectedCreateOptionIndex]
                    .parameters ?? []
                ).length ||
              (
                existingRecord.createOptions[selectedCreateOptionIndex]
                  .parameters ?? []
              ).some(({ validState }) => !validState())
            }
            loading={false}
          />
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default (observer(CreateDialog): typeof CreateDialog);
