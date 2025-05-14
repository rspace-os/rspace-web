/* eslint no-use-before-define: 0 */

import { Optional } from "./optional";

type ResultInternals<T> =
  | {
      key: "ok";
      value: T;
    }
  | {
      key: "error";
      errors: Array<Error>;
    };

/**
 * This class is for modeling the results of computations that can fail with
 * some number of different errors. It is similar to (Optional)[./optional.js]
 * but includes a list of errors when the computation fails.
 *
 * It adheres to the functor and monadic laws, supporting the composition of
 * computations that may fail. The results of multiple computations that may
 * fail can be aggregated with this data structure, such as when a parser is
 * defined as a series of possible options, or where a value is validated
 * against a set of validation rules. In those cases, if either any or all of
 * the rules, respectively, end in an OK state then the final Result will be in
 * an OK state.
 */
export default class Result<T> {
  private state: ResultInternals<T>;

  /**
   * DO NOT call directly from outside this module, call the smart constructors
   * below instead
   */
  constructor(internal: ResultInternals<T>) {
    this.state = internal;
  }

  /************************
   ** SMART CONSTRUCTORS **
   ************************/

  /*
   * These constructor functions are defined as static properties with a
   * function type and arrow-function value rather than as static methods so
   * that they may be disassociated from the class. By doing so, we can call
   * these smart constructors like so `[1,2,3].map(Result.Ok)`, as opposed to
   * having to create a temporary lambda `[1,2,3].map((x) => Result.Ok(x))`.
   * The `+` syntax makes the properties read-only and prevents some code
   * outside of this module from altering the behaviour of these functions.
   */

  static Ok: <U>(value: U) => Result<U> = (value) => {
    return new Result({ key: "ok", value });
  };

  static Error: <U>(errors: Array<Error>) => Result<U> = <U>(
    errors: Array<Error>
  ): Result<U> => {
    return new Result<U>({ key: "error", errors });
  };

  static fromNullable: <U>(
    value: U | null | undefined,
    error: Error
  ) => Result<U> = <U>(
    value: U | null | undefined,
    error: Error
  ): Result<U> => {
    if (value === null || typeof value === "undefined")
      return Result.Error([error]);
    return Result.Ok(value);
  };

  /****************
   ** PREDICATES **
   ****************/

  get isOk(): boolean {
    return this.state.key === "ok";
  }

  get isError(): boolean {
    return this.state.key === "error";
  }

  /************************************
   ** FUNCTIONS APPLIED TO OK BRANCH **
   ************************************/

  /**
   * Just like any other functor, transform the value wrapped by the OK branch.
   */
  map<U>(f: (value: T) => U): Result<U> {
    if (this.state.key === "error") return Result.Error(this.state.errors);
    return Result.Ok(f(this.state.value));
  }

  /**
   * The monadic bind operator, similar to Promise's `then` method.
   *
   * This can be thought of a conjunctive operator, as the resulting state will
   * only be OK if the result of both the original and passed computations is
   * OK.
   */
  flatMap<U>(f: (value: T) => Result<U>): Result<U> {
    if (this.state.key === "error") return Result.Error(this.state.errors);
    return f(this.state.value);
  }

  /**
   * Just like `flatMap`, but discard the result of the function and return the
   * input instead.
   */
  flatMapDiscarding<U>(f: (value: T) => Result<U>): Result<T> {
    if (this.state.key === "error") return Result.Error(this.state.errors);
    const value = this.state.value;
    return f(value).map(() => value);
  }

  /**
   * Perform a side-effect on the value if the result is in an OK state.
   * Otherwise the error(s) are discarded.
   */
  do(f: (value: T) => void): void {
    if (this.state.key === "error") return;
    f(this.state.value);
  }

  /**
   * Perform an asynchronous side-effect on the value if the result is in an OK
   * state. Otherwise the first error is returned in the rejected state of the
   * promise.
   */
  doAsync(f: (value: T) => Promise<void>): Promise<void> {
    if (this.state.key === "error") return Promise.reject(this.state.errors[0]);
    return f(this.state.value);
  }

  /***************************************
   ** FUNCTIONS APPLIED TO ERROR BRANCH **
   ***************************************/

  /**
   * In the case of the error branch, return a fixed value.
   *
   * It is useful to return null, in order to be able to render the value
   * wrapped in the OK branch as a React node.
   */
  orElse<U>(altValue: U): T | U {
    if (this.state.key === "error") return altValue;
    return this.state.value;
  }

  /**
   * Lazily produce a value that can be returned in the case of the Error
   * branch. This function can also throw the passed (or any other) error to
   * convert this errors-as-values based logic into using exception handling
   * instead.
   */
  orElseGet<U>(f: (errors: Array<Error>) => U): T | U {
    if (this.state.key === "error") return f(this.state.errors);
    return this.state.value;
  }

