import React from "react";
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
import Collapse from "@mui/material/Collapse";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";

/*
 * The create dialog allows users to create new Inventory records with respect
 * to an existing one; from splitting subsamples into multiple smaller
 * subsamples, to creating new containers directly inside of existing
 * containers, to defining new templates to easily re-create a complex sample.
 *
 * The module contains both the dialog itself as well as a series of smaller
 * components that whilst they are not exported their use is configured
 * externally: each model class that implements InventoryRecord defines a set
 * of options that are presented by this dialog; each option defines a series
 * of steps, each with a reference to a form component that is defined here.
 * These configured options provide explanative text, validation logic, and
 * ultimately provide the code that is run when the dialog is submitted --
 * wherein either a network call is made to create the new object or the user
 * is redirect to a part of the UI where they can further customised the new
 * record.
 */

type CreateDialogProps = {
  existingRecord: CreateFrom & InventoryRecord;
  open: boolean;
  onClose: () => void;
};

const Name = observer(
  ({
    id,
    state,
  }: {
    id: string;
    state: { value: string };
  }): React.ReactNode => {
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
  }
);

const Fields = observer(
  ({
    id: _id,
    state,
  }: {
    id: string;
    state: {
      copyFieldContent: ReadonlyArray<{
        id: Id;
        name: string;
        content: string;
        hasContent: boolean;
        selected: boolean;
      }>;
    };
  }): React.ReactNode => {
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
                        f.selected = checked && f.hasContent;
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
  }
);

const SplitCount = observer(
  ({
    id,
    state,
  }: {
    id: string;
    state: { copies: number };
  }): React.ReactNode => {
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
  }
);

const LocationPicker = observer(
  ({ id: _id, state }: { id: string; state: { container: Container } }) => {
    const search = React.useMemo(() => {
      const s = new Search({
        fetcherParams: {
          parentGlobalId: state.container.globalId,
        },
        uiConfig: {
          allowedSearchModules: new Set([]) as AllowedSearchModules,
          allowedTypeFilters: new Set([]) as AllowedTypeFilters,
          hideContentsOfChip: true,
          selectionLimit: 1,
          onlyAllowSelectingEmptyLocations: true,
        },
        factory: new AlwaysNewFactory(),
      });
      void s.setSearchView(cTypeToDefaultSearchView(state.container.cType));
      runInAction(() => {
        s.alwaysFilterOut = () => true;
      });
      return s;
      /*
       * You might think that this useMemo should run whenever `state.container`
       * changes, after all `state` is an observable value and when it changes so
       * should the UI. However, the only way to change `state.container` is to
       * close the create dialog and open it again with a different container
       * selected. Not only will this unmount the whole dialog including this form
       * field but even just changing the selected option (i.e. choosing to create
       * a sample inside the container instead of another container) will result in
       * an unmounting and remounting.
       */
    }, []);

    /*
     * If the container has locations but the details have not been fetched,
     * then fetch them before rendering otherwise the container will show as
     * empty.
     */
    React.useEffect(() => {
      if (
        state.container.locationsCount > 0 &&
        state.container.locations?.length === 0
      )
        void state.container.fetchAdditionalInfo();
    }, [state.container]);

    if (state.container.cType === "LIST") return null;
    if (
      state.container.locationsCount > 0 &&
      state.container.locations?.length === 0
    )
      return null;
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
  }
);

const NewSubsampleCount = observer(
  ({
    id,
    state,
  }: {
    id: string;
    state: { count: number };
  }): React.ReactNode => {
    return (
      <Box>
        <FormControl>
          <NumberField
            id={id}
            name="count"
            autoFocus
            value={state.count}
            onChange={({ target }) => {
              runInAction(() => {
                state.count = parseInt(target.value, 10);
              });
            }}
            variant="outlined"
            size="small"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">Count</InputAdornment>
              ),
            }}
            inputProps={{
              min: 1,
              max: 100,
              step: 1,
            }}
          />
        </FormControl>
      </Box>
    );
  }
);

const NewSubsampleQuantity = observer(
  ({
    id,
    state,
  }: {
    id: string;
    state: { quantity: number | ""; quantityLabel: string };
  }) => {
    return (
      <Box>
        <FormControl>
          <NumberField
            id={id}
            name="quantity"
            autoFocus
            value={state.quantity}
            error={state.quantity === ""}
            onChange={({ target }) => {
              runInAction(() => {
                /*
                 * The saved value can be either a number or an empty string,
                 * which is just to allow for the field to be temporarily cleared
                 * whilst entering a different number. The should should be
                 * prohibited from submitting if the field is empty.
                 */
                const newValue = parseFloat(target.value);
                if (target.checkValidity())
                  state.quantity = isNaN(newValue) ? "" : newValue;
              });
            }}
            variant="outlined"
            size="small"
            InputProps={{
              endAdornment: (
                <InputAdornment position="end">
                  {state.quantityLabel}
                </InputAdornment>
              ),
            }}
            inputProps={{
              min: 0,
              step: 0.001,
            }}
          />
        </FormControl>
      </Box>
    );
  }
);

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
  }: CreateOptionParameter & {
    activeStep: number;
    setActiveStep: (step: number) => void;
    showNextButton: boolean;
  }) => {
    const fieldId = React.useId();
    return (
      <>
        {/*
         * We animate in as soon as the paramter is mounted but unfortunately
         * there's no easy way to animate out when unmounted.
         */}
        <Collapse in appear>
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
              <Grid
                container
                /*
                 * This nowrap is a result of a very odd bug. Without it, across
                 * various browsers and devices, the "Back" button below would be
                 * rendered on top of the grid when LocationPicker is rendered for
                 * grid containers. For some reason, the `div.MuiGrid-item` wrapping
                 * the table would have a fixed height despite there not being such
                 * styles applied. The `div.MuiTableContainer-root` within would have
                 * the expected height but the next `div.MuiGrid-item` containing the
                 * "Back" button would be rendered over it. No idea, very strange,
                 * but this does fix it.
                 */
                flexWrap="nowrap"
                direction="column"
                spacing={1}
              >
                <Grid item>
                  {state.key === "split" && (
                    <SplitCount id={fieldId} state={state} />
                  )}
                  {state.key === "name" && <Name id={fieldId} state={state} />}
                  {state.key === "location" && (
                    <LocationPicker id={fieldId} state={state} />
                  )}
                  {state.key === "fields" && (
                    <Fields id={fieldId} state={state} />
                  )}
                  {state.key === "newSubsamplesCount" && (
                    <NewSubsampleCount id={fieldId} state={state} />
                  )}
                  {state.key === "newSubsamplesQuantity" && (
                    <NewSubsampleQuantity id={fieldId} state={state} />
                  )}
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
        </Collapse>
      </>
    );
  }
);

