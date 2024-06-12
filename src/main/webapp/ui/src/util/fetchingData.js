//@flow strict

/*
 * These are just some generic helper functions and types to aid with ensuring
 * that the various states that the system can be in when fetching data --
 * success, loading, and error -- are always correctly covered.
 */

import Result from "./result";

export type Fetched<A> =
  | {| tag: "loading" |}
  | {| tag: "error", error: string |}
  | {| tag: "success", value: A |};

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

export function map<A, B>(fetched: Fetched<A>, func: (A) => B): Fetched<B> {
  if (fetched.tag === "success")
    return { tag: "success", value: func(fetched.value) };
  return fetched;
}

export function getSuccessValue<A>(fetched: Fetched<A>): Result<A> {
  if (fetched.tag === "loading") return Result.Error([new Error("loading")]);
  if (fetched.tag === "error")
    // $FlowExpectedError[extra-arg] Flow does not supprt cause parameter
    return Result.Error([new Error("error", { cause: fetched.error })]);
  return Result.Ok(fetched.value);
}
