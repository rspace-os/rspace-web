//@flow strict
/* eslint no-use-before-define: 0 */
import Result from "./result";

/*
 * This type defines the internal state of the class. It is not exported to
 * prevent the rest of the code base from being able to use the standard
 * constructor. It is necessary because we want to ensure that Flow can use
 * type refinement to determine the existence of the value based on the key.
 */
type OptionalInternals<T> =
  | {|
      key: "empty",
    |}
  | {|
      key: "present",
      value: T,
    |};

/**
 * Optional data structures are a common addition to most languages' standard
 * library, but which JavaScript lacks. JavaScript has optional chaining and
 * null coalescing, allowing for some of the same capabilities but, without
 * quite the same rigor and without quite as much help from the type checker.
 * For those not already familiar, an Optional data structure can essentially
 * be thought of as an array that can have at most one value: either its empty
 * and there's nothing there, or there's a single value wrapped by the data
 * structure. The advantage is has over null or such an array, is that the
 * structure can be nested in accordance with various mathematical properties,
 * with accessor methods forcing the programmer to always think carefully about
 * both possibilities.
 *
 * This implementation is mostly based on [Java]'s, but with some influence
 * from [fp-ts]'s, [Elm]'s, and [Haskell]'s.
 *
 * [Java]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
 * [fp-ts]: https://gcanti.github.io/fp-ts/modules/Option.ts.html
 * [Elm]: https://package.elm-lang.org/packages/elm/core/latest/Maybe
 * [Haskell]: https://hackage.haskell.org/package/base-4.18.0.0/docs/Prelude.html#t:Maybe
 */
export class Optional<T> {
  /*
   * This is a read-only private property
   * If you want to change it, create a new instance of the class
   * If you want to read it, use one of the methods to safely get at the value
   */
  +#state: OptionalInternals<T>;

  /*
   * DO NOT attempt to call this constructor from outside of this module. Use
   * one of the two static methods, `present` and `empty`, instead.
   */
  constructor(internal: OptionalInternals<T>) {
    this.#state = internal;
  }

