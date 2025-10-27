import React from "react";
import Users from "../ExportRepoUser";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import Grid from "@mui/material/Grid";
import { type Person, type Repo, type StandardValidations } from "./common";
import Tags, { type Tag } from "./Tags";

type DataverseArgs = {
  repo: Repo;
  onTagsChange: (event: {
    target: {
      value: Array<Tag>;
    };
  }) => void;
  handleChange: (event: {
    target: {
      name: "title" | "description" | "subject" | "license";
      value: string;
    };
  }) => void;
  handleMetadataLanguageChange: (event: {
    target: {
      name: "metadataLanguage";
      value: string;
    };
  }) => void;
  inputValidations: StandardValidations;
  submitAttempt: boolean;
  updatePeople: (people: Array<Person>) => void;
  contacts: Array<Person>;
  authors: Array<Person>;
  title: string;
  description: string;
  subject: string;
  license: number;
  tags: Array<{
    value: string;
    vocabulary: string;
    uri: string;
    version: string;
  }>;
  fetchingTags: boolean;
  metadataLanguage: string;
};

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Dataverse.
 */
export default function DataverseRepo({
  repo,
  handleChange,
  inputValidations,
  submitAttempt,
  updatePeople,
  contacts,
  authors,
  title,
  description,
  subject,
  metadataLanguage,
  handleMetadataLanguageChange,
  license,
  tags,
  onTagsChange,
  fetchingTags,
}: DataverseArgs): React.ReactNode {
  return (
    <Grid container sx={{ width: "100%" }}>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.title}
          name="title"
          label="Title *"
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText="Please choose a title, >3 symbols"
          fullWidth
          value={title}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          name="description"
          label="Description *"
          multiline
          maxRows="4"
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText="Please add a relevant description for your export"
          fullWidth
          value={description}
        />
      </Grid>
      <Grid item xs={4}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          label="Subject *"
          defaultValue={""}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText="Please select your subject"
          margin="normal"
          fullWidth
          value={subject}
        >
          {repo.subjects.map((option) => (
            <MenuItem key={option.name} value={option.name}>
              {option.name}
            </MenuItem>
          ))}
        </TextField>
      </Grid>
      {repo.metadataLanguages.length > 0 && (<Grid item xs={4}>
        <TextField
          //TODO: deal with validation
          error={submitAttempt && !inputValidations.subject}
          name="metadataLanguage"
          select
          label="Metadata language *"
          defaultValue={null}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleMetadataLanguageChange}
          helperText="Please select your metadata language"
          margin="normal"
          fullWidth
          value={metadataLanguage}
        >
          {repo.metadataLanguages.map((option) => (
            <MenuItem key={option.title} value={option.locale}>
              {option.title}
            </MenuItem>
          ))}
        </TextField>
      </Grid>)}
      <Grid item xs={4}>
        <TextField
          name="license"
          select
          label="License *"
          defaultValue={0}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText="Please select your license"
          margin="normal"
          fullWidth
          value={license}
        >
          {repo.license.licenses.map((option, idx) => (
            <MenuItem key={option.licenseDefinition.name} value={`${idx}`}>
              {option.licenseDefinition.name}
            </MenuItem>
          ))}
        </TextField>
      </Grid>
      <Grid item xs={12}>
        <Users
          updatePeople={updatePeople}
          initialPeople={contacts.concat(authors)}
          submitAttempt={submitAttempt}
          inputValidations={inputValidations}
        />
      </Grid>
      <Grid item xs={12} mt={1}>
        <Tags
          fieldOwner={{
            fieldValues: { tags },
            setFieldsDirty: ({ tags: newTags }: { tags?: Array<Tag> }) => {
              if (newTags) onTagsChange({ target: { value: newTags } });
            },
            setFieldEditable: () => {},
            noValueLabel: { tags: null },
            isFieldEditable: () => true,
            canChooseWhichToEdit: false,
          }}
          loading={fetchingTags}
        />
      </Grid>
    </Grid>
  );
}
