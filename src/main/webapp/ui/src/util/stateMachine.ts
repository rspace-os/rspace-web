import { action, observable, computed, makeObservable } from "mobx";
import RsSet from "./set";

type TransitionMapping<T extends string> = { [state in T]: Set<T> };
type TransitionCallbackReturn<T> = {
  state: T;
  data: unknown;
};
type TransitionCallback<T> = {
  before: Set<T>;
  after: Set<T>;
  callback: (
    oldState: TransitionCallbackReturn<T>,
    newState: TransitionCallbackReturn<T>
  ) => void;
};

/**
 * This class implements a simple finite state automata (state machine).
 *
 * An example of how to use:
 *
 * const SM = new StateMachine<"one" | "two" | "three">({
 *    one: new Set(['two']),
 *    two: new Set(['three']),
 *    three: new Set(['one']),
 * }, "one", x => x);
 *
 *                                 // currentState is initially "one"
 * SM.addTransitionCallback(
 *   new Set([]),
 *   new Set(["two"]),
 *   () => console.log("now two")
 * );                              // setup a transition callback for moving to "two"
 * SM.isCurrentState("one");       // true
 * SM.transitionTo("two");         // currentState is now "two", also logged "now two"
 * SM.isCurrentState("two");       // true
 * SM.isCurrentState("three");     // false
 * SM.transitionTo("one");         // ERROR: Cannot transition from "two" to "one".
 * SM.assertCurrentState("three"); // ERROR: Current state is not one of {"three"}.
 * SM.isCurrentState(new RsSet(["one", "two"])); // true
 */
export default class StateMachine<T extends string> {
  // Defintion of the state machine
  transitionMapping: TransitionMapping<T>;

  // Current state of the system
  currentState: T;
  data: unknown; // Be sure to type check usages of this as TypeScript types are erased.

  // For logging, error messages, and debugging
  showFn: (currentState: T) => string;
  enableLogging: boolean;

  /*
   * Callbacks registered against the transitioning into and out
   * of particular states will be invoked upon that transition.
   */
  transitionCallbacks: Map<number, TransitionCallback<T>>;
  transitionCallbacksNextId: number;

  constructor(
    transitionMapping: TransitionMapping<T>,
    initialState: T,
    showFn: (currentState: T) => string,
    initialData: unknown
  ) {
    makeObservable(this, {
      currentState: observable,
      data: observable,
      transitionTo: action,
      addTransitionCallback: action,
      removeTransitionCallback: action,
      states: computed,
    });
    this.transitionMapping = transitionMapping;
    if (!this.states.has(initialState)) {
      throw new Error("The initial state must be a valid state.");
    }
    this.currentState = initialState;
    this.showFn = showFn;
    this.data = initialData;
    this.enableLogging = false;
    this.transitionCallbacks = new Map();
    this.transitionCallbacksNextId = 0;
  }

  get states(): RsSet<T> {
    return new RsSet(Object.keys(this.transitionMapping) as Array<T>);
  }

  setOfStatesToString(states: Set<T>): string {
    return `{${[...states].map((s) => this.showFn(s)).join(", ")}}`;
  }

  isCurrentState(states: RsSet<T> | T): boolean {
    if (!(states instanceof RsSet))
      return this.isCurrentState(new RsSet([states]));
    if (!states.isSubsetOf(this.states))
      throw new Error(
        `Invalid: ${this.setOfStatesToString(
          states
        )} is not a subset of ${this.setOfStatesToString(this.states)}`
      );
    return states.has(this.currentState);
  }

  assertCurrentState(states: RsSet<T> | T): void {
    if (!(states instanceof RsSet))
      return this.assertCurrentState(new RsSet([states]));
    if (!states.isSubsetOf(this.states))
      throw new Error(
        `Invalid: ${this.setOfStatesToString(
          states
        )} is not a subset of ${this.setOfStatesToString(this.states)}`
      );
    if (!states.has(this.currentState)) {
      throw new Error(
        `Current state is not one of ${this.setOfStatesToString(states)}.`
      );
    }
  }

  transitionTo(
    state: T,
    dataFn: (oldData: unknown) => unknown = (x) => x
  ): void {
    // eslint-disable-next-line no-console
    if (this.enableLogging) console.log(this.currentState, "->", state);
    if (!this.transitionMapping[this.currentState].has(state)) {
      throw new Error(
        `Cannot transition from '${this.showFn(
          this.currentState
        )}' to '${this.showFn(state)}'.`
      );
    }
    const before = { state: this.currentState, data: this.data };

    // data must be set before currentState because once currentState changes,
    // react and mobx will re-render
    this.data = dataFn(this.data);
    this.currentState = state;

    for (const [, tc] of this.transitionCallbacks) {
      const {
        before: beforeSet,
        after: afterSet,
        callback,
      }: TransitionCallback<T> = tc;
      if (beforeSet.has(before.state) || afterSet.has(state))
        callback(before, { state: this.currentState, data: this.data });
    }
  }

  addTransitionCallback(callback: TransitionCallback<T>): number {
    const id = this.transitionCallbacksNextId++;
    this.transitionCallbacks.set(id, callback);
    return id;
  }

  removeTransitionCallback(id: number) {
    this.transitionCallbacks.delete(id);
  }
}
