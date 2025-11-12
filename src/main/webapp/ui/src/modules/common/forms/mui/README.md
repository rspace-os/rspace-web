# TextField Component for TanStack Form

A Material UI v5 TextField component that seamlessly integrates with TanStack Form for type-safe form handling.

## Features

- ✅ Full TypeScript support with type inference
- ✅ Automatic error handling and display
- ✅ Support for all Material UI TextField props
- ✅ Validation error messages
- ✅ Helper text support
- ✅ Async validation support
- ✅ Cross-field validation support
- ✅ Loading states during async validation

## Installation

The component is already available in the project. Just import it:

```tsx
import { TextField } from "@/modules/common/forms/mui";
```

## Basic Usage

```tsx
import { useForm } from "@tanstack/react-form";
import { TextField } from "@/modules/common/forms/mui";
import { Button } from "@mui/material";

function MyForm() {
  const form = useForm({
    defaultValues: {
      email: "",
    },
    onSubmit: async ({ value }) => {
      console.log(value);
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void form.handleSubmit();
      }}
    >
      <form.Field name="email">
        {(field) => (
          <TextField
            field={field}
            label="Email"
            type="email"
            required
          />
        )}
      </form.Field>

      <Button type="submit">Submit</Button>
    </form>
  );
}
```

## With Validation

### Synchronous Validation

```tsx
<form.Field
  name="username"
  validators={{
    onChange: ({ value }) => {
      if (!value) return "Username is required";
      if (value.length < 3) return "Username must be at least 3 characters";
      return undefined;
    },
  }}
>
  {(field) => (
    <TextField
      field={field}
      label="Username"
      required
    />
  )}
</form.Field>
```

### Asynchronous Validation

```tsx
<form.Field
  name="email"
  validators={{
    onChange: ({ value }) => {
      if (!value) return "Email is required";
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        return "Invalid email format";
      }
      return undefined;
    },
    onChangeAsync: async ({ value }) => {
      // Check if email is available
      const isAvailable = await checkEmailAvailability(value);
      return isAvailable ? undefined : "Email is already taken";
    },
    onChangeAsyncDebounceMs: 500, // Debounce async validation
  }}
>
  {(field) => (
    <TextField
      field={field}
      label="Email"
      type="email"
      required
      helperText={
        field.state.meta.isValidating
          ? "Checking availability..."
          : "Enter your email address"
      }
    />
  )}
</form.Field>
```

### Cross-Field Validation

```tsx
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
```

## Props

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `field` | `FieldApi<...>` | required | The field instance from TanStack Form |
| `label` | `string` | undefined | Label for the text field |
| `placeholder` | `string` | undefined | Placeholder text |
| `required` | `boolean` | `false` | Whether the field is required |
| `disabled` | `boolean` | `false` | Whether the field is disabled |
| `helperText` | `string` | undefined | Custom helper text (overridden by errors) |
| ...muiProps | `TextFieldProps` | - | All other Material UI TextField props |

## Material UI Props Support

The component supports all Material UI TextField props:

```tsx
<TextField
  field={field}
  label="Bio"
  multiline
  rows={4}
  variant="outlined"
  size="small"
  margin="dense"
  InputProps={{ endAdornment: <SearchIcon /> }}
/>
```

## Field Types

### Text Input
```tsx
<TextField field={field} label="Name" />
```

### Email Input
```tsx
<TextField field={field} label="Email" type="email" />
```

### Password Input
```tsx
<TextField field={field} label="Password" type="password" />
```

### Number Input
```tsx
<TextField field={field} label="Age" type="number" />
```

### Multiline (Textarea)
```tsx
<TextField
  field={field}
  label="Description"
  multiline
  rows={4}
/>
```

### With Adornments
```tsx
<TextField
  field={field}
  label="Website"
  InputProps={{
    startAdornment: <InputAdornment position="start">https://</InputAdornment>,
  }}
/>
```

## Form Submission

### Basic Submission

```tsx
<form.Subscribe
  selector={(state) => [state.canSubmit, state.isSubmitting]}
>
  {([canSubmit, isSubmitting]) => (
    <Button
      type="submit"
      disabled={!canSubmit}
    >
      {isSubmitting ? "Submitting..." : "Submit"}
    </Button>
  )}
</form.Subscribe>
```

### With Error Handling

```tsx
const form = useForm({
  defaultValues: { email: "" },
  onSubmit: async ({ value }) => {
    try {
      await submitForm(value);
    } catch (error) {
      // Handle submission errors
      console.error(error);
    }
  },
});
```

## Validation Patterns

### Required Field
```tsx
validators={{
  onChange: ({ value }) => !value ? "This field is required" : undefined,
}}
```

### Email Validation
```tsx
validators={{
  onChange: ({ value }) => 
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) 
      ? undefined 
      : "Invalid email format",
}}
```

### Min/Max Length
```tsx
validators={{
  onChange: ({ value }) => {
    if (value.length < 3) return "Must be at least 3 characters";
    if (value.length > 50) return "Must be less than 50 characters";
    return undefined;
  },
}}
```

### Pattern Matching
```tsx
validators={{
  onChange: ({ value }) =>
    /^[a-zA-Z0-9_]+$/.test(value)
      ? undefined
      : "Only letters, numbers, and underscores allowed",
}}
```

## Examples

See `TextField.example.tsx` for complete working examples including:
- Basic form with multiple fields
- Async validation (username availability check)
- Dependent fields (password confirmation)
- Complex validation patterns

## TypeScript Support

The component is fully typed and provides excellent TypeScript support:

```tsx
// Field value is automatically inferred
const form = useForm({
  defaultValues: {
    email: "", // TypeScript knows this is a string
    age: 0,    // TypeScript knows this is a number
  },
});

// Validation functions are type-checked
validators={{
  onChange: ({ value }) => {
    // value is inferred as string based on the field
    return value.length > 0 ? undefined : "Required";
  },
}}
```

## Best Practices

1. **Always wrap forms with a `<form>` element** for proper keyboard navigation and accessibility
2. **Use `void form.handleSubmit()`** to properly handle the async return value
3. **Debounce async validations** to avoid excessive API calls
4. **Provide helpful error messages** that guide users on how to fix issues
5. **Use `helperText`** for additional context when no errors are present
6. **Mark required fields** with the `required` prop

## Troubleshooting

### Error: "Cannot read property 'value' of undefined"
Make sure you're passing the `field` prop from `form.Field`:
```tsx
<form.Field name="email">
  {(field) => <TextField field={field} />}
</form.Field>
```

### Validation not triggering
Ensure validators are defined in the `form.Field` component, not the TextField:
```tsx
<form.Field name="email" validators={{ onChange: ... }}>
  {(field) => <TextField field={field} />}
</form.Field>
```

### Type errors with FieldApi
This is expected due to TanStack Form's complex generic types. The component handles all types internally.

