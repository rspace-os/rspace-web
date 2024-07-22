//@flow

/*
 * This file defines a Flow library definition for the fast-check testing
 * library, which doesn't expose type definitions. These definitions have been
 * derived from the library's documentation, https://fast-check.dev/.
 */
declare module "fast-check" {
  /*
   * At a fundamental level, the fast-check library defines an algebra for
   * constructing, manipulating, and running tests against the concept of an
   * arbibrary value of some type.
   */
  declare type Arbitrary<T> = {|
    map<U>((T) => U): Arbitrary<U>,
    chain<U>((T) => Arbitrary<U>): Arbitrary<U>,
    filter((T) => boolean): Arbitrary<T>,
  |};

  /*
   * === Arbitraries ===
   *
   * The arbitraries allow for the construction and combination of instances of
   * Arbitrary.
   * For more info, see https://fast-check.dev/docs/core-blocks/arbitraries/
   */

  declare type Size = "xsmall" | "small" | "medium" | "large" | "xlarge";

  // Boolean
  declare function boolean(): Arbitrary<boolean>;

  // Numeric
  declare function integer(
    ?{|
      min?: number,
      max?: number,
    |}
  ): Arbitrary<number>;
  declare function nat(?number): Arbitrary<number>;
  declare function float(?{| min?: number, max?: number |}): Arbitrary<number>;

  // Single character
  declare function char(): Arbitrary<string>;
  declare function unicode(): Arbitrary<string>;

  // String
  declare function string(
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<string>;
  declare function unicodeString(
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<string>;
  declare function json(): Arbitrary<string>;
  declare function unicodeJson(): Arbitrary<string>;
  declare function webUrl(
    ?{|
      withQueryParameters?: boolean,
      withFragments?: boolean,
    |}
  ): Arbitrary<string>;
  declare function emailAddress(): Arbitrary<string>;

  // Date
  declare function date(
    ?{|
      min?: Date,
      max?: Date,
      noInvalidDate?: boolean,
    |}
  ): Arbitrary<Date>;

  // Simple Combinators
  declare function constant<A>(A): Arbitrary<A>;
  declare function constantFrom<A>(...Array<A>): Arbitrary<A>;
  declare function oneof<A>(...Array<Arbitrary<A>>): Arbitrary<A>;
  declare function option<A>(
    Arbitrary<A>,
    ?{|
      nil: null | typeof undefined | typeof Number.NaN,
    |}
  ): Arbitrary<?A>;

  // Array Combinators
  declare function tuple<A, B>(Arbitrary<A>, Arbitrary<B>): Arbitrary<[A, B]>;
  declare function tuple<A, B, C>(
    Arbitrary<A>,
    Arbitrary<B>,
    Arbitrary<C>
  ): Arbitrary<[A, B, C]>;
  declare function tuple<A, B, C, D>(
    Arbitrary<A>,
    Arbitrary<B>,
    Arbitrary<C>,
    Arbitrary<D>
  ): Arbitrary<[A, B, C, D]>;
  declare function array<A>(
    Arbitrary<A>,
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<Array<A>>;
  declare function uniqueArray<A>(
    Arbitrary<A>,
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<Array<A>>;
  declare function subarray<A>(
    Array<A>,
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<Array<A>>;
  declare function shuffledSubarray<A>(
    Array<A>,
    ?{|
      minLength?: number,
      maxLength?: number,
    |}
  ): Arbitrary<Array<A>>;

  // Object Combinators
  declare function dictionary<A, B>(
    Arbitrary<A>,
    Arbitrary<B>
  ): Arbitrary<{ [A]: B }>;
  declare function record<A: {}>({[keyA in keyof A]: Arbitrary<A[keyA]>}): Arbitrary<A>;
  declare function anything(): Arbitrary<mixed>;

  // Function Combinators
  declare function func<Args, Out>(
    Arbitrary<Out>
  ): Arbitrary<(...args: Array<Args>) => Out>;

  /*
   * === Runners ===
   *
   * The runners take Arbitraries and execute tests against them, generating a
   * large number of values of the given type and checking that they all pass
   * the given test function.
   * For more info, see https://fast-check.dev/docs/core-blocks/runners/ and
   * https://fast-check.dev/docs/core-blocks/properties/
   */

  declare type PropertyCheck = {|
    beforeEach: (() => void) => PropertyCheck,
    afterEach: (() => void) => PropertyCheck,
  |};
  declare type AsyncPropertyCheck = {|
    beforeEach: (() => void) => AsyncPropertyCheck,
    afterEach: (() => void) => AsyncPropertyCheck,
  |};

  declare function assert(
    PropertyCheck | AsyncPropertyCheck,
    ?{|
      numRuns?: number,
    |}
  ): void;

  declare function property<A>(Arbitrary<A>, (A) => void): PropertyCheck;
  declare function property<A, B>(
    Arbitrary<A>,
    Arbitrary<B>,
    (A, B) => void
  ): PropertyCheck;
  declare function property<A, B, C>(
    Arbitrary<A>,
    Arbitrary<B>,
    Arbitrary<C>,
    (A, B, C) => void
  ): PropertyCheck;
  declare function asyncProperty<A>(
    Arbitrary<A>,
    (A) => Promise<void>
  ): AsyncPropertyCheck;
  declare function asyncProperty<A, B>(
    Arbitrary<A>,
    Arbitrary<B>,
    (A, B) => Promise<void>
  ): AsyncPropertyCheck;
  declare function property<A, B, C>(
    Arbitrary<A>,
    Arbitrary<B>,
    Arbitrary<C>,
    (A, B, C) => Promise<void>
  ): AsyncPropertyCheck;
  declare function pre(boolean): void;

  /*
   * === Model Testing ===
   *
   * Model based testing is designed to ease testing of UIs or state machines.
   *
   * The idea of the approach is to define commands that could be applied to
   * the system. The framework then picks zero, one or more commands and run
   * them sequentially if they can be executed on the current state.
   *
   * For more info, see https://fast-check.dev/docs/advanced/model-based-testing/
   */

  declare interface Command<Model, Real> {
    check(Model): boolean;
    run(Model, Real): void | Promise<void>;
    toString(): string;
  }

  declare function commands<Model, Real>(
    Array<Arbitrary<Command<Model, Real>>>,
    ?{ size?: Size }
  ): Arbitrary<Array<Command<Model, Real>>>;

  declare type ModelRunSetup<Model, Real> = () => { model: Model, real: Real };
  declare function modelRun<Model, Real>(
    ModelRunSetup<Model, Real>,
    Array<Command<Model, Real>>
  ): void;
  declare function asyncModelRun<Model, Real>(
    ModelRunSetup<Model, Real>,
    Array<Command<Model, Real>>
  ): Promise<void>;
}
