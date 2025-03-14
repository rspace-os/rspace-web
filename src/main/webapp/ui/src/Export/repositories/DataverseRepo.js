// @flow

import { default as React, type Node, type ComponentType } from "react";
import Users from "../ExportRepoUser";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import Grid from "@mui/material/Grid";
import { type Person, type Repo, type StandardValidations } from "./common";
import Tags, { type Tag } from "./Tags";

type DataverseArgs = {|
  repo: Repo,
  onTagsChange: ({
    target: {
      value: Array<Tag>,
    },
  }) => void,
  handleChange: (event: {
    target: {
      name: "title" | "description" | "subject" | "license",
      value: string,
    },
  }) => void,
  inputValidations: StandardValidations,
  submitAttempt: boolean,
  updatePeople: (people: Array<Person>) => void,
  contacts: Array<Person>,
  authors: Array<Person>,
  title: string,
  description: string,
  subject: string,
  license: number,
  tags: Array<{|
    value: string,
    vocabulary: string,
    uri: string,
    version: string,
  |}>,
  fetchingTags: boolean,
|};

function DataverseRepo({
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
  license,
  tags,
  onTagsChange,
  fetchingTags,
}: DataverseArgs): Node {
  return (
    <Grid container sx={{ width: "100%" }}>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.title}
          name="title"
          label="Title *"
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
          onChange={handleChange}
          margin="normal"
          helperText="Please add a relevant description for your export"
          fullWidth
          value={description}
        />
      </Grid>
      <Grid item xs={5}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          label="Subject *"
          defaultValue={""}
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
      <Grid item xs={2}></Grid>
      <Grid item xs={5}>
        <TextField
          name="license"
          select
          label="License *"
          defaultValue={0}
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

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Dataverse.
 */
export default (DataverseRepo: ComponentType<DataverseArgs>);