  /**
   * If the first computation did not succeed, then try another.
   *
   * This can be thought of as a disjunctive operator as if either computation
   * results in an OK state the output is an OK state. If both computation
   * results in an Error state then the errors are accumulated. Across
   * functional programming, this is known as the Alternative typeclass.
   */
  orElseTry<U>(func: (errors: Array<Error>) => Result<U>): Result<T | U> {
    /*
     * We take the Result apart and put it back together to satisfy to typescript
     * that `Result<T> | Result<U>` is the same as `Result<T | U>`
     */
    if (this.state.key === "error") {
      const errors = this.state.errors;
      const resultOfFunc = func(errors);
      if (resultOfFunc.state.key === "error")
        return Result.Error([...errors, ...resultOfFunc.state.errors]);
      return Result.Ok(resultOfFunc.state.value);
    }
    return Result.Ok(this.state.value);
  }

  /**
   * Simply hand-off the error handling to try/catch logic.
   * The first error will be thrown and all others discarded.
   */
  elseThrow(): T {
    if (this.state.key === "error") throw this.state.errors[0];
    return this.state.value;
  }

  /**
   * To modify/accumulate the errors collected in the Error branch.  The most
   * common reason to do this is to take a generic error message and convert it
   * into a specific one for the use-case at hand. Some tips:
   *
   *    - `AggregateError` is a useful data structure for accumulating errors;
   *       it's first argument is the list of errors and the second the typical
   *       message.
   *
   *    - The usual `Error` constructor can also take an object with key
   *      `cause` as its second parameter to keep a reference to the original
   *      error(s).
   */
  mapError(f: (errors: Array<Error>) => Error): Result<T> {
    if (this.state.key === "error") return Result.Error([f(this.state.errors)]);
    return Result.Ok(this.state.value);
  }

  /********************
   ** OUTPUT METHODS **
   ********************/

  toString(): string {
    if (this.state.key === "error") return "Result.Error";
    return `Result.Ok`;
  }

  /**
   * Converts the Result into an Optional, discarding the errors in the Error
   * branch (and returning Optional.empty), and otherwise carring the wrapped
   * value over from the Ok branch to the Optional.present one.
   */
  toOptional(): Optional<T> {
    if (this.state.key === "error") return Optional.empty();
    return Optional.present(this.state.value);
  }

  /**
   * Converts the Result into a Promise, with the OK branch of the Result
   * mapping to the resolved branch of the Promise and the Error branch mapping
   * to the rejected branch of the Promise.
   *
   * The Result type can effectively be thought of as a synchronous promise,
   * and so here we're simply lifting it up be asynchonous and thus can be
   * composed with other asynchronous logic and used with async/await syntax.
   *
   * All of the errors are captured by wrapping them in an AggregateError where
   * necessary.
   */
  toPromise(): Promise<T> {
    if (this.state.key === "error") {
      if (this.state.errors.length > 1) {
        return Promise.reject(new AggregateError(this.state.errors));
      }
      return Promise.reject(this.state.errors[0]);
    }
    return Promise.resolve(this.state.value);
  }

  /***********************
   ** AGGREGATE HELPERS **
   ***********************/

  /**
   * Accumulate up all of the values wrapped by OK states amongst the passed
   * Results. If all of the Results are in an Error state, then accumulate up
   * all of the errors. This is useful for defining parsers, where there are a
   * series of possible options, of which any one suffices; should none pass
   * then a full error report of each branch being tried can be displayed.
   */
  static any<U>(...args: ReadonlyArray<Result<U>>): Result<ReadonlyArray<U>> {
    function helper(r: Result<U>, ...rest: ReadonlyArray<Result<U>>) {
      if (rest.length > 0) {
        return (
          Result.any(rest[0], ...rest.slice(1))
            .flatMap<ReadonlyArray<U>>((restOfT: ReadonlyArray<U>) =>
              r
                // if `r` and `rest` are both OK, then concatenate,
                .map((t: U) => [t, ...restOfT])
                // else if `r` is Error and `rest` OK, then return `rest`
                .orElseTry(() => Result.Ok(restOfT))
            )
            // if `rest` is Error, then return `r`
            .orElseTry(() => r.map((t) => [t]))
        );
      }
      return r.map((t) => [t]);
    }
    return helper(...(args as [Result<U>, ...ReadonlyArray<Result<U>>]));
  }

  /**
   * A simple helper wrapped around `any` above, that should multiple succeed
   * then only the first is returned.
   */
  static first<U>(...args: ReadonlyArray<Result<U>>): Result<U> {
    return Result.any(...args).map((oks) => oks[0]);
  }

