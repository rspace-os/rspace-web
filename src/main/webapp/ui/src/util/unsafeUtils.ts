//@flow

/*
 * These functions should ideally not be used as they are difficult or impossible to properly type
 * and so any code that utilises them does not have as strong type guarantees.
 */

/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unsafe-assignment */

// be careful when using as it erases Flow types
export const pick =
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (...props: Array<string>): ((o: any) => any) =>
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (o: Record<string, any>): object =>
      // biome-ignore lint/performance/noAccumulatingSpread: initial biome migration
      props.reduce((a, e) => ({ ...a, [e]: o[e] }), {});

// navigate tree of JSON objects using a period-delimited string
// e.g. traverseObjectTree({foo: {bar: 4}}, "foo.bar", null) === 4
export const traverseObjectTree = (
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  obj: Record<string, any>,
  prop: string,
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  defval: any = null,
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
): any => {
  let o = obj;
  const path = prop.split(".");
  for (let i = 0; i < path.length; i++) {
    if (typeof o[path[i]] === "undefined") return defval;
    o = o[path[i]];
  }
  return o;
};
