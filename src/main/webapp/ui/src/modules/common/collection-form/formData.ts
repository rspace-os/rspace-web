import { type FormStore, getInput } from "@formisch/react";

/**
 * Only dereference synchronously in the render body of a component that called a formisch hook.
 * Reads in a child without a formisch hook attach to whichever listener was set last;
 * reads in callbacks, effects or handlers subscribe nothing.
 * A top-level key subscribes its whole subtree; spread, Object.keys and `in` see an empty object.
 */
export function formData<T>(form: FormStore): Partial<T> {
  return new Proxy({} as Partial<T>, {
    get: (_, key) =>
      typeof key === "string" && !(key in Object.prototype) ? getInput(form, { path: [key] } as never) : undefined,
  });
}
