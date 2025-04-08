import React, { useEffect, useState } from "react";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Grid from "@mui/material/Grid";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Switch from "@mui/material/Switch";
import Collapse from "@mui/material/Collapse";
import DMPTableSmall from "../eln-dmp-integration/DMPTool/DMPTableSmall";
import DryadRepo from "./repositories/DryadRepo";
import FigshareRepo from "./repositories/FigshareRepo";
import DataverseRepo from "./repositories/DataverseRepo";
import ZenodoRepo from "./repositories/ZenodoRepo";
import DigitalCommonsDataRepo from "./repositories/DigitalCommonsDataRepo";
import Alert from "@mui/material/Alert";
import { type Validator } from "../util/Validator";
import {
  type Repo,
  type Person,
  type DMPUserInternalId,
  type RepoDetails,
} from "./repositories/common";
import { runInAction, observable } from "mobx";
import { mapNullable } from "../util/Util";
import { observer } from "mobx-react-lite";
import axios from "@/common/axios";
import Divider from "@mui/material/Divider";
import { type Tag } from "./repositories/Tags";
import { AutocompleteInputChangeReason } from "@mui/material/Autocomplete";

const STANDARD_VALIDATIONS = {
  description: false,
  title: false,
  author: false,
  contact: false,
  subject: false,
};

const DRYAD_VALIDATIONS = {
  ...STANDARD_VALIDATIONS,
  crossrefFunder: false,
};

type ExportRepoArgs = {
  validator: Validator;
  repoList: Array<Repo>;
  repoDetails: RepoDetails;
  updateRepoConfig: (repoDetails: RepoDetails) => void;
  fetchTags: () => Promise<Array<Tag>>;
};

const DmpSelector = observer(
  ({
    state,
    handleSwitch,
    repo,
  }: {
    state: { selectedPlans: Array<DMPUserInternalId>; linkDMP: boolean };
    handleSwitch: (
      foo: "linkDMP"
    ) => (event: { target: { checked: boolean } }) => void;
    repo: Repo;
  }) => {
    const addSelectedPlan = (id: DMPUserInternalId) => {
      if (!state.selectedPlans.includes(id)) {
        runInAction(() => {
          state.selectedPlans = [...state.selectedPlans, id];
        });
      }
    };

    const removeSelectedPlan = (id: DMPUserInternalId) => {
      runInAction(() => {
        state.selectedPlans = state.selectedPlans.filter(
          (planId) => planId !== id
        );
      });
    };

    return mapNullable(
      (dmps) => (
        <Grid container style={{ width: "100%" }}>
          <Grid item xs={12}>
            <FormControlLabel
              control={
                <Switch
                  checked={state.linkDMP}
                  onChange={handleSwitch("linkDMP")}
                  value="link DMP"
                  color="primary"
                  data-test-id="link DMP"
                />
              }
              label={"Associate export with a Data Management Plans (DMPs)"}
            />
          </Grid>
          <Grid item xs={12}>
            <Collapse in={state.linkDMP} component="div" collapsedSize={0}>
              <>
                <DMPTableSmall
                  plans={dmps}
                  selectedPlans={state.selectedPlans}
                  addSelectedPlan={addSelectedPlan}
                  removeSelectedPlan={removeSelectedPlan}
                />
                {repo.repoName === "app.zenodo" &&
                  state.selectedPlans.length > 1 && (
                    <Alert severity="error">
                      Only one DMP can be associated with an export to Zenodo.
                    </Alert>
                  )}
              </>
            </Collapse>
          </Grid>
        </Grid>
      ),
      repo.linkedDMPs
    );
  }
);

