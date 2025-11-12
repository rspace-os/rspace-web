import { useForm } from "@tanstack/react-form";
import { Button, Stack } from "@mui/material";
import { TextField } from "@/modules/common/forms/mui";

/**
 * Example form demonstrating TextField component usage with TanStack Form
 */
export function TextFieldExample() {
  const form = useForm({
    defaultValues: {
      username: "",
      email: "",
      password: "",
      bio: "",
    },
    onSubmit: async ({ value }) => {
      console.log("Form submitted:", value);
      // Handle form submission
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
    >
      <Stack spacing={2}>
        {/* Basic text field */}
        <form.Field name="username">
          {(field) => (
            <TextField
              field={field}
              label="Username"
              placeholder="Enter your username"
              required
            />
          )}
        </form.Field>

        {/* Email field with validation */}
        <form.Field
          name="email"
          validators={{
            onChange: ({ value }) => {
              if (!value) return "Email is required";
              if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
                return "Please enter a valid email address";
              }
              return undefined;
            },
          }}
        >
          {(field) => (
            <TextField
              field={field}
              label="Email"
              type="email"
              placeholder="user@example.com"
              required
            />
          )}
        </form.Field>

        {/* Password field with validation */}
        <form.Field
          name="password"
          validators={{
            onChange: ({ value }) => {
              if (!value) return "Password is required";
              if (value.length < 8) {
                return "Password must be at least 8 characters";
              }
              return undefined;
            },
          }}
        >
          {(field) => (
            <TextField
              field={field}
              label="Password"
              type="password"
              required
              helperText="Must be at least 8 characters"
            />
          )}
        </form.Field>

        {/* Multiline text field (textarea) */}
        <form.Field name="bio">
          {(field) => (
            <TextField
              field={field}
              label="Bio"
              placeholder="Tell us about yourself"
              multiline
              rows={4}
              helperText="Optional"
            />
          )}
        </form.Field>

        {/* Submit button */}
        <form.Subscribe
          selector={(state) => [state.canSubmit, state.isSubmitting]}
        >
          {([canSubmit, isSubmitting]) => (
            <Button
              type="submit"
              variant="contained"
              disabled={!canSubmit}
            >
              {isSubmitting ? "Submitting..." : "Submit"}
            </Button>
          )}
        </form.Subscribe>
      </Stack>
    </form>
  );
}

/**
 * Example: Async validation (e.g., checking username availability)
 */
export function AsyncValidationExample() {
  const form = useForm({
    defaultValues: {
      username: "",
    },
    onSubmit: async ({ value }) => {
      console.log("Form submitted:", value);
    },
  });

  // Simulate API call to check username availability
  const checkUsernameAvailability = async (username: string): Promise<boolean> => {
    await new Promise((resolve) => setTimeout(resolve, 500));
    // Simulate taken usernames
    return !["admin", "root", "user"].includes(username.toLowerCase());
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
    >
      <form.Field
        name="username"
        validators={{
          onChange: ({ value }) => {
            if (!value) return "Username is required";
            if (value.length < 3) return "Username must be at least 3 characters";
            return undefined;
          },
          onChangeAsync: async ({ value }) => {
            if (value.length < 3) return undefined;
            const isAvailable = await checkUsernameAvailability(value);
            return isAvailable ? undefined : "Username is already taken";
          },
          onChangeAsyncDebounceMs: 500,
        }}
      >
        {(field) => (
          <TextField
            field={field}
            label="Username"
            placeholder="Choose a username"
            required
            helperText={field.state.meta.isValidating ? "Checking availability..." : undefined}
          />
        )}
      </form.Field>

      <Button type="submit" variant="contained" sx={{ mt: 2 }}>
        Submit
      </Button>
    </form>
  );
}

/**
 * Example: Dependent fields with cross-field validation
 */
export function DependentFieldsExample() {
  const form = useForm({
    defaultValues: {
      password: "",
      confirmPassword: "",
    },
    onSubmit: async ({ value }) => {
      console.log("Form submitted:", value);
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        e.stopPropagation();
        form.handleSubmit();
      }}
    >
      <Stack spacing={2}>
        <form.Field name="password">
          {(field) => (
            <TextField
              field={field}
              label="Password"
              type="password"
              required
            />
          )}
        </form.Field>

        <form.Field
          name="confirmPassword"
          validators={{
            onChangeListenTo: ["password"],
            onChange: ({ value, fieldApi }) => {
              const password = fieldApi.form.getFieldValue("password");
              if (value !== password) {
                return "Passwords do not match";
              }
              return undefined;
            },
          }}
        >
          {(field) => (
            <TextField
              field={field}
              label="Confirm Password"
              type="password"
              required
            />
          )}
        </form.Field>

        <Button type="submit" variant="contained">
          Submit
        </Button>
      </Stack>
    </form>
  );
}

