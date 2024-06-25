//@flow strict
import { computed, makeObservable } from "mobx";
import { filterMap } from "./Util";
import { Optional } from "./optional";

/*
 * This is an extension of the standard Set data structure, with the addition
 * of methods that operate on the contents of the set in a manner similar to
 * Array (map, filter, etc.) as well as the standard collection of set
 * operations (intersection, union, etc.), and other useful functionality.
 *
 * This class should not be used wherever it is intended that the contents will
 * be observed, as the capability to make the super class's properties (e.g.
 * the actual content of the set) observable is only found in future versions
 * of the mobx library. For now, use a regular Set instead. To resolve, either
 * the library should be updated (a large amount of work) or this class should
 * implement all of Set's functionality rather than extend it, with those
 * properties then being made observable.
 *
 * This class should also not be used where memory usage is a concern as it has
 * a slightly higher memory footprint than the standard Set data structure, of
 * about 1KB (that is, in addition to the memory cost of the actual data stored
 * in the set).
 */

export default class RsSet<A> extends Set<A> {
  /*
   * Usage:
   *  new RsSet([1,2,3])    // just like Set
   *  new RsSet(null)       // null creates the empty set
   */
  constructor(init: ?Iterable<A>) {
    super(init);
    makeObservable(this, {
      isEmpty: computed,
      first: computed,
      last: computed,
      only: computed,
    });
  }

  /**
   * Checks if the set contains a given element, using a custom equality
   * function.
   *
   * If a set contains a collection of objects, then checking if the set
   * contains a particular object requires having a reference to the same
   * object in memory. A different object in memory that models the same data,
   * or even one whose properties are references to the properties of a
   * object in the set will not identify it as being in the set. This function
   * allows for one to check if a given object is in the set based on some
   * other definition of equality e.g. by comparing IDs.
   *
   * Note that Set.prototype.has MUST run in sublinear time
   * (https://262.ecma-international.org/6.0/#sec-set-objects) whereas this
   * method runs in linear time so for large enough sets this may not be
   * the best solution.
   */
  hasWithEq(elem: A, eq: (A, A) => boolean): boolean {
    for (const each of this) {
      if (eq(elem, each)) return true;
    }
    return false;
  }

  /*
   * This is for Flow's benefit, because it returns an instance of RsSet,
   *  not Set but is otherwise not necessary
   */
  add(a: A): RsSet<A> {
    super.add(a);
    return this;
  }

  get isEmpty(): boolean {
    return this.size === 0;
  }

  /*
   * The first element, according to the order in which the elements were added.
   * Usage:
   *  new RsSet([1,2]).first    // 1
   */
  get first(): A {
    return [...this][0];
  }

  /*
   * The last element, according to the order in which the elements were added.
   * Usage:
   *  new RsSet([1,2]).last    // 2
   */
  get last(): A {
    return [...this][this.size - 1];
  }

  /**
   * If the set contains just one element, then return it wrapped in
   * Optional.present.
   */
  get only(): Optional<A> {
    if (this.isEmpty) return Optional.empty();
    return Optional.present(this.first);
  }

  /*
   * Just like Array.prototype.map, however, note that unlike that
   *  function this may shrink the size of the set.
   * Usage:
   *  new RsSet([1,2]).map(x => x + 1)                    // new RsSet([2,3])
   *  new RsSet([0.25,0.5,0.75]).map(x => Math.round(x))  // new RsSet([0,1])
   */
  map<B>(f: (A) => B): RsSet<B> {
    return new RsSet([...this].map(f));
  }

  /*
   * Just like Array.prototype.filter
   * Usage:
   *  new RsSet([1,2,3]).filter(x => x < 3)    // new RsSet([1,2])
   */
  filter(f: (A) => boolean): RsSet<A> {
    return new RsSet([...this].filter(f));
  }

  /*
   * Just like normal filter, but specifically just to check
   * whether the items of `this` are instances of the passed
   * class. Useful where Flow would not recognise a normal
   * filter.
   * Usage: see test
   */
  filterClass<T>(clazz: Class<T>): RsSet<T> {
    const setOft = new RsSet<T>();
    for (const a of this) {
      if (a instanceof clazz) setOft.add(a);
    }
    return setOft;
  }

