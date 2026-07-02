import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import Users from "../ExportRepoUser";
import type { Person, Repo, StandardValidations } from "./common";

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
  publish: boolean;
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
  const { t } = useTranslation("workspace");
  return (
    <Grid container sx={{ width: "100%" }}>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.title}
          name="title"
          label={t("export.repositories.common.title")}
          required
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText={t("export.repositories.figshare.titleHelper")}
          fullWidth
          value={title}
        />
      </Grid>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          name="description"
          label={t("export.repositories.common.description")}
          required
          multiline
          maxRows="4"
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText={t("export.repositories.figshare.descriptionHelper")}
          fullWidth
          value={description}
        />
      </Grid>
      <Grid size={5}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          label={t("export.repositories.figshare.subject")}
          required
          defaultValue={""}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.figshare.subjectHelper")}
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
      <Grid size={2}></Grid>
      <Grid size={5}>
        <TextField
          name="license"
          select
          label={t("export.repositories.figshare.license")}
          required
          defaultValue={0}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.figshare.licenseHelper")}
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
      <Grid size={12}>
        <FormControl component="fieldset" sx={{ marginTop: "20px" }}>
          <FormLabel component="legend">{t("export.repositories.figshare.publishingStatus")}</FormLabel>
          <RadioGroup
            aria-label={t("export.repositories.figshare.publishingStatus")}
            name="publish"
            // @ts-expect-error React event handlers are not parameterised by the name prop
            onChange={handleChange}
            sx={{
              flexDirection: "row",
            }}
            value={publish}
          >
            <FormControlLabel
              value="true"
              control={<Radio color="primary" />}
              label={t("export.repositories.figshare.publish")}
            />
            <FormControlLabel
              value="false"
              control={<Radio color="primary" />}
              label={t("export.repositories.figshare.draft")}
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
