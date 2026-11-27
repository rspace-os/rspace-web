/**
 * The messages of a TanStack Form field's validation errors, which are
 * Standard Schema issues when the form validates with a Valibot schema.
 */
export function fieldErrorText(errors: ReadonlyArray<unknown>): string {
  return errors
    .map((error) =>
      typeof error === "object" && error !== null && "message" in error ? String(error.message) : String(error),
    )
    .join(" ");
}
