//@flow strict

/*
 * These are just some generic helper functions and types to aid with ensuring
 * that the various states that the system can be in when fetching data --
 * success, loading, and error -- are always correctly covered.
 */

import Result from "./result";

/**
 * Represents the state of a network call for data.
 */
export type Fetched<A> =
  | {| tag: "loading" |}
  | {| tag: "error", error: string |}
  | {| tag: "success", value: A |};

/**
 * Decompose the state of some fetched data, considering all of the possible
 * states.
 */
export function match<A, B>(
  fetched: Fetched<A>,
  matcher: {|
    loading: () => B,
    error: (string) => B,
    success: (A) => B,
  |}
): B {
  if (fetched.tag === "loading") return matcher.loading();
  if (fetched.tag === "error") return matcher.error(fetched.error);
  return matcher.success(fetched.value);
}

/**
 * Apply a function to the value some fetched data, assuming that is has
 * succeessfully been fetched. This useful when rendering some data that has
 * been fetched, by applying a function that returns some JSX, with the result
 * being as if we had fetched the JSX directly.
 */
export function map<A, B>(fetched: Fetched<A>, func: (A) => B): Fetched<B> {
  if (fetched.tag === "success")
    return { tag: "success", value: func(fetched.value) };
  return fetched;
}

/**
 * Transform the wrapped fetched data into a Result, collapsing the loading and
 * error states into the Result's Error type. For those intersested functional
 * programming, this is a Natural Transformation.
 */
export function getSuccessValue<A>(fetched: Fetched<A>): Result<A> {
  if (fetched.tag === "loading") return Result.Error([new Error("loading")]);
  if (fetched.tag === "error")
    return Result.Error([new Error("error", { cause: fetched.error })]);
  return Result.Ok(fetched.value);
}

/**
 * Check if a fetched data is in the loading state.
 */
export function isLoading<A>(fetched: Fetched<A>): boolean {
  if (fetched.tag === "loading") return true;
  return false;
}
