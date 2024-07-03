//@flow

import React, {
  type Node,
  type ComponentType,
  useState,
  useEffect,
} from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import useStores from "../../stores/use-stores";
import docLinks from "../../assets/DocLinks";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import FormControl from "../../components/Inputs/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import FormControlLabel from "@mui/material/FormControlLabel";
import SubmitSpinner from "../../components/SubmitSpinnerButton";
import Typography from "@mui/material/Typography";
import { type InventoryRecord } from "../../stores/definitions/InventoryRecord";
import {
  type Location,
  cTypeToDefaultSearchView,
} from "../../stores/definitions/Container";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import ContainerModel from "../../stores/models/ContainerModel";
import SampleModel from "../../stores/models/SampleModel";
import SubSampleModel from "../../stores/models/SubSampleModel";
import TemplateModel from "../../stores/models/TemplateModel";
import NoValue from "../../components/NoValue";
import NumberField from "../../components/Inputs/NumberField";
import InputAdornment from "@mui/material/InputAdornment";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import {
  OptionHeading,
  OptionExplanation,
} from "../../components/Inputs/RadioField";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import SearchContext from "../../stores/contexts/Search";
import SearchView from "../Search/SearchView";
import { menuIDs } from "../../util/menuIDs";
import { doNotAwait } from "../../util/Util";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import Search from "../../stores/models/Search";
import type {
  AllowedSearchModules,
  AllowedTypeFilters,
} from "../../stores/definitions/Search";

const useStyles = makeStyles()((theme) => ({
  dialog: {
    height: "100%",
  },
  bottomSpaced: { marginBottom: theme.spacing(1) },
}));

// not Location type, just a way to identify a location (as it varies per container type)
type ParentLocation = {
  index?: number,
  id?: number,
  coordX?: number,
  coordY?: number,
};

export type NewInContainerParams = {
  parentContainers: Array<ContainerModel>,
  parentLocation: ParentLocation,
};

function Content({ container }: {| container: ContainerModel |}): Node {
  const [search, setSearch] = React.useState<Search | null>(null);
  React.useEffect(() => {
    const searchParams = {
      fetcherParams: {
        parentGlobalId: container.globalId,
      },
      uiConfig: {
        allowedSearchModules: (new Set([]): AllowedSearchModules),
        allowedTypeFilters: (new Set([]): AllowedTypeFilters),
        hideContentsOfChip: true,
      },
      factory: new AlwaysNewFactory(),
    };
    const s = new Search(searchParams);
    setSearch(s);
    void s.setSearchView(cTypeToDefaultSearchView(container.cType));
  }, [container]);

  if (!search) return null;
  return (
    <SearchContext.Provider
      value={{
        search,
        scopedResult: container,
        differentSearchForSettingActiveResult: search,
      }}
    >
      <AlwaysNewWindowNavigationContext>
        <SearchView contextMenuId={menuIDs.NONE} />
      </AlwaysNewWindowNavigationContext>
    </SearchContext.Provider>
  );
}

type CreateInContextDialogArgs = {|
  open: boolean,
  onClose: () => void,
  selectedResult: InventoryRecord,
  menuID: $Values<typeof menuIDs>,
|};