  /**
   * Accumulate up all of the values wrapped by OK states and only if they all
   * succeed then return all of the values wrapped in an OK state. Should any
   * fail, then return an Error state with the errors from all those that
   * failed. As such, we can be sure that the length of the array wrapped by
   * the OK branch will be the same as the number of Results initially passed
   * in. This is particularly useful when defining validation logic, where the
   * user should only be able to proceed with a form completion if every field
   * is in a valid state.
   */
  static all<U>(...args: ReadonlyArray<Result<U>>): Result<ReadonlyArray<U>> {
    function helper(r: Result<U>, ...rest: ReadonlyArray<Result<U>>) {
      if (typeof r === "undefined") return Result.Ok([]);
      if (rest.length > 0) {
        return Result.all(rest[0], ...rest.slice(1))
          .orElseTry<ReadonlyArray<U>>(() =>
            r.flatMap<ReadonlyArray<U>>(() => Result.Error([]))
          )
          .flatMap<ReadonlyArray<U>>((restOfT) =>
            r.map<ReadonlyArray<U>>((t: U) => [t, ...restOfT])
          );
      }
      return r.map((t) => [t]);
    }

    return helper(...(args as [Result<U>, ...ReadonlyArray<Result<U>>]));
  }

  /*
   * These helper functions transform passed functions that operate on normal
   * values into function that operate on values wrapped in Results, e.g.:
   *   const resultC = lift2((a, b) => a + b)(resultA, resultB);
   *
   * They can't all simply be replaced with a single function that takes a rest
   * argument and recurses over that array because the types of each of the
   * wrapped values is different and we want to preserve that difference. As
   * such, if the function you require is not defined then add it, and in order
   * to do so all of functions beneath it. Alternatively, you can use try-catch
   * logic and the `elseThrow` method to operate on the values held by multiple
   * Results:
   *    try {
   *      const a = resultA.elseThrow();
   *      const b = resultB.elseThrow();
   *      return Result.Ok(a + b);
   *    } catch (error) {
   *      return Result.Error(error);
   *    }
   * As you can see, this is a lot verbose for just two values but may be
   * preferable when working with lots of results than having to define `lift42`.
   *
   * It is possible that all of this deep function calls and instantiation of
   * Result may have performance implications, in which case this functional
   * approach may not be most applicable and the code should instead be
   * implemented using `null`s, exception handling, and @ts-ignore where
   * required. Don't preempt that though.
   */

  static lift<A, B>(func: (a: A) => B): (resultA: Result<A>) => Result<B> {
    return (resultA) => resultA.map(func);
  }

  static lift2<A, B, C>(
    func: (a: A, b: B) => C
  ): (resultA: Result<A>, resultB: Result<B>) => Result<C> {
    return (resultA, resultB) =>
      resultA.flatMap((a) => Result.lift((b: B) => func(a, b))(resultB));
  }

  static lift3<A, B, C, D>(
    func: (a: A, b: B, c: C) => D
  ): (resultA: Result<A>, resultB: Result<B>, resultC: Result<C>) => Result<D> {
    return (resultA, resultB, resultC) =>
      resultA.flatMap((a) =>
        Result.lift2((b: B, c: C) => func(a, b, c))(resultB, resultC)
      );
  }

  static lift4<A, B, C, D, E>(
    func: (a: A, b: B, c: C, d: D) => E
  ): (
    resultA: Result<A>,
    resultB: Result<B>,
    resultC: Result<C>,
    resultD: Result<D>
  ) => Result<E> {
    return (resultA, resultB, resultC, resultD) =>
      resultA.flatMap((a) =>
        Result.lift3((b: B, c: C, d: D) => func(a, b, c, d))(
          resultB,
          resultC,
          resultD
        )
      );
  }

  static lift5<A, B, C, D, E, F>(
    func: (a: A, b: B, c: C, d: D, e: E) => F
  ): (
    resultA: Result<A>,
    resultB: Result<B>,
    resultC: Result<C>,
    resultD: Result<D>,
    resultE: Result<E>
  ) => Result<F> {
    return (resultA, resultB, resultC, resultD, resultE) =>
      resultA.flatMap((a) =>
        Result.lift4((b: B, c: C, d: D, e: E) => func(a, b, c, d, e))(
          resultB,
          resultC,
          resultD,
          resultE
        )
      );
  }

  static lift6<A, B, C, D, E, F, G>(
    func: (a: A, b: B, c: C, d: D, e: E, f: F) => G
  ): (
    resultA: Result<A>,
    resultB: Result<B>,
    resultC: Result<C>,
    resultD: Result<D>,
    resultE: Result<E>,
    resultF: Result<F>
  ) => Result<G> {
    return (resultA, resultB, resultC, resultD, resultE, resultF) =>
      resultA.flatMap((a) =>
        Result.lift5((b: B, c: C, d: D, e: E, f: F) => func(a, b, c, d, e, f))(
          resultB,
          resultC,
          resultD,
          resultE,
          resultF
        )
      );
  }
}
