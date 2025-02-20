//@flow

/*
 * This is an abstraction over the concept of a function that can be called to
 * validate whether the system is in a valid state. Let's say there's some
 * parent component, like a wizard, that has a number of child components that
 * it manages the progression through. The parent component should verify that
 * the state of the currently displayed child is in a valid state before
 * allowing the user to proceed to the next.
 *
 * A naive approach would be to have the parent component store the state of
 * the entire system, so that it has access to everything to verify it. The
 * downside to doing this is that all of the state and much of the logic that
 * specific to each child component is all bundled together into the parent
 * component which, for a sufficiently large enough number of child
 * components, is going to become unmanageable.
 *
 * Instead, this Validator abstraction allows the parent component is create
 * these objects that it can then hand to each child component. Each child then
 * sets a validation function that makes use of its internal state. Finally,
 * when it needs to the parent component can invoke the validation function of
 * the relevant child component. This keeps all of the state of the child
 * components and the associated validation logic in the child components.
 */

/**
 * A wrapper around a function that can be called to validate the state of the
 * system.
 */
export type Validator = {|
  /*
   * All of the booleans are wrapped in promises so that the validation
   * function may perform asynchronous operations. To ensure that the user is
   * not left waiting after pressing the button that triggers the isValid call,
   * it is best to limit such logic to only what is essential. An example would
   * be to display a confirmation dialog asking the user if they would like to
   * override any validation warnings.
   */
  setValidFunc: (f: () => Promise<boolean>) => void,
  isValid: () => Promise<boolean>,
|};

/**
 * Constructor function for creating new Validator objects
 */
export const mkValidator = (
  isValidFunc?: () => Promise<boolean>
): Validator => {
  let validFunc = isValidFunc ?? ((_) => Promise.resolve(true));
  return {
    setValidFunc: (f: () => Promise<boolean>) => {
      validFunc = f;
    },
    isValid: () => validFunc(),
  };
};
