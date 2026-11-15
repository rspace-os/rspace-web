import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { StandardValidations } from "./common";
import Tags, { type Tag } from "./Tags";

/*
 * This component collects the metadata required by Zenodo to make a deposit.
 */

type ZenodoRepoArgs = {
  // Modified values are propagated up through handleChange.
  handleChange: (event: { target: { name: "title" | "description"; value: string } }) => void;

  // Callers of this component should validate the modified values and use
  // this prop to signal as to whether the corresponding field should be in an
  // error state or not.
  inputValidations: StandardValidations;

  // Error states will only be shown when this flag is true, which is to say
  // that the user has attempted to submit the form. This means we don't show
  // any error state whilst they are in the process of inputting the metadata.
  submitAttempt: boolean;

  title: string;
  description: string;

  tags: Array<Tag>;
  onTagsChange: (event: {
    target: {
      value: Array<Tag>;
    };
  }) => void;
  fetchingTags: boolean;
};

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Zenodo.
 */
export default function ZenodoRepo({
  handleChange,
  inputValidations,
  submitAttempt,
  title,
  description,
  tags,
  onTagsChange,
  fetchingTags,
}: ZenodoRepoArgs): React.ReactNode {
  const { t } = useTranslation("workspace");
  return (
    <Grid container sx={{ width: "100%" }}>
      <Grid size={12}>
        <TextField
          name="title"
          error={submitAttempt && !inputValidations.title}
          label={t("export.repositories.common.title")}
          required
          onChange={({ target: { value } }) => handleChange({ target: { name: "title", value } })}
          margin="normal"
          fullWidth
          value={title}
        />
      </Grid>
      <Grid size={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          label={t("export.repositories.common.description")}
          required
          name="description"
          multiline
          maxRows="4"
          onChange={({ target: { value } }) => handleChange({ target: { name: "description", value } })}
          margin="normal"
          fullWidth
          value={description}
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
