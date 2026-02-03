import { useForm } from "@tanstack/react-form";
import * as v from "valibot";
import { Button, Stack, TextField as MuiTextField } from "@mui/material";
import Autocomplete from "@mui/material/Autocomplete";
import {
  raidQueryKeys,
  useGetAvailableRaIDIdentifiersAjaxQuery,
} from "@/modules/raid/queries";
import { useAddRaidIdentifierMutation } from "@/modules/raid/mutations";
import { useQueryClient } from "@tanstack/react-query";

// Schema for a RaID option
const RaidOptionSchema = v.object({
  label: v.string(),
  raidServerAlias: v.string(),
  raidIdentifier: v.pipe(
    v.string(),
    v.nonEmpty("RaID identifier is required"),
    v.minLength(3, "RaID identifier must be at least 3 characters")
  ),
});

const RaidConnectionsFormSchema = v.object({
  raidOption: v.pipe(
    v.nullable(RaidOptionSchema),
    v.check((val) => val !== null, "RaID identifier is required")
  ),
});

type RaidConnectionsAddFormValues = v.InferOutput<typeof RaidConnectionsFormSchema>;
type RaidOption = v.InferOutput<typeof RaidOptionSchema>;

interface RaidConnectionsAddFormProps {
  groupId: string;
  handleCloseForm: () => void;
}

const RaidConnectionsAddForm = ({ groupId, handleCloseForm }: RaidConnectionsAddFormProps) => {
  const { data } = useGetAvailableRaIDIdentifiersAjaxQuery();
  const queryClient = useQueryClient();
  const mutation = useAddRaidIdentifierMutation({ groupId });

  const form = useForm({
    defaultValues: {
      raidOption: null,
    } as RaidConnectionsAddFormValues,
    validators: {
      onChange: RaidConnectionsFormSchema,
    },
    onSubmit: async ({ value }) => {
      if (!value.raidOption) {
        throw new Error("RaID option is required");
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
    return <>Error loading RaID identifier options: {data.errorMsg}</>;
  }

  const options = data.data.map((option) => ({
    label: `${option.raidTitle} (${option.raidIdentifier})`,
    raidServerAlias: option.raidServerAlias,
    raidIdentifier: option.raidIdentifier,
  }));

  return (
    <form
      style={{ display: "contents" }}
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
                option.raidIdentifier === value.raidIdentifier &&
                option.raidServerAlias === value.raidServerAlias
              }
              noOptionsText="No valid available RaID found, or the RaID has been used by another project group."
              renderInput={(params) => (
                <MuiTextField
                  {...params}
                  label="RaID Identifier"
                  required
                  error={field.state.meta.errors.length > 0 || mutation.isError}
                  helperText={
                    field.state.meta.errors.map(String).join(", ") ||
                    mutation.error?.message
                  }
                />
              )}
            />
          )}
        </form.Field>

        <form.Subscribe
          selector={(state) => [
            state.canSubmit,
            state.isPristine,
            state.isSubmitting,
          ]}
        >
          {([canSubmit, isPristine, isSubmitting]) => (
            <>
              <Button
                type="submit"
                variant="outlined"
                color="primary"
                size="small"
                disabled={!canSubmit || isPristine}
              >
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
    </form>
  );
};

export default RaidConnectionsAddForm;
