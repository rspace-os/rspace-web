// @flow

import { default as React, type Node, type ComponentType } from "react";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import Autocomplete from "@mui/material/Autocomplete";
import MenuItem from "@mui/material/MenuItem";
import Users from "../ExportRepoUser";
import { type Person, type Repo, type StandardValidations } from "./common";

export type DryadValidations = {|
  ...StandardValidations,
  crossrefFunder: boolean,
|};

type DryadRepoArgs = {|
  repo: Repo,
  handleChange: (event: {
    target: {
      name: "title" | "description" | "subject" | "license",
      value: string,
    },
  }) => void,
  handleCrossrefFunderChange: (mixed, { name: string }) => void,
  handleFetchCrossrefFunder: (
    event: { target: { value: string } },
    value: string,
    reason: string
  ) => void,
  crossrefFunders: Array<{ name: string }>,
  inputValidations: DryadValidations,
  submitAttempt: boolean,
  updatePeople: (people: Array<Person>) => void,
  contacts: Array<Person>,
  authors: Array<Person>,
|};

function DryadRepo({
  repo,
  handleChange,
  handleCrossrefFunderChange,
  handleFetchCrossrefFunder,
  inputValidations,
  submitAttempt,
  crossrefFunders,
  updatePeople,
  contacts,
  authors,
}: DryadRepoArgs): Node {
  return (
    <Grid container style={{ width: "100%" }}>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.title}
          name="title"
          label="Title *"
          onChange={handleChange}
          margin="normal"
          helperText="Please choose a title, >3 symbols"
          fullWidth
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          name="description"
          label="Add an abstract *"
          multiline
          maxRows="4"
          onChange={handleChange}
          margin="normal"
          helperText="Please add a relevant abstract for your export"
          fullWidth
        />
      </Grid>
      <Grid item xs={6}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          defaultValue={""}
          label="Research Domain *"
          onChange={handleChange}
          helperText="Please select your research domain"
          margin="normal"
          fullWidth
        >
          {repo.subjects.map((option) => (
            <MenuItem key={option.name} value={option.name}>
              {option.name}
            </MenuItem>
          ))}
        </TextField>
      </Grid>
      <Grid item xs={6}>
        <TextField
          name="license"
          select
          label="License *"
          defaultValue={0}
          onChange={handleChange}
          helperText="Please select your license"
          margin="normal"
          fullWidth
        >
          {repo.license.licenses.map((option, idx) => (
            <MenuItem key={option.licenseDefinition.name} value={`${idx}`}>
              {option.licenseDefinition.name}
            </MenuItem>
          ))}
        </TextField>
      </Grid>
      <Grid item xs={6}>
        {/* Granting Org */}
        <Autocomplete
          id="dryad-crossref-funders-autocomplete"
          label="Granting Organization *"
          options={crossrefFunders}
          getOptionLabel={(option) => option.name}
          getOptionSelected={(option, value) => option.name === value.name}
          onChange={handleCrossrefFunderChange}
          onInputChange={handleFetchCrossrefFunder}
          sx={{ width: 300 }}
          renderInput={(params) => (
            <TextField
              {...params}
              label="Granting Organization *"
              error={submitAttempt && !inputValidations.crossrefFunder}
              helperText="Enter 3 of more characters to filter list"
            />
          )}
        />
      </Grid>
      <Users
        updatePeople={updatePeople}
        initialPeople={contacts.concat(authors)}
        submitAttempt={submitAttempt}
        inputValidations={inputValidations}
      />
    </Grid>
  );
}

export default (DryadRepo: ComponentType<DryadRepoArgs>);
