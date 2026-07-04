// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Box, Button, TextField as MuiTextField, Stack } from "@mui/material";
import Autocomplete from "@mui/material/Autocomplete";
import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { lazyT } from "@/modules/common/i18n/lazyT";
import { formatList } from "@/modules/common/i18n/listFormat";
import { useAddRaidIdentifierMutation } from "@/modules/raid/mutations";
import { raidQueryKeys, useGetAvailableRaidIdentifiersAjaxQuery } from "@/modules/raid/queries";
import { formatRaidConnectionLabel } from "@/my-rspace/profile/RaidConnections/formatRaidConnectionLabel";

// Schema for a RAiD option
const raidOptionSchema = v.object({
  label: v.string(),
  raidServerAlias: v.string(),
  raidIdentifier: v.pipe(
    v.string(),
    v.nonEmpty(lazyT("common:profile.raidConnections.validation.identifierRequired")),
    v.minLength(3, lazyT("common:profile.raidConnections.validation.identifierMinLength")),
  ),
});

const raidConnectionsFormSchema = v.object({
  raidOption: v.pipe(
    v.nullable(raidOptionSchema),
    v.check((val) => val !== null, lazyT("common:profile.raidConnections.validation.identifierRequired")),
  ),
});

type RaidConnectionsAddFormValues = {
  raidOption: RaidOption | null;
};

type RaidOption = v.InferOutput<typeof raidOptionSchema>;

interface RaidConnectionsAddFormProps {
  groupId: string;
  handleCloseForm: () => void;
}

const defaultValues: RaidConnectionsAddFormValues = {
  raidOption: null,
};

const RaidConnectionsAddForm = ({ groupId, handleCloseForm }: RaidConnectionsAddFormProps) => {
  const { t, i18n } = useTranslation("common");
  const { data } = useGetAvailableRaidIdentifiersAjaxQuery();
  const queryClient = useQueryClient();
  const mutation = useAddRaidIdentifierMutation({ groupId });

  const form = useForm({
    defaultValues,
    validators: {
      onChange: raidConnectionsFormSchema,
    },
    onSubmit: async ({ value }) => {
      if (!value.raidOption) {
        throw new Error("RAiD option is required");
      }

      try {
        await mutation.mutateAsync({
          raidServerAlias: value.raidOption.raidServerAlias,
          raidIdentifier: value.raidOption.raidIdentifier,
        });
      } catch {
        // mutation.isError/error already drive the error UI below; stop the
        // submit flow here instead of invalidating queries and closing the form.
        return;
      }

      await queryClient.invalidateQueries({
        queryKey: raidQueryKeys.availableRaidIdentifiers(),
      });

      handleCloseForm();
    },
  });

  if (!data.success) {
    return <>{t("profile.raidConnections.loadOptionsError", { error: data.errorMsg })}</>;
  }

  const options: Array<RaidOption> = data.data.map((option) => ({
    label: formatRaidConnectionLabel(option),
    raidServerAlias: option.raidServerAlias,
    raidIdentifier: option.raidIdentifier,
  }));

  return (
    <Box
      component="form"
      sx={{ display: "contents" }}
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        void form.handleSubmit();
      }}
    >
      <Stack spacing={2} direction="row" sx={{ width: "100%" }}>
        <form.Field name="raidOption">
          {(field) => (
            <Autocomplete<RaidOption>
              size="small"
              sx={{ flexGrow: "1" }}
              options={options}
              value={field.state.value}
              onChange={(_, newValue) => {
                field.handleChange(newValue);
              }}
              isOptionEqualToValue={(option, value) =>
                option.raidIdentifier === value.raidIdentifier && option.raidServerAlias === value.raidServerAlias
              }
              noOptionsText={t("profile.raidConnections.noOptions")}
              renderInput={(params) => (
                <MuiTextField
                  {...params}
                  label={t("profile.raidConnections.identifier")}
                  required
                  error={field.state.meta.errors.length > 0 || mutation.isError}
                  helperText={
                    formatList(field.state.meta.errors.map(String), i18n.resolvedLanguage ?? i18n.language) ||
                    mutation.error?.message
                  }
                />
              )}
            />
          )}
        </form.Field>

        <form.Subscribe selector={(state) => [state.canSubmit, state.isPristine, state.isSubmitting]}>
          {([canSubmit, isPristine, isSubmitting]) => (
            <>
              <Button type="submit" variant="outlined" color="primary" size="small" disabled={!canSubmit || isPristine}>
                {isSubmitting ? t("profile.raidConnections.adding") : t("actions.add")}
              </Button>
              <Button
                type="button"
                variant="outlined"
                color="secondary"
                disabled={isSubmitting}
                onClick={() => {
                  form.reset();
                  handleCloseForm();
                }}
              >
                {t("actions.cancel")}
              </Button>
            </>
          )}
        </form.Subscribe>
      </Stack>
    </Box>
  );
};

export default RaidConnectionsAddForm;
