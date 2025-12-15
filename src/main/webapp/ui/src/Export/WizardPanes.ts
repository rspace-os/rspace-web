import { type Validator, mkValidator } from "../util/Validator";

// A unique identifier for each pane
type Key = string;

/*
 * This is a doubly-linked list to model the panes of the wizard.
 */
type Pane = {
  key: Key;

  prev: Pane | null; // null means it is the first pane
  next: Pane | null; // null means it is the last pane

  // for validating that the next pane can be moved to
  validator: Validator;

  // title displayed at the top of the dialog
  title: string;
};

export const makePane = (key: Key, title: string): Pane => ({
  key,
  prev: null,
  next: null,
  validator: mkValidator(),
  title,
});

/*
 * Given the first pane in the list and new pane to append to the end, this
 * function does that append. Do note that is mutates the current end pane and
 * the new end pane.
 */
export const appendPane = (start: Pane, newEnd: Pane): void => {
  let ptr = start;
  while (ptr.next) ptr = ptr.next;
  ptr.next = newEnd;
  newEnd.prev = ptr;
};

/*
 * Counts the number of panes in the list, just list Array.prototype.length
 */
export const numberOfPanes = (start: Pane): number => {
  let ptr = start;
  let count = 1;
  while (ptr.next) {
    ptr = ptr.next;
    count++;
  }
  return count;
};

/**
 * Gets the index of a specified pane
 *
 * @param start The first pane in the list
 * @param current The pane whose index we're looking for
 * @return Natural number less than or equal to the length of the list
 * @throws If the specified pane is not in the list
 */
export const getIndexOfPane = (start: Pane, current: Pane): number => {
  let ptr = start;
  let index = 0;
  if (ptr.key === current.key) return index;
  while (ptr.next) {
    ptr = ptr.next;
    index++;
    if (ptr.key === current.key) return index;
  }
  throw new Error("Did not find current pane");
};

/**
 * Gets a pane by its unique key.
 *
 * @param start The first pane in the list
 * @param key The string that uniquely identifies the pane that is being
 *            requested
 * @throws If there is no pane with the provided key in the list
 */
export const getPaneByKey = (start: Pane, key: string): Pane => {
  let ptr = start;
  if (ptr.key === key) return ptr;
  while (ptr.next) {
    ptr = ptr.next;
    if (ptr.key === key) return ptr;
  }
  throw new Error(`Did not find pane with key: "${key}"`);
};
