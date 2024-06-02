//@flow

/*
 * These functions should ideally not be used as they are difficult or impossible to properly type
 * and so any code that utilises them does not have as strong type guarantees.
 */

// be careful when using as it erases Flow types
export const pick =
  (...props: Array<string>): ((any) => any) =>
  (o: { ... }): { ... } =>
    props.reduce((a, e) => ({ ...a, [e]: o[e] }), {});

// navigate tree of JSON objects using a period-delimited string
// e.g. traverseObjectTree({foo: {bar: 4}}, "foo.bar", null) === 4
export const traverseObjectTree = (
  obj: {},
  prop: string,
  defval: any = null
): any => {
  let o = obj;
  const path = prop.split(".");
  for (let i = 0; i < path.length; i++) {
    if (typeof obj[path[i]] === "undefined") return defval;
    o = o[path[i]];
  }
  return obj;
};
