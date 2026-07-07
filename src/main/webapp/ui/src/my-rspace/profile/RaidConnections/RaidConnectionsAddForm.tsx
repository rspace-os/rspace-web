import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import MuiTextField from "@mui/material/TextField";
import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import * as v from "valibot";
import { useAddRaidIdentifierMutation } from "@/modules/raid/mutations";
import { raidQueryKeys, useGetAvailableRaidIdentifiersAjaxQuery } from "@/modules/raid/queries";

// Schema for a RAiD option
const RaidOptionSchema = v.object({
  label: v.string(),
  raidServerAlias: v.string(),
  raidIdentifier: v.pipe(
    v.string(),
    v.nonEmpty("RAiD identifier is required"),
    v.minLength(3, "RAiD identifier must be at least 3 characters"),
  ),
});

const RaidConnectionsFormSchema = v.object({
  raidOption: v.pipe(
    v.nullable(RaidOptionSchema),
    v.check((val) => val !== null, "RAiD identifier is required"),
  ),
});

type RaidConnectionsAddFormValues = v.InferOutput<typeof RaidConnectionsFormSchema>;
type RaidOption = v.InferOutput<typeof RaidOptionSchema>;

interface RaidConnectionsAddFormProps {
  groupId: string;
  handleCloseForm: () => void;
}

const defaultValues: RaidConnectionsAddFormValues = {
  raidOption: null,
};

const RaidConnectionsAddForm = ({ groupId, handleCloseForm }: RaidConnectionsAddFormProps) => {
  const { data } = useGetAvailableRaidIdentifiersAjaxQuery();
  const queryClient = useQueryClient();
  const mutation = useAddRaidIdentifierMutation({ groupId });

  const form = useForm({
    defaultValues,
    validators: {
      onChange: RaidConnectionsFormSchema,
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
    return <>Error loading RAiD identifier options: {data.errorMsg}</>;
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
              noOptionsText="No valid available RAiD found, or the RAiD has been used by another project group."
              renderInput={(params) => (
                <MuiTextField
                  {...params}
                  label="RAiD Identifier"
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
                {isSubmitting ? "Adding..." : "Add"}
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
                Cancel
              </Button>
            </>
          )}
        </form.Subscribe>
      </Stack>
    </Box>
  );
};

export default RaidConnectionsAddForm;