  isEqual(other: Optional<T>): boolean {
    if (this.#state.key === "empty") return other.#state.key === "empty";
    return this.#state.value === other.#state.value;
  }

  /*
   * Creates a new Optional, that wraps a particular value.
   * This is the same as Java's `Optional.of`
   */
  static present<U>(value: U): Optional<U> {
    return new Optional({ key: "present", value });
  }

  /*
   * Creates a new Optional for a missing value.
   * This is the same as Java's `Optional.empty`
   */
  static empty<U>(): Optional<U> {
    return new Optional({ key: "empty" });
  }

  /*
   * There really should be little reason to use this method outside of this
   * module. Always try to use one of the other, less generic, methods first.
   */
  destruct<U>(emptyFunc: () => U, presentFunc: (T) => U): U {
    if (this.#state.key === "present") return presentFunc(this.#state.value);
    return emptyFunc();
  }

  isPresent(): boolean {
    return this.destruct(
      () => false,
      () => true
    );
  }

  isEmpty(): boolean {
    return !this.isPresent();
  }

  // altValueGetter doesn't have to return a value; it could throw an exception
  orElseGet<U>(altValueGetter: () => U): T | U {
    return this.destruct(altValueGetter, (value) => value);
  }

  /*
   * Provide a default value for an Optional
   * For example,
   * `Optional.empty().orElse(4)` will be 4
   */
  orElse<U>(altValue: U): T | U {
    return this.orElseGet(() => altValue);
  }

  /**
   * If `this` is Optional.empty then try executing a function that itself
   * returns another optional. These can be chained together to provide
   * something akin to the Alternative typeclasses, which is useful when
   * writing parsers.
   */
  orElseTry<U>(altFunc: () => Optional<U>): Optional<T | U> {
    return this.destruct(
      () => {
        const resultOfAltFunc = altFunc();
        return resultOfAltFunc.destruct(
          () => Optional.empty(),
          (value) => Optional.present(value)
        );
      },
      (value) => Optional.present(value)
    );
  }

  /*
   * Apply a function over the value that may or may not be inside.
   * `Optional.present(3).map(n => n + 1)` will be the same as `Optional.present(4)`
   * `Optional.empty().map(n => n + 1)` will be the same as `Optional.empty()`
   */
  map<U>(func: (T) => U): Optional<U> {
    return this.destruct(
      () => Optional.empty(),
      (value: T) => Optional.present(func(value))
    );
  }

  /*
   * This is just the same as `map` with no returned value -- a bit like
   * `Array.prototype.forEach`. It is needed because eslint will complain if a
   * call to `Optional.prototype.map` does not return as eslint thinks that it
   * is a call to `Array.prototype.map`.
   */
  do(func: (T) => void): void {
    return this.destruct(() => {}, func);
  }

  /*
   * For chaining together functions that return optionals, much like
   * Array.prototype.flatMap
   *
   * `Optional.present(3).flatMap(n => n % 2 === 0 ? Optional.present(n) : Optional.empty())` is the same as `Optional.empty()`
   * `Optional.present(4).flatMap(n => n % 2 === 0 ? Optional.present(n) : Optional.empty())` is the same as `Optional.present(4)`
   * `Optional.empty().flatMap(n => n % 2 === 0 ? Optional.present(n) : Optional.empty())` is the same as `Optional.empty()`
   *
   * Which is to say, that if either the original Optional or the Optional
   * return by the function are Optional.empty then Optional.empty is
   * returned, otherwise the value returned by the function is.
   */
  flatMap<U>(func: (T) => Optional<U>): Optional<U> {
    return optionalFlat(this.map(func));
  }

  /*
   * Just like Java's `Optional.ofNullable`, it turns `null` into
   * Optional.empty and any other value is wrapped inside Optional.present
   */
  static fromNullable(value: ?T): Optional<T> {
    return value === null || typeof value === "undefined"
      ? Optional.empty()
      : Optional.present(value);
  }

  toResult(onEmpty: () => Error): Result<T> {
    return this.destruct(
      () => Result.Error([onEmpty()]),
      (value) => Result.Ok(value)
    );
  }
}

/**
 * Converts a nested Optional into one of a single depth. If the outer or inner
 * Optionals are Optional.empty, then Optional.empty is returned,
 * otherwise the inner Optional.present is returned.
 *
 * Unfortunately, this can't be a method on the Optional class because the
 * input must be doubly wrapped and there's no way in Flow to say that a method
 * is only valid if the type argument of the class is of a particular shape.
 */
export function optionalFlat<T>(opt: Optional<Optional<T>>): Optional<T> {
  return opt.destruct(
    () => Optional.empty(),
    (value) => value
  );
}

/*
 * These helper functions transform passed functions that operate on normal
 * values into function that operate on values wrapped in Optionals.
 *
 * They can't all simply be replaced with a single function that takes a rest
 * argument and recurses over that array because the types of each of the
 * wrapped values is different and we want to preserve that difference. As
 * such, if the function you require is not defined then add it, and in order
 * to do so all of functions beneath it.
 *
 * It is possible that all of this deep function calls and instantiation of
 * Optionals may have performance implications, in which case this functional
 * approach may not be most applicable and the code should instead be
 * implemented using `null`s, exception handling, and flow type suppressions
 * where required. Don't preempt that though.
 */

/**
 * Lift a function that operates on a normal value into one that operates on an
 * Optional value.
 */
export function lift<A, B>(f: (A) => B, optA: Optional<A>): Optional<B> {
  return optA.map(f);
}

/**
 * Lift a function that operates on two normal values into one that operates on
 * two Optional values.
 */
export function lift2<A, B, C>(
  f: (A, B) => C,
  optA: Optional<A>,
  optB: Optional<B>
): Optional<C> {
  return optA.flatMap((a) => lift((b) => f(a, b), optB));
}

/**
 * Lift a function that operates on three normal values into one that operates
 * on three Optional values.
 */
export function lift3<A, B, C, D>(
  f: (A, B, C) => D,
  optA: Optional<A>,
  optB: Optional<B>,
  optC: Optional<C>
): Optional<D> {
  return optA.flatMap((a) => lift2((b, c) => f(a, b, c), optB, optC));
}

/**
 * Lift a function that operates on four normal values into one that operates
 * on four Optional values.
 */
export function lift4<A, B, C, D, E>(
  f: (A, B, C, D) => E,
  optA: Optional<A>,
  optB: Optional<B>,
  optC: Optional<C>,
  optD: Optional<D>
): Optional<E> {
  return optA.flatMap((a) =>
    lift3((b, c, d) => f(a, b, c, d), optB, optC, optD)
  );
}

/*
 * Other helper functions
 */

/**
 * Just like the standard square bracket syntax but rather than returning
 * undefined when the object has no such key, this function returns
 * Optional.empty
 */
export function getByKey<Key: string, Value>(
  key: Key,
  obj: { +[Key]: Value }
): Optional<Value> {
  if (key in obj) return Optional.present(obj[key]);
  return Optional.empty();
}

/*
 * A note on using Optional with React:
 *
 * It can be quite handy to use Optionally wrapped data when rendering, in
 * much the same way that the conjunctive or ternary operator are used to
 * conditionally render data that may be null. That would look something like
 * this:
 *
 * ```
 * const RenderOptionalString = ({ str }: {| str: Optional<string> |}) => (
 *   <>
 *     Value: {str
 *               .map(s => <span>{s}</span>)
 *               .orElse(<span>ERROR</span>)}
 *   </>
 * );
 * ```
 *
 * However, eslint will complain about that code because of the react/jsx-key
 * rule which warns about missing `key` attributes when mapping over arrays.
 * Eslint uses a crude heuristic for determining where a `key` attribute
 * should be added, and by the seems of it is just looking for the method
 * `map`. Here, we're mapping over a functor that only has a single value so
 * there wont ever be an issue with react keys. To stop eslint from
 * complaining a simple solution is to add a key with any arbitrary valid
 * value. For example, eslint will not complain about this:
 *
 * ```
 * const RenderOptionalString = ({ str }: {| str: Optional<string> |}) => (
 *   <>
 *     Value: {str
 *               .map(s => <span key={null}>{s}</span>)
 *               .orElse(<span>ERROR</span>)}
 *   </>
 * );
 * ```
 */