function CreateDialog({
  existingRecord,
  open,
  onClose,
}: CreateDialogProps): React.ReactNode {
  const [selectedCreateOptionIndex, setSelectedCreateOptionIndex] =
    React.useState<null | number>(null);
  const [activeStep, setActiveStep] = React.useState<number>(0);
  const [submitting, setSubmitting] = React.useState(false);
  const firstStepId = React.useId();
  const [loading, setLoading] = React.useState(true);
  const { addAlert } = React.useContext(AlertContext);

  /*
   * When the dialog opens, we fetch additional information about the existing
   * record, including most importantly in the case of samples, the fields. This
   * is necessary because the fields are not fetched by default when the record
   * is initially loaded and the create dialog needs them to copy them to the
   * new template.
   */
  React.useEffect(() => {
    if (open) {
      setLoading(true);
      existingRecord
        .fetchAdditionalInfo()
        .catch((error) => {
          addAlert(
            mkAlert({
              message: `Failed to load additional information: ${
                error instanceof Error ? error.message : String(error)
              }`,
              variant: "error",
            })
          );
          onClose();
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [open, existingRecord]);

  const handleSubmit = () => {
    void (async () => {
      if (selectedCreateOptionIndex === null)
        throw new Error("Cannot submit until an option is chosen");
      setSubmitting(true);
      try {
        await existingRecord.createOptions[
          selectedCreateOptionIndex
        ].onSubmit();
        handleClose();
      } finally {
        setSubmitting(false);
      }
    })();
  };

  const handleClose = () => {
    if (selectedCreateOptionIndex !== null)
      existingRecord.createOptions[selectedCreateOptionIndex].onReset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSubmit();
        }}
      >
        <DialogTitle>
          Create new items from <strong>{existingRecord.name}</strong>
          <HelpLinkIcon
            link={docLinks.createDialog}
            title="Info on creating new items."
          />
        </DialogTitle>
        <DialogContent>
          {loading ? (
            <Box sx={{ textAlign: "center", p: 2 }}>
              <Typography variant="body1">Loading...</Typography>
            </Box>
          ) : (
            <Stepper activeStep={activeStep} orientation="vertical">
              <Step>
                <StepLabel
                  optional={
                    selectedCreateOptionIndex !== null && (
                      <Button
                        variant="outlined"
                        sx={{ mt: 0.5 }}
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
                  <label
                    htmlFor={firstStepId}
                    style={{ fontSize: "1.1em", letterSpacing: "0.04em" }}
                  >
                    Type of item to create
                    {selectedCreateOptionIndex !== null && (
                      <Typography variant="body2">
                        {
                          existingRecord.createOptions[
                            selectedCreateOptionIndex
                          ].label
                        }
                      </Typography>
                    )}
                  </label>
                </StepLabel>
                {/*
                 * We disable the animation here when going back to the first
                 * step otherwise the UI is pretty janky when the user taps the
                 * "Back" or "Change" buttons: the parameter steps below
                 * immediately unmount but the slower animation of this step
                 * showing caused the content to jump up and down. By disabling
                 * the animation we avoid this jankiness.
                 *
                 * Various attempts were made to animate the parameters step
                 * closing but due to the way that the Stepper component works
                 * this was difficult. Keeping them in the DOM and hiding/showing
                 * them with Collapse instead of using conditional rendering
                 * resulted in the step numbers incrementing across the various
                 * options so that if the first option had two parameters the
                 * parameter for the second option would be displayed with a
                 * number 3.
                 */}
                <StepContent
                  transitionDuration={
                    selectedCreateOptionIndex === null ? 0 : 300
                  }
                >
                  <FormControl>
                    <RadioGroup
                      id={firstStepId}
                      value={selectedCreateOptionIndex}
                      onChange={(_event, index) => {
                        setSelectedCreateOptionIndex(parseInt(index, 10));
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
                      setActiveStep={(s) => {
                        setActiveStep(s);
                        if (s === 0) setSelectedCreateOptionIndex(null);
                      }}
                      showNextButton={
                        index <
                        (
                          existingRecord.createOptions[
                            selectedCreateOptionIndex
                          ].parameters ?? []
                        ).length -
                          1
                      }
                      key={index}
                    />
                  )
                )}
            </Stepper>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <SubmitSpinner
            label="Create"
            onClick={handleSubmit}
            disabled={
              submitting ||
              selectedCreateOptionIndex === null ||
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
            loading={submitting}
          />
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default observer(CreateDialog);
