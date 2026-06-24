import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import Users from "../ExportRepoUser";
import type { Person, Repo, StandardValidations } from "./common";
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
          helperText={t("export.repositories.dataverse.titleHelper")}
          fullWidth
          value={title}
        />
      </Grid>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          name="description"
          label={t("export.repositories.common.descriptionLabel")}
          multiline
          maxRows="4"
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          margin="normal"
          helperText={t("export.repositories.dataverse.descriptionHelper")}
          fullWidth
          value={description}
        />
      </Grid>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.subject}
          name="subject"
          select
          label={t("export.repositories.dataverse.subjectLabel")}
          defaultValue={""}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.dataverse.subjectHelper")}
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
      {repo.metadataLanguages && repo.metadataLanguages.length > 0 && (
        <Grid size={12}>
          <TextField
            name="metadataLanguage"
            select
            label={t("export.repositories.dataverse.metadataLanguageLabel")}
            // @ts-expect-error React event handlers are not parameterised by the name prop
            onChange={handleMetadataLanguageChange}
            helperText={t("export.repositories.dataverse.metadataLanguageHelper")}
            margin="normal"
            fullWidth
            value={metadataLanguage}
          >
            {repo.metadataLanguages?.map((option) => (
              <MenuItem key={option.title} value={option.locale}>
                {option.title}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
      )}
      <Grid size={12}>
        <TextField
          name="license"
          select
          label={t("export.repositories.dataverse.licenseLabel")}
          defaultValue={0}
          // @ts-expect-error React event handlers are not parameterised by the name prop
          onChange={handleChange}
          helperText={t("export.repositories.dataverse.licenseHelper")}
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
        <Users
          updatePeople={updatePeople}
          initialPeople={contacts.concat(authors)}
          submitAttempt={submitAttempt}
          inputValidations={inputValidations}
        />
      </Grid>
      <Grid sx={{ mt: 1 }} size={12}>
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