  /*
   * Just like Array.prototype.reduce
   * Usage:
   *  new RsSet([1,2,3]).reduce((acc,x) => acc + x, 0)    // 6
   */
  reduce<B>(f: (B, A) => B, init: B): B {
    return [...this].reduce(f, init);
  }

  /*
   * Just like Array.prototype.every
   * Usage:
   *  new RsSet([1,2,3]).every(x => x < 4)    // true
   */
  every(f: (A) => boolean): boolean {
    return [...this].every(f);
  }

  /*
   * Just like Array.prototype.some
   * Usage:
   *  new RsSet([1,2,3]).some(x => x > 2)    // true
   */
  some(f: (A) => boolean): boolean {
    return [...this].some(f);
  }

  /*
   * Takes the union of this and all provided sets, which is to say all of the elements that
   *  occur in any of the sets.
   * Usage:
   *  new RsSet([1,2]).union(new RsSet([2,3]))                      // new RsSet([1,2,3])
   *  new RsSet([1,2]).union(new RsSet([2,3]), new RsSet([2,4]))    // new RsSet([1,2,3,4])
   */
  union(...sets: Array<RsSet<A>>): RsSet<A> {
    const output = new RsSet(this);
    for (const set of sets) {
      for (const element of set.values()) {
        output.add(element);
      }
    }
    return output;
  }

  /*
   * Takes the intersection of this and all provided sets, which is to say all of the elements
   *  that occur in all of the sets.
   * Usage:
   *  new RsSet([1,2]).intersection(new RsSet([2,3]))                      // new RsSet([2])
   *  new RsSet([1,2]).intersection(new RsSet([2,3]), new RsSet([2,4]))    // new RsSet([2])
   */
  intersection(...sets: Array<RsSet<A>>): RsSet<A> {
    const allSets = [this, ...sets];
    return new RsSet<A>()
      .union(...allSets)
      .filter((e) => allSets.every((s) => s.has(e)));
  }

  /*
   * Subtracts the elements of a provided set from the elements of this.
   * Usage:
   *  new RsSet([1,2]).subtract(new RsSet([2,3]))    // new RsSet([1])
   */
  subtract(s: RsSet<A>): RsSet<A> {
    return new RsSet([...this].filter((e) => !s.has(e)));
  }

  /*
   * Finds the elements that occur in either set but not both.
   * Usage:
   *  new RsSet([1,2]).disjunctiveUnion(new RsSet([2,3]))  // new RsSet([1,3])
   */
  disjunctiveUnion(s: RsSet<A>): RsSet<A> {
    return this.union(s).subtract(this.intersection(s));
  }

  /*
   * Applies a mapping function to the contents of this, subtract the
   *  contents of a given set, and then returns the original elements of
   *  this that correspond to the result of the subtraction. Note: it is
   *  critically important that the mapping function, f, is a one-to-one
   *  function e.g. an ID <-> Object
   * Usage:
   *  const unwantedIds = new RsSet([1,2])
   *  const thingsWithIds = new RsSet([{id: 1}, {id: 3}])
   *  thingsWithIds.subtractMap(x => x.id, unwantedIds)
   *    // new RsSet([{id: 3}])
   */
  subtractMap<B>(f: (A) => B, s: RsSet<B>): RsSet<A> {
    const map = new Map([...this].map((e) => [f(e), e]));
    const set = new RsSet([...map.keys()]).subtract(s);
    return new RsSet([...filterMap(map, (k) => set.has(k)).values()]);
  }

