import React from "react";
import Users from "../ExportRepoUser";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import Grid from "@mui/material/Grid";
import { type Person, type Repo, type StandardValidations } from "./common";

type FigshareArgs = {
  repo: Repo;
  handleChange: (event: {
    target: {
      name: "title" | "description" | "subject" | "license" | "publish";
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
  publish: "false" | "true";
};

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Figshare.
 */
export default function FigshareRepo({
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
  publish,
}: FigshareArgs): React.ReactNode {
  return (
    <Grid container style={{ width: "100%" }}>
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
      <Grid item xs={5}>
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
      <Grid item xs={2}></Grid>
      <Grid item xs={5}>
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
        <FormControl component="fieldset" style={{ marginTop: "20px" }}>
          <FormLabel component="legend">Publishing status</FormLabel>
          <RadioGroup
            aria-label="Publishing status"
            name="publish"
            // @ts-expect-error React event handlers are not parameterised by the name prop
            onChange={handleChange}
            style={{
              flexDirection: "row",
            }}
            value={publish}
          >
            <FormControlLabel
              value="true"
              control={<Radio color="primary" />}
              label="Publish"
            />
            <FormControlLabel
              value="false"
              control={<Radio color="primary" />}
              label="Draft"
            />
          </RadioGroup>
        </FormControl>
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
