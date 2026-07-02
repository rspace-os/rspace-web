// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Box, Button, TextField as MuiTextField, Stack } from "@mui/material";
import Autocomplete from "@mui/material/Autocomplete";
import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { useAddRaidIdentifierMutation } from "@/modules/raid/mutations";
import { raidQueryKeys, useGetAvailableRaidIdentifiersAjaxQuery } from "@/modules/raid/queries";

// Schema for a RAiD option
const createRaidOptionSchema = ({
  identifierMinLength,
  identifierRequired,
}: {
  identifierMinLength: string;
  identifierRequired: string;
}) =>
  v.object({
    label: v.string(),
    raidServerAlias: v.string(),
    raidIdentifier: v.pipe(v.string(), v.nonEmpty(identifierRequired), v.minLength(3, identifierMinLength)),
  });

const createRaidConnectionsFormSchema = (messages: { identifierMinLength: string; identifierRequired: string }) =>
  v.object({
    raidOption: v.pipe(
      v.nullable(createRaidOptionSchema(messages)),
      v.check((val) => val !== null, messages.identifierRequired),
    ),
  });

type RaidConnectionsAddFormValues = {
  raidOption: RaidOption | null;
};

type RaidOption = v.InferOutput<ReturnType<typeof createRaidOptionSchema>>;

interface RaidConnectionsAddFormProps {
  groupId: string;
  handleCloseForm: () => void;
}

const defaultValues: RaidConnectionsAddFormValues = {
  raidOption: null,
};

const RaidConnectionsAddForm = ({ groupId, handleCloseForm }: RaidConnectionsAddFormProps) => {
  const { t } = useTranslation("common");
  const { data } = useGetAvailableRaidIdentifiersAjaxQuery();
  const queryClient = useQueryClient();
  const mutation = useAddRaidIdentifierMutation({ groupId });
  const raidConnectionsFormSchema = createRaidConnectionsFormSchema({
    identifierMinLength: t("profile.raidConnections.validation.identifierMinLength"),
    identifierRequired: t("profile.raidConnections.validation.identifierRequired"),
  });

  const form = useForm({
    defaultValues,
    validators: {
      onChange: raidConnectionsFormSchema,
    },
    onSubmit: async ({ value }) => {
      if (!value.raidOption) {
        throw new Error("RAiD option is required");
      }

      await mutation.mutateAsync({
        raidServerAlias: value.raidOption.raidServerAlias,
        raidIdentifier: value.raidOption.raidIdentifier,
      });

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
    label: `${option.raidTitle} (${option.raidIdentifier})`,
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
                  helperText={field.state.meta.errors.map(String).join(", ") || mutation.error?.message}
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
