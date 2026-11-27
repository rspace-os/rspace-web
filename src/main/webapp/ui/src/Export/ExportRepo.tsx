import Alert from "@mui/material/Alert";
import type { AutocompleteInputChangeReason } from "@mui/material/Autocomplete";
import Collapse from "@mui/material/Collapse";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Switch from "@mui/material/Switch";
import { useStore } from "@tanstack/react-form";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import axios from "@/common/axios";
import DMPTableSmall from "../eln-dmp-integration/DMPTool/DMPTableSmall";
import { mapNullable } from "../util/Util";
import { type ExportForm, type RepositoryForm, validateWith } from "./exportForm";
import { type DMPUserInternalId, type Person, REPOS_WITHOUT_PEOPLE_OR_SUBJECT, type Repo } from "./repositories/common";
import DataverseRepo from "./repositories/DataverseRepo";
import DigitalCommonsDataRepo from "./repositories/DigitalCommonsDataRepo";
import DryadRepo from "./repositories/DryadRepo";
import FigshareRepo from "./repositories/FigshareRepo";
import type { Tag } from "./repositories/Tags";
import ZenodoRepo from "./repositories/ZenodoRepo";

const atLeastThreeCharacters = v.pipe(v.string(), v.minLength(3));

/**
 * The rules for the metadata of a deposit to the given repository.
 */
const repositoryFormSchema = (repo: Repo) => {
  const needsPeopleAndSubject = !REPOS_WITHOUT_PEOPLE_OR_SUBJECT.has(repo.repoName);
  const people = needsPeopleAndSubject ? v.pipe(v.array(v.unknown()), v.nonEmpty()) : v.array(v.unknown());
  return v.looseObject({
    title: atLeastThreeCharacters,
    description: atLeastThreeCharacters,
    subject: needsPeopleAndSubject ? v.pipe(v.string(), v.nonEmpty()) : v.string(),
    authors: people,
    contacts: people,
    otherProperties:
      repo.repoName === "app.dryad" ? v.looseObject({ funder: v.pipe(v.string(), v.nonEmpty()) }) : v.unknown(),
    // Zenodo deposits can be associated with at most one DMP
    selectedPlans: repo.repoName === "app.zenodo" ? v.pipe(v.array(v.unknown()), v.maxLength(1)) : v.unknown(),
  });
};

export type RepositoryFormErrors = v.FlatErrors<ReturnType<typeof repositoryFormSchema>>;

/**
 * Validates the metadata of a deposit against the rules of the chosen
 * repository.
 *
 * @param repoList The repositories that the user can choose from.
 */
export const repositoryFormValidator =
  (repoList: ReadonlyArray<Repo>) =>
  ({ value }: { value: RepositoryForm }): RepositoryFormErrors | undefined =>
    // plans selected while association is off are not sent, so they cannot break Zenodo's one-plan limit
    validateWith(repositoryFormSchema(repoList[Number(value.repoChoice)]))({
      value: value.linkDMP ? value : { ...value, selectedPlans: [] },
    });

type ExportRepoArgs = {
  form: ExportForm;
  // the issues found when the user last tapped Next on this pane, if any
  errors: RepositoryFormErrors | undefined;
  fetchTags: () => Promise<Array<Tag>>;
};