  /*
   * Applies a mapping function to the contents of this, finds the
   *  intersection with a given set, and then returns the original elements
   *  of this that correspond to the result of the intersection. Note: it
   *  is critically important that the mapping function, f, is a one-to-one
   *  function e.g. an ID <-> Object
   * Usage:
   *  const wantedIds = new RsSet([1,2])
   *  const thingsWithIds = new RsSet([{id: 1}, {id: 3}])
   *  thingsWithIds.intersectionMap(x => x.id, wantedIds)
   *    // new RsSet([{id: 1}])
   */
  intersectionMap<B>(f: (A) => B, s: RsSet<B>): RsSet<A> {
    const map = new Map([...this].map((e) => [f(e), e]));
    const set = new RsSet([...map.keys()]).intersection(s);
    return new RsSet([...filterMap(map, (k) => set.has(k)).values()]);
  }

  /*
   * Takes all of the elements of `this` set for which the `eq` function
   * returns true when called with elements of the passed set `s`. The elements
   * of `s` are never in the returned set.
   */
  unionWithEq(s: RsSet<A>, eq: (A, A) => boolean): RsSet<A> {
    const result = new RsSet<A>();
    outerA: for (const elementOfThis of this) {
      for (const alreadyAdded of result) {
        if (eq(elementOfThis, alreadyAdded)) continue outerA;
      }
      result.add(elementOfThis);
    }
    outerB: for (const elementOfS of s) {
      for (const alreadyAdded of result) {
        if (eq(elementOfS, alreadyAdded)) continue outerB;
      }
      result.add(elementOfS);
    }
    return result;
  }

  subtractWithEq(s: RsSet<A>, eq: (A, A) => boolean): RsSet<A> {
    const result = new RsSet<A>();
    outer: for (const elementOfThis of this) {
      for (const elementOfS of s) {
        if (eq(elementOfThis, elementOfS)) continue outer;
      }
      result.add(elementOfThis);
    }
    return result;
  }

  /*
   * Finds whether all of the elements in this are also in another given set.
   * Usage:
   *  new RsSet([1,2]).isSubsetOf(new RsSet([1,2,3]))    // true
   */
  isSubsetOf(s: RsSet<A>): boolean {
    return this.intersection(s).size === this.size;
  }

  /*
   * Finds whether all of the elements in a given set are also in this.
   * Usage:
   *  new RsSet([1,2,3]).isSupersetOf(new RsSet([1,2]))    // true
   */
  isSupersetOf(s: RsSet<A>): boolean {
    return this.intersection(s).size === s.size;
  }

  /*
   * Finds whether this and a given set contain all, and only all, the same
   *  elements.
   * Usage:
   *  new RsSet([1,2]).isSame(new RsSet([1,2]))    // true
   */
  isSame(s: RsSet<A>): boolean {
    return this.size === s.size && this.isSubsetOf(s) && this.isSupersetOf(s);
  }

  /*
   * Converts this to an array, utilising a sort function to specify the
   *  ordering. If a sort function is not provided then the order will be
   *  based on when they were added to the set.
   * Usage:
   *  new RsSet([1,3,2]).toArray((x,y) => x - y)            // [1,2,3]
   *  new RsSet([1,2]).union(new RsSet([2,3])).toArray()    // [1,2,3]
   *  new RsSet([2,3]).union(new RsSet([1,2])).toArray()    // [2,3,1]
   */
  toArray(f: ?(A, A) => number): Array<A> {
    const array = [...this];
    if (f) array.sort(f);
    return array;
  }

  /*
   * Map over the set, keeping only those elements which when applied to the
   *  passed function are wrapped in an Optional.present. Note that the type
   *  variable changes from `A` to `B`, allowing this function to both filter
   *  the set and shrink the size of the type.
   * Usage:
   *  const withFilter: RsSet<number | string> = new RsSet([1, "foo"]).filter(
   *    (x) => x instanceof Number
   *  ); // new RsSet([1])
   *  const withMapOptional: RsSet<number> = new RsSet([1, "foo"]).mapOptional(
   *    (x) => (x instanceof Number ? Optional.present(x) : Optional.empty())
   *  ); // new RsSet([1])
   */
  mapOptional<B>(f: (A) => Optional<B>): RsSet<B> {
    // These classes are here solely so that filterClass can be used
    class Present<T> {
      value: T;
      constructor(value: T) {
        this.value = value;
      }
    }
    class Empty {}

    const setOfOptionals: RsSet<Optional<B>> = this.map(f);
    const setOfPresentOrEmpty: RsSet<Present<B> | Empty> = setOfOptionals.map(
      (opt) =>
        opt.destruct(
          () => new Empty(),
          (v) => new Present(v)
        )
    );
    const setOfPresent: RsSet<Present<B>> =
      setOfPresentOrEmpty.filterClass(Present);
    return setOfPresent.map((opt) => opt.value);
  }
}