function ExportRepo({
  validator,
  repoList,
  repoDetails,
  updateRepoConfig,
  fetchTags,
}: ExportRepoArgs): React.ReactNode {
  const [state] = useState(
    observable({
      inputValidations: STANDARD_VALIDATIONS as
        | typeof STANDARD_VALIDATIONS
        | typeof DRYAD_VALIDATIONS,
      submitAttempt: false,
      repoChoice: repoDetails.repoChoice,
      title: repoDetails.meta.title,
      description: repoDetails.meta.description,
      subject: repoDetails.meta.subject,
      license: 0,
      publish: repoDetails.meta.publish,
      authors: repoDetails.meta.authors,
      contacts: repoDetails.meta.contacts,
      otherProperties: {} as Record<string, string>,
      linkDMP: false,
      selectedPlans: [] as Array<DMPUserInternalId>,
      crossrefFunders: [] as Array<{ name: string }>,
      selectedFunder: {},
      tags: [] as Array<Tag>,
    })
  );
  const [fetchingTags, setFetchingTags] = useState(false);

  useEffect(() => {
    void (async () => {
      setFetchingTags(true);
      const tags = await fetchTags();
      runInAction(() => {
        state.tags = tags;
      });
      setFetchingTags(false);
    })();
  }, []);

  useEffect(() => {
    validator.setValidFunc(() => {
      const repo = repoList[state.repoChoice];
      let validations: Record<string, boolean> = { ...STANDARD_VALIDATIONS };

      if (repo.repoName === "app.dryad") {
        validations = { ...DRYAD_VALIDATIONS };
        if (Object.keys(state.otherProperties.funder).length !== 0)
          validations.crossrefFunder = true;
      }

      if (state.description.length > 2) validations.description = true;
      if (state.subject !== "") validations.subject = true;
      if (state.title.length > 2) validations.title = true;
      if (state.authors.length) validations.author = true;
      if (state.contacts.length) validations.contact = true;

      runInAction(() => {
        state.inputValidations = validations as
          | typeof STANDARD_VALIDATIONS
          | typeof DRYAD_VALIDATIONS;
        state.submitAttempt = true;
      });

      return Promise.resolve(
        [
          ...Object.values(validations),
          !(repo.repoName === "app.zenodo" && state.selectedPlans.length > 1),
        ].every((b) => b)
      );
    });
  }, []);

  const getParams = () => {
    const repo = repoList[state.repoChoice];

    return {
      repoChoice: state.repoChoice,
      appName: repo.repoName,
      depositToRepository: true,
      repoCfg: repo.repoCfg,
      selectedDMPs: state.selectedPlans,
      meta: {
        title: state.title,
        description: state.description,
        subject: state.subject,
        licenseName:
          repo.license.licenses[state.license].licenseDefinition.name,
        licenseUrl: repo.license.licenses[state.license].licenseDefinition.url,
        authors: state.authors,
        contacts: state.contacts,
        publish: state.publish,
        otherProperties: state.otherProperties,
        tags: state.tags,
      },
    };
  };

  const updateRemoteConfig = () => {
    updateRepoConfig(getParams());
  };

  const handleChange = <Key extends keyof typeof state>(event: {
    target: { name: Key; value: (typeof state)[Key] };
  }) => {
    runInAction(() => {
      state[event.target.name] = event.target.value;
    });
    updateRemoteConfig();
  };

  const handleSwitch =
    <Key extends keyof typeof state>(
      name: Key
    ): ((event: { target: { checked: boolean } }) => void) =>
    (event) => {
      runInAction(() => {
        // @ts-expect-error the type of state[name] might not be a boolean
        state[name] = event.target.checked;
      });
    };

  const updatePeople = (people: Array<Person>) => {
    runInAction(() => {
      state.authors = people.filter((person) => person.type === "Author");
      state.contacts = people.filter((person) => person.type === "Contact");
    });
    updateRemoteConfig();
  };

  const handleCrossrefFunderChange = (_: unknown, values: { name: string }) => {
    runInAction(() => {
      state.otherProperties = {
        funder: JSON.stringify(values),
      };
    });
    updateRemoteConfig();
  };

  const fetchCrossrefFunders = async (query: string) => {
    try {
      const searchParams = new URLSearchParams();
      if (query) {
        searchParams.append("query", query);
      }
      const { data } = await axios.get<{
        message: { items: Array<{ name: string }> };
      }>("https://api.crossref.org/funders", {
        params: searchParams,
      });
      runInAction(() => {
        state.crossrefFunders = data.message.items;
      });
      updateRemoteConfig();
    } catch (error) {
      console.error("Error retrieving funders from crossref API:", error);
    }
  };

  const handleFetchCrossrefFunder = (
    event: React.SyntheticEvent<Element, Event>,
    searchTerm: string,
    reason: AutocompleteInputChangeReason
  ) => {
    if (searchTerm && searchTerm.length > 2 && reason === "input") {
      void fetchCrossrefFunders(searchTerm);
    }
  };

  const repo: Repo = repoList[state.repoChoice];

  return (
    <Grid container direction="column" spacing={1} style={{ width: "100%" }}>
      <Grid item xs={12}>
        <FormControl component="fieldset">
          <FormLabel component="legend">
            Please choose one of your configured repositories to submit your
            export to:
          </FormLabel>
          <RadioGroup
            aria-label="Repository choice"
            name="repoChoice"
            value={state.repoChoice.toString()}
            // @ts-expect-error React event handlers are not parameterised by the name prop
            onChange={handleChange}
          >
            {repoList.map((r, i) => (
              <div key={r.repoName}>
                <FormControlLabel
                  value={`${i}`}
                  control={
                    <Radio
                      color="primary"
                      inputProps={{
                        // @ts-expect-error Extra props are just passed through to the underlying DOM element
                        "data-testid": `radio-button-${r.repoName}`,
                      }}
                    />
                  }
                  label={r.displayName + (r.label ? ` - ${r.label}` : "")}
                  data-testid={`repo-label-${r.repoName}`}
                />
              </div>
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
      <Grid item>
        <Divider orientation="horizontal" />
      </Grid>
      <Grid item>
        {repo.repoName === "app.dryad" && (
          <>
            <DmpSelector
              state={state}
              handleSwitch={handleSwitch}
              repo={repo}
            />
            <DryadRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              handleCrossrefFunderChange={handleCrossrefFunderChange}
              handleFetchCrossrefFunder={handleFetchCrossrefFunder}
              crossrefFunders={state.crossrefFunders}
              inputValidations={
                state.inputValidations as typeof DRYAD_VALIDATIONS
              }
              submitAttempt={state.submitAttempt}
              contacts={state.contacts}
              authors={state.authors}
            />
          </>
        )}
        {repo.repoName === "app.dataverse" && (
          <>
            <DmpSelector
              state={state}
              handleSwitch={handleSwitch}
              repo={repo}
            />
            <DataverseRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              inputValidations={state.inputValidations}
              submitAttempt={state.submitAttempt}
              contacts={state.contacts}
              authors={state.authors}
              title={state.title}
              description={state.description}
              license={state.license}
              subject={state.subject}
              onTagsChange={({ target: { value } }) => {
                runInAction(() => {
                  state.tags = value;
                });
                updateRemoteConfig();
              }}
              tags={state.tags}
              fetchingTags={fetchingTags}
            />
          </>
        )}
        {repo.repoName === "app.figshare" && (
          <>
            <DmpSelector
              state={state}
              handleSwitch={handleSwitch}
              repo={repo}
            />
            <FigshareRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              inputValidations={state.inputValidations}
              submitAttempt={state.submitAttempt}
              contacts={state.contacts}
              authors={state.authors}
              title={state.title}
              description={state.description}
              license={state.license}
              publish={state.publish}
              subject={state.subject}
            />
          </>
        )}
        {repo.repoName === "app.zenodo" && (
          <>
            <DmpSelector
              state={state}
              handleSwitch={handleSwitch}
              repo={repo}
            />
            <ZenodoRepo
              handleChange={handleChange}
              updatePeople={updatePeople}
              inputValidations={state.inputValidations}
              submitAttempt={state.submitAttempt}
              title={state.title}
              description={state.description}
              tags={state.tags}
              fetchingTags={fetchingTags}
              onTagsChange={({ target: { value } }) => {
                runInAction(() => {
                  state.tags = value;
                });
                updateRemoteConfig();
              }}
            />
          </>
        )}
        {repo.repoName === "app.digitalCommonsData" && (
          <DigitalCommonsDataRepo
            handleChange={handleChange}
            updatePeople={updatePeople}
            inputValidations={state.inputValidations}
            submitAttempt={state.submitAttempt}
            title={state.title}
            description={state.description}
            tags={state.tags}
            fetchingTags={fetchingTags}
            onTagsChange={() => {}}
          />
        )}
      </Grid>
    </Grid>
  );
}

export default observer(ExportRepo);
