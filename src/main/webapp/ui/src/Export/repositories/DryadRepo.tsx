import Autocomplete, { type AutocompleteInputChangeReason } from "@mui/material/Autocomplete";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import Users from "../ExportRepoUser";
import type { Person, Repo, StandardValidations } from "./common";

/**
 * Boolean flags for the valid state of the Dryad metadata fields.
 */
export type DryadValidations = StandardValidations & {
  crossrefFunder: boolean;
};

type DryadRepoArgs = {
  repo: Repo;
  handleChange: (event: {
    target: {
      name: "title" | "description" | "subject" | "license";
      value: string;
    };
  }) => void;
  handleCrossrefFunderChange: (_unused: unknown, event: { name: string }) => void;
  handleFetchCrossrefFunder: (
    event: React.SyntheticEvent<Element, Event>,
    value: string,
    reason: AutocompleteInputChangeReason,
  ) => void;
  crossrefFunders: Array<{ name: string }>;
  inputValidations: DryadValidations;
  submitAttempt: boolean;
  updatePeople: (people: Array<Person>) => void;
  contacts: Array<Person>;
  authors: Array<Person>;
};

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Dryad.
 */
export default function DryadRepo({
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
}: DryadRepoArgs): React.ReactNode {
  const { t } = useTranslation("workspace");
  return (
    <Grid container sx={{ width: "100%" }}>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.title}
          name="title"
          label={t("export.repositories.common.titleLabel")}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText={t("export.repositories.dryad.titleHelper")}
          fullWidth
        />
      </Grid>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          name="description"
          label={t("export.repositories.dryad.abstractLabel")}
          multiline
          maxRows="4"
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText={t("export.repositories.dryad.abstractHelper")}
          fullWidth
        />
      </Grid>
      <Grid size={6}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          defaultValue={""}
          label={t("export.repositories.dryad.researchDomainLabel")}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.dryad.researchDomainHelper")}
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
      <Grid size={6}>
        <TextField
          name="license"
          select
          label={t("export.repositories.dryad.licenseLabel")}
          defaultValue={repo.license.licenses.length ? "0" : ""}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.dryad.licenseHelper")}
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
      <Grid size={6}>
        {/* Granting Org */}
        <Autocomplete
          id="dryad-crossref-funders-autocomplete"
          label={t("export.repositories.dryad.grantingOrgLabel")}
          options={crossrefFunders}
          getOptionLabel={(option) => option.name}
          isOptionEqualToValue={(option, value) => option.name === value.name}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleCrossrefFunderChange}
          onInputChange={handleFetchCrossrefFunder}
          sx={{ width: 300 }}
          renderInput={(params) => (
            <TextField
              {...params}
              label={t("export.repositories.dryad.grantingOrgLabel")}
              error={submitAttempt && !inputValidations.crossrefFunder}
              helperText={t("export.repositories.dryad.grantingOrgHelper")}
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