/*
 * Turns a set of sets into a flat set by taking the intersection of all of the sets,
 *  similar to Array.prototype.flatten, but with intersection instead of appending.
 * Usage:
 *  flattenWithIntersection(new RsSet([new RsSet([1,2]), new RsSet([2,3])]))    // new RsSet([2])
 */
export const flattenWithIntersection = <A>(
  setOfSets: RsSet<RsSet<A>>
): RsSet<A> => {
  if (setOfSets.isEmpty) return new RsSet();
  const [first, ...rest] = setOfSets.toArray();
  return first.intersection(...rest);
};

/*
 * Does the same as flattenWithIntersection, but with a custom equality
 * function rather than relying on how `Set` is defined.
 */
export const flattenWithIntersectionWithEq = <A>(
  setOfSets: RsSet<RsSet<A>>,
  eqFunc: (A, A) => boolean
): RsSet<A> => {
  const allElements = flattenWithUnion(setOfSets);
  const intersection = new RsSet<A>();

  allElementsLoop: for (const element of allElements) {
    // check if `element` is already in `ret`
    for (const alreadyAdded of intersection) {
      if (eqFunc(alreadyAdded, element)) continue allElementsLoop;
    }

    // check if `element` is in all `setOfSets`
    eachSetLoop: for (const set of setOfSets) {
      for (const elem of set) {
        if (eqFunc(elem, element)) {
          // if in this set, then check next set
          continue eachSetLoop;
        }
      }
      // if not in this set then move to next element as it has to be in every set
      continue allElementsLoop;
    }

    intersection.add(element);
  }
  return intersection;
};

/*
 * Turns a set of sets into a flat set by taking the union of all of the sets,
 *  similar to Array.prototype.flatten, but with union instead of appending.
 * Usage:
 *  flattenWithUnion(new RsSet([new RsSet([1,2]), new RsSet([2,3])]))    // new RsSet([1,2,3])
 */
export const flattenWithUnion = <A>(setOfSets: RsSet<RsSet<A>>): RsSet<A> => {
  if (setOfSets.isEmpty) return new RsSet();
  const [first, ...rest] = setOfSets.toArray();
  return first.union(...rest);
};

/*
 * Performs union operation on a list of sets, where uniquness is defined by a provided function,
 *  rather than based on === which considers all objects unique.
 * Example:
 *  unionWith(x => x.id, [
 *    new RsSet([{id: 1, foo: "A"}]),
 *    new RsSet([{id: 1, foo: "B"}, {id: 2, foo: "C"}])
 *  ])  // new RsSet([{id: 1, foo: "A"}, {id: 2, foo: "C"}])
 */
export const unionWith = <A, B>(
  eqFunc: (A) => B,
  sets: Array<RsSet<A>>
): RsSet<A> => {
  const uniqueBs = new RsSet<B>();
  const uniqueAs = new RsSet<A>();
  for (const set of sets) {
    for (const element of set.values()) {
      if (!uniqueBs.has(eqFunc(element))) {
        uniqueBs.add(eqFunc(element));
        uniqueAs.add(element);
      }
    }
  }
  return uniqueAs;
};

/*
 * Takes a value that may or may not be null/undefined and converts it into
 *  either an empty or singleton set.
 * Example:
 *  nullishToSingleton(null)   // new RsSet()
 *  nullishToSingleton(3)      // new RsSet([3])
 */
export const nullishToSingleton = <A>(a: ?A): RsSet<A> =>
  a === null || typeof a === "undefined" ? new RsSet<A>() : new RsSet([a]);
