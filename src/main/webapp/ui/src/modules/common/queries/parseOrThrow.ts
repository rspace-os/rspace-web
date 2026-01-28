import * as v from "valibot";
import { Either, Left, Right } from "purify-ts/Either";
import { ValiError } from "valibot";

/**
 * Parse data using a Valibot schema and return an Either type.
 * Returns Right with the validated data on success, or Left with an error on failure.
 *
 * @param schema - The Valibot schema to validate against
 * @param data - The data to validate
 * @returns Either<Error, T> where T is the validated type
 */
export function parse<
  TSchema extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>,
>(schema: TSchema, data: unknown): Either<Error, v.InferOutput<TSchema>> {
  try {
    const result: v.InferOutput<TSchema> = v.parse(schema, data);
    return Right<v.InferOutput<TSchema>>(result);
  } catch (error) {
    if (error instanceof ValiError) {
      const issues = error.issues as Array<{
        path?: Array<{ key: string | number }>;
        message: string;
      }>;
      const errorMessage = issues
        .map((issue) => {
          const pathStr = issue.path
            ?.map((p) => String(p.key))
            .join(".") || "";
          return pathStr ? `${pathStr}: ${issue.message}` : issue.message;
        })
        .join("; ");
      return Left<Error>(new Error(`Validation failed: ${errorMessage}`));
    }
    return Left<Error>(error instanceof Error ? error : new Error(String(error)));
  }
}

/**
 * Parse data using a Valibot schema and throw an error if validation fails.
 * This is a convenience wrapper around `parse` that unwraps the Either type
 * and throws on Left, making it easier to use in async functions.
 *
 * @param schema - The Valibot schema to validate against
 * @param data - The data to validate
 * @returns The validated and typed data
 * @throws Error if validation fails
 */
export function parseOrThrow<
  TSchema extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>,
>(schema: TSchema, data: unknown): v.InferOutput<TSchema> {
  const result: Either<Error, v.InferOutput<TSchema>> = parse(schema, data);
  return result.caseOf({
    Left: (error: Error) => {
      throw error;
    },
    Right: (validatedData: v.InferOutput<TSchema>) => validatedData,
  });
}

