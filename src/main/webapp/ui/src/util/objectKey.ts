const keys = new WeakMap<object, string>();
let nextKey = 0;

/**
 * A stable React key for an object that has no id of its own, such as a
 * record that has not been saved yet. Unlike an array index, the key follows
 * the object when the list is reordered or an earlier item is removed, so
 * component state stays with the item it belongs to.
 */
export function objectKey(object: object): string {
  let key = keys.get(object);
  if (key === undefined) {
    key = `object-${nextKey++}`;
    keys.set(object, key);
  }
  return key;
}