function CreateInContextDialog({
  open,
  onClose,
  selectedResult,
  menuID,
}: CreateInContextDialogArgs): Node {
  if (!selectedResult.globalId)
    throw new Error("Global ID of selected item must be known.");
  const globalId = selectedResult.globalId;
  const sourceItemString = selectedResult.name + " - " + globalId;

  const { uiStore, searchStore, createStore } = useStores();
  const { classes } = useStyles();

  const gridContainer: boolean =
    selectedResult instanceof ContainerModel && selectedResult.cType === "GRID";

  const imageContainer: boolean =
    selectedResult instanceof ContainerModel &&
    selectedResult.cType === "IMAGE";

  const imaGridContainer: boolean = imageContainer || gridContainer;

  const canStoreContainers = (): boolean =>
    selectedResult instanceof ContainerModel &&
    selectedResult.canStoreContainers;

  const canStoreSamples = (): boolean =>
    selectedResult instanceof ContainerModel && selectedResult.canStoreSamples;

  const createActions = {
    CONTAINER: [
      {
        id: "ic-1",
        name: `Create Container in ${sourceItemString}`,
        explanation: canStoreContainers()
          ? "A new Container will be created in the selected Container."
          : "The selected Container cannot store Containers.",
        disabled: !canStoreContainers(),
      },
      {
        id: "ic-2",
        name: `Create Sample in ${sourceItemString}`,
        explanation: canStoreSamples()
          ? "A new Sample will be created and its Subsample(s) will be placed in the selected Container."
          : "The selected Container cannot store Samples.",
        disabled: !canStoreSamples(),
      },
    ],
    SAMPLE: [
      {
        id: "sa-1",
        name: `Create Template from ${sourceItemString}`,
        explanation: "A new Template will be created from the selected Sample.",
        disabled: false,
      },
      {
        id: "sa-2",
        name: "Split",
        explanation:
          selectedResult instanceof SampleModel &&
          selectedResult.subSamples.length === 1 ? (
            "Split this sample's only subsample into multiple subsamples."
          ) : (
            <>
              Only samples with a single subsample can be split directly. <br />
              Go to a specific subsample and open its Create dialog to split it.
            </>
          ),
        disabled: !(
          selectedResult instanceof SampleModel &&
          selectedResult.subSamples.length === 1
        ),
      },
    ],
    SAMPLE_TEMPLATE: [
      {
        id: "it-1",
        name: `Create Sample from ${sourceItemString}`,
        explanation: "A new Sample will be created from the selected Template.",
        disabled: false,
      },
    ],
    SUBSAMPLE: [
      {
        id: "ss-1",
        name: `Split Subsample ${globalId}`,
        explanation: "The selected Subsample will be split into parts.",
        disabled: false,
      },
    ],
  };

  const [createOption, setCreateOption] = useState(
    createActions[selectedResult.type].filter((a) => !a.disabled)[0].name
  );
  const [splitCopies, setSplitCopies] = useState(2);
  const [validState, setValidState] = useState(true);

  const [allContainerLocations, setAllContainerLocations] = useState<
    Array<[number, boolean]>
  >([]);
  const [availableLocations, setAvailableLocations] = useState<
    Array<[number, boolean]>
  >([]);

  async function setLocations() {
    if (selectedResult instanceof ContainerModel) {
      /* in some cases (e.g. mobile) locations have not been fetched yet */
      if (!selectedResult.infoLoaded)
        (await selectedResult.fetchAdditionalInfo(): void);

      const allLocationsMapped = selectedResult.locations.map(
        (loc: Location, i: number) => [i + 1, loc.hasContent]
      );
      setAllContainerLocations(allLocationsMapped);
      /* locations without content */
      const available = allLocationsMapped.filter(
        (loc: [number, Location]) => !loc[1]
      );
      setAvailableLocations(available);

      const startingLocation =
        createStore.targetLocationIdentifier ?? available[0][0];
      createStore.setTargetLocationIdentifier(startingLocation);

      /* highlight starting location */
      if (createStore.creationContext)
        selectedResult.locations[startingLocation - 1].toggleSelected(true);
    }
  }

  /** update locations state after render
   * only grid and image containers need to set a target location
   */
  useEffect(() => {
    if (
      selectedResult instanceof ContainerModel &&
      imaGridContainer &&
      !selectedResult.isFull
    ) {
      void setLocations();
    }
  }, []);

  // handler could be moved to createStore or other model as an action (as an improvement)
  const onSubmitHandler = async (): Promise<void> => {
    if (selectedResult instanceof ContainerModel) {
      // handle cases for 3 cTypes
      const parentContainers = [selectedResult];
      let parentLocation: ParentLocation = {};
      if (gridContainer) {
        parentLocation = {
          coordX:
            selectedResult.locations[createStore.targetLocationIdentifier - 1]
              .coordX,
          coordY:
            selectedResult.locations[createStore.targetLocationIdentifier - 1]
              .coordY,
        };
      }
      if (imageContainer) {
        parentLocation = {
          id: selectedResult.locations[createStore.targetLocationIdentifier - 1]
            .id,
        };
      }

      const newItemParams: NewInContainerParams = {
        parentContainers,
        parentLocation,
      };
      if (createOption === createActions[selectedResult.type][0].name)
        await searchStore.createNewContainer(newItemParams);
      if (createOption === createActions[selectedResult.type][1].name)
        await searchStore.createNewSample(newItemParams);
    }
    if (selectedResult instanceof SubSampleModel) {
      if (createOption === createActions[selectedResult.type][0].name) {
        await searchStore.search.splitRecord(
          parseInt(splitCopies, 10),
          selectedResult
        );
      }
    }
    /* check for TemplateModel before SampleModel check */
    if (selectedResult instanceof TemplateModel) {
      if (createOption === createActions[selectedResult.type][0].name) {
        const newSample: SampleModel = await searchStore.createNewSample();
        await newSample.setTemplate(selectedResult);
      }
    }
    if (
      selectedResult instanceof SampleModel &&
      !(selectedResult instanceof TemplateModel)
    ) {
      if (createOption === createActions[selectedResult.type][0].name) {
        createStore.setTemplateCreationContext(menuID);
      }
      if (createOption === createActions[selectedResult.type][1].name) {
        await searchStore.search.splitRecord(
          parseInt(splitCopies, 10),
          selectedResult.subSamples[0]
        );
      }
    }
    onClose();
  };

  const LocationSelector = observer(
    ({ container }: {| container: ContainerModel |}): Node => {
      return (
        <FormControl
          aria-label="Location Options"
          label="Location"
          explanation={
            <>
              {imaGridContainer
                ? "Select an available location for the new item."
                : "Auto (List Containers do not require location selection)."}
            </>
          }
        >
          {/* list container does not require location selection */}
          {selectedResult instanceof ContainerModel &&
          imaGridContainer &&
          availableLocations.length > 0 ? (
            <>
              <Select
                id="location-selector"
                value={createStore.targetLocationIdentifier}
                onChange={({ target: { value } }) => {
                  selectedResult.locations[value - 1].selectOnlyThis();
                  createStore.setTargetLocationIdentifier(
                    allContainerLocations[value - 1][0]
                  );
                }}
                size="small"
                variant="standard"
              >
                {availableLocations.map((l) => (
                  <MenuItem key={l[0]} value={l[0]}>
                    <>{l[0]}</>
                  </MenuItem>
                ))}
              </Select>
              <Content container={container} />
            </>
          ) : null}
        </FormControl>
      );
    }
  );

  function SplitCopiesSelector({ disabled }: {| disabled: boolean |}): Node {
    const MIN = 2;
    const MAX = 100;
    return (
      <Box
        sx={{
          ml: 3,
          width: (theme) => `calc(100% - ${theme.spacing(3)})`,
        }}
      >
        <FormControl>
          <NumberField
            name="copies"
            autoFocus
            value={splitCopies}
            onChange={({ target }) => {
              setSplitCopies(parseInt(target.value, 10));
              setValidState(target.checkValidity() && target.value !== "");
            }}
            error={!validState}
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
            onKeyDown={({ key }) => {
              if (key === "Enter" && validState) void onSubmitHandler();
            }}
            disabled={disabled}
          />
          {/* FormHelperText used rather than NumberField's helperText prop so that the text is always shown, not only when there's an error. */}
          <FormHelperText error={!validState}>
            {`The total number of subsamples wanted, including the source (min ${MIN}
          , max ${MAX})`}
          </FormHelperText>
        </FormControl>
      </Box>
    );
  }

  return (
    <>
      <Dialog
        classes={{
          paper: classes.dialog,
        }}
        open={open}
        onClose={onClose}
        maxWidth="md"
        fullScreen={uiStore.isVerySmall}
        data-testid="CreateInContextDialog"
      >
        <DialogTitle>
          Create Item
          <HelpLinkIcon
            link={docLinks.createDialog}
            title="Info on creating new items."
          />
        </DialogTitle>
        <DialogContent>
          <Typography variant="h6">Source Item</Typography>
          <Typography variant="h5" className={classes.bottomSpaced}>
            {sourceItemString}
          </Typography>
          <>
            <FormControl
              aria-label="Creation Dialog Options"
              label="Creation options"
              explanation={
                <>
                  Select which item you want to create. The available options
                  depend on the source item&apos;s type.
                </>
              }
            >
              <RadioGroup
                value={createOption}
                onChange={({ target: { value } }) => {
                  setCreateOption(value);
                }}
              >
                <Grid container direction="column" spacing={0}>
                  {createActions[selectedResult.type].map((action: any) => (
                    <Grid item key={action.id}>
                      <FormControlLabel
                        value={action.name}
                        control={
                          <Radio
                            color="primary"
                            disabled={action.disabled}
                            data-testid={`option-radio-${action.id}`}
                          />
                        }
                        label={
                          <Box m={1}>
                            <Grid container direction="column" spacing={1}>
                              <OptionHeading>{action.name}</OptionHeading>
                              <OptionExplanation
                                data-testid={`option-explanation-${action.id}`}
                              >
                                {action.explanation}
                              </OptionExplanation>
                            </Grid>
                          </Box>
                        }
                      ></FormControlLabel>
                    </Grid>
                  ))}
                </Grid>
              </RadioGroup>
            </FormControl>
            {selectedResult instanceof ContainerModel && (
              <LocationSelector container={selectedResult} />
            )}
            {selectedResult instanceof SubSampleModel && (
              <SplitCopiesSelector
                disabled={createOption !== createActions.SUBSAMPLE[0].name}
              />
            )}
            {selectedResult instanceof SampleModel &&
              selectedResult.subSamples.length === 1 && (
                <SplitCopiesSelector
                  disabled={createOption !== createActions.SAMPLE[1].name}
                />
              )}
            {createActions[selectedResult.type].length === 0 && (
              <NoValue label="No option available." />
            )}
          </>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={false}>
            Cancel
          </Button>
          <SubmitSpinner
            label="Create"
            onClick={doNotAwait(onSubmitHandler)}
            disabled={!validState}
            loading={createStore.submitting}
          />
        </DialogActions>
      </Dialog>
    </>
  );
}

export default (observer(
  CreateInContextDialog
): ComponentType<CreateInContextDialogArgs>);