const DmpSelector = ({
  linkDMP,
  selectedPlans,
  setLinkDMP,
  setSelectedPlans,
  repo,
}: {
  linkDMP: boolean;
  selectedPlans: Array<DMPUserInternalId>;
  setLinkDMP: (linkDMP: boolean) => void;
  setSelectedPlans: (update: (selectedPlans: Array<DMPUserInternalId>) => Array<DMPUserInternalId>) => void;
  repo: Repo;
}) => {
  const { t } = useTranslation("workspace");
  const addSelectedPlan = (id: DMPUserInternalId) => {
    setSelectedPlans((plans) => (plans.includes(id) ? plans : [...plans, id]));
  };

  const removeSelectedPlan = (id: DMPUserInternalId) => {
    setSelectedPlans((plans) => plans.filter((planId) => planId !== id));
  };

  return mapNullable(
    (dmps) => (
      <Grid container sx={{ width: "100%" }}>
        <Grid size={12}>
          <FormControlLabel
            control={
              <Switch
                checked={linkDMP}
                onChange={({ target: { checked } }) => setLinkDMP(checked)}
                value="link DMP"
                color="primary"
                data-test-id="link DMP"
                slotProps={{ input: { role: "checkbox" } }}
              />
            }
            label={t("export.repositories.dmp.associateLabel")}
          />
        </Grid>
        <Grid size={12}>
          <Collapse in={linkDMP} component="div" collapsedSize={0}>
            <DMPTableSmall
              plans={dmps}
              selectedPlans={selectedPlans}
              addSelectedPlan={addSelectedPlan}
              removeSelectedPlan={removeSelectedPlan}
            />
            {repo.repoName === "app.zenodo" && selectedPlans.length > 1 && (
              <Alert severity="error">{t("export.repositories.dmp.zenodoLimit")}</Alert>
            )}
          </Collapse>
        </Grid>
      </Grid>
    ),
    repo.linkedDMPs,
  );
};

