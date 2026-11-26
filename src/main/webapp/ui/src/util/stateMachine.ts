import { action, makeObservable, observable } from "mobx";
import RsSet from "./set";

type TransitionMapping<T extends string> = { [state in T]: Set<T> };

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
 * SM.isCurrentState("one");       // true
 * SM.transitionTo("two");         // currentState is now "two"
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

  constructor(
    transitionMapping: TransitionMapping<T>,
    initialState: T,
    showFn: (currentState: T) => string,
    initialData: unknown,
  ) {
    makeObservable(this, {
      currentState: observable,
      data: observable,
      transitionTo: action,
    });
    this.transitionMapping = transitionMapping;
    if (!this.states.has(initialState)) {
      throw new Error("The initial state must be a valid state.");
    }
    this.currentState = initialState;
    this.showFn = showFn;
    this.data = initialData;
    this.enableLogging = false;
  }

  get states(): RsSet<T> {
    return new RsSet(Object.keys(this.transitionMapping) as Array<T>);
  }

  setOfStatesToString(states: Set<T>): string {
    return `{${[...states].map((s) => this.showFn(s)).join(", ")}}`;
  }

  isCurrentState(states: RsSet<T> | T): boolean {
    if (!(states instanceof RsSet)) return this.isCurrentState(new RsSet([states]));
    if (!states.isSubsetOf(this.states))
      throw new Error(
        `Invalid: ${this.setOfStatesToString(states)} is not a subset of ${this.setOfStatesToString(this.states)}`,
      );
    return states.has(this.currentState);
  }

  assertCurrentState(states: RsSet<T> | T): void {
    // biome-ignore lint/correctness/noVoidTypeReturn: initial biome migration
    if (!(states instanceof RsSet)) return this.assertCurrentState(new RsSet([states]));
    if (!states.isSubsetOf(this.states))
      throw new Error(
        `Invalid: ${this.setOfStatesToString(states)} is not a subset of ${this.setOfStatesToString(this.states)}`,
      );
    if (!states.has(this.currentState)) {
      throw new Error(`Current state is not one of ${this.setOfStatesToString(states)}.`);
    }
  }

  transitionTo(state: T, dataFn: (oldData: unknown) => unknown = (x) => x): void {
    if (this.enableLogging) console.log(this.currentState, "->", state);
    if (!this.transitionMapping[this.currentState].has(state)) {
      throw new Error(`Cannot transition from '${this.showFn(this.currentState)}' to '${this.showFn(state)}'.`);
    }
    // data must be set before currentState because once currentState changes,
    // react and mobx will re-render
    this.data = dataFn(this.data);
    this.currentState = state;
  }
}