function ExportRepo({ form, errors, fetchTags }: ExportRepoArgs): React.ReactNode {
  const { t } = useTranslation("workspace");
  const state = useStore(form.store, (formState) => formState.values.repositoryForm);
  const repoList = useStore(form.store, (formState) => formState.values.exportConfig.repoData);
  const [crossrefFunders, setCrossrefFunders] = useState<Array<{ name: string }>>([]);
  const [fetchingTags, setFetchingTags] = useState(false);

  useEffect(() => {
    const { archiveType } = form.state.values.exportConfig;
    if (form.state.values.repositoryForm.tagsLoadedFor === archiveType) return;
    let unmounted = false;
    void (async () => {
      setFetchingTags(true);
      try {
        const tags = await fetchTags();
        if (unmounted) return;
        form.setFieldValue("repositoryForm", (s) => ({ ...s, tags, tagsLoadedFor: archiveType }));
      } catch (e) {
        console.error(e);
        // Tags loaded for another format do not describe this export, so drop them and forget which format they were
        // for: the next visit, for either format, then tries again.
        if (!unmounted)
          form.setFieldValue("repositoryForm", (s) =>
            s.tagsLoadedFor !== null && s.tagsLoadedFor !== archiveType ? { ...s, tags: [], tagsLoadedFor: null } : s,
          );
      } finally {
        if (!unmounted) setFetchingTags(false);
      }
    })();
    return () => {
      unmounted = true;
    };
  }, []);

  const fieldErrors = errors?.nested ?? {};
  const submitAttempt = errors !== undefined;
  const inputValidations = {
    title: !fieldErrors.title,
    description: !fieldErrors.description,
    subject: !fieldErrors.subject,
    author: !fieldErrors.authors,
    contact: !fieldErrors.contacts,
    crossrefFunder: !fieldErrors["otherProperties.funder"],
  };

  const handleChange = ({
    target: { name, value },
  }: {
    target: { name: "repoChoice" | "title" | "description" | "subject" | "license" | "publish"; value: string };
  }) => {
    if (name === "repoChoice") {
      // licences are listed per repository, so an index chosen for another repository may not exist in this one
      form.setFieldValue("repositoryForm", (s) => ({ ...s, repoChoice: value, license: 0 }));
      return;
    }
    form.setFieldValue(`repositoryForm.${name}`, value);
  };

  const handleMetadataLanguageChange = (event: { target: { name: string; value: string } }) => {
    const { value } = event.target;
    form.setFieldValue("repositoryForm", (s) => ({
      ...s,
      otherProperties: { metadataLanguage: value },
      metadataLanguage: value,
    }));
  };

  const setLinkDMP = (linkDMP: boolean) => {
    form.setFieldValue("repositoryForm.linkDMP", linkDMP);
  };

  const setSelectedPlans = (update: (selectedPlans: Array<DMPUserInternalId>) => Array<DMPUserInternalId>) => {
    form.setFieldValue("repositoryForm.selectedPlans", update);
  };

  const updatePeople = (people: Array<Person>) => {
    form.setFieldValue("repositoryForm", (s) => ({
      ...s,
      authors: people.filter((person) => person.type === "Author"),
      contacts: people.filter((person) => person.type === "Contact"),
    }));
  };

  const handleCrossrefFunderChange = (_: unknown, values: { name: string }) => {
    form.setFieldValue("repositoryForm.otherProperties", { funder: JSON.stringify(values) });
  };

  const onTagsChange = ({ target: { value } }: { target: { value: Array<Tag> } }) => {
    form.setFieldValue("repositoryForm.tags", value);
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
      // Only the local suggestion list changes, so a late response cannot write into an export opened after this one.
      setCrossrefFunders(data.message.items);
    } catch (error) {
      console.error("Error retrieving funders from crossref API:", error);
    }
  };

  const handleFetchCrossrefFunder = (
    _event: React.SyntheticEvent<Element, Event>,
    searchTerm: string,
    reason: AutocompleteInputChangeReason,
  ) => {
    if (searchTerm && searchTerm.length > 2 && reason === "input") {
      void fetchCrossrefFunders(searchTerm);
    }
  };

  const repo: Repo = repoList[Number(state.repoChoice)];

  return (
    <Grid container sx={{ flexDirection: "column", width: "100%" }} spacing={1}>
      <Grid size={12}>
        <FormControl component="fieldset">
          <FormLabel component="legend">{t("export.repositories.choiceLabel")}</FormLabel>
          <RadioGroup
            aria-label={t("export.repositories.labels.choice")}
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
                      slotProps={{
                        input: {
                          "data-testid": `radio-button-${r.repoName}`,
                        } as Record<string, unknown>,
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
      <Grid>
        <Divider orientation="horizontal" />
      </Grid>
      <Grid>
        {repo.repoName === "app.dryad" && (
          <>
            <DmpSelector
              linkDMP={state.linkDMP}
              selectedPlans={state.selectedPlans}
              setLinkDMP={setLinkDMP}
              setSelectedPlans={setSelectedPlans}
              repo={repo}
            />
            <DryadRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              handleCrossrefFunderChange={handleCrossrefFunderChange}
              handleFetchCrossrefFunder={handleFetchCrossrefFunder}
              crossrefFunders={crossrefFunders}
              inputValidations={inputValidations}
              submitAttempt={submitAttempt}
              contacts={state.contacts}
              authors={state.authors}
            />
          </>
        )}
        {repo.repoName === "app.dataverse" && (
          <>
            <DmpSelector
              linkDMP={state.linkDMP}
              selectedPlans={state.selectedPlans}
              setLinkDMP={setLinkDMP}
              setSelectedPlans={setSelectedPlans}
              repo={repo}
            />
            <DataverseRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              inputValidations={inputValidations}
              submitAttempt={submitAttempt}
              contacts={state.contacts}
              authors={state.authors}
              title={state.title}
              description={state.description}
              license={state.license}
              subject={state.subject}
              onTagsChange={onTagsChange}
              tags={state.tags}
              fetchingTags={fetchingTags}
              metadataLanguage={state.metadataLanguage}
              handleMetadataLanguageChange={handleMetadataLanguageChange}
            />
          </>
        )}
        {repo.repoName === "app.figshare" && (
          <>
            <DmpSelector
              linkDMP={state.linkDMP}
              selectedPlans={state.selectedPlans}
              setLinkDMP={setLinkDMP}
              setSelectedPlans={setSelectedPlans}
              repo={repo}
            />
            <FigshareRepo
              repo={repo}
              handleChange={handleChange}
              updatePeople={updatePeople}
              inputValidations={inputValidations}
              submitAttempt={submitAttempt}
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
              linkDMP={state.linkDMP}
              selectedPlans={state.selectedPlans}
              setLinkDMP={setLinkDMP}
              setSelectedPlans={setSelectedPlans}
              repo={repo}
            />
            <ZenodoRepo
              handleChange={handleChange}
              inputValidations={inputValidations}
              submitAttempt={submitAttempt}
              title={state.title}
              description={state.description}
              tags={state.tags}
              fetchingTags={fetchingTags}
              onTagsChange={onTagsChange}
            />
          </>
        )}
        {repo.repoName === "app.digitalCommonsData" && (
          <DigitalCommonsDataRepo
            handleChange={handleChange}
            inputValidations={inputValidations}
            submitAttempt={submitAttempt}
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

export default ExportRepo;
