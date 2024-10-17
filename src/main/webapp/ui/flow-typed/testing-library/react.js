//@flow strict

declare module "@testing-library/react" {
  import { type Node } from "react";

  declare export type Element = {|
    // avoid using
    click(): void,

    checked: boolean,
    value: string,
    textContent: string,
    className: string,
  |};

  type Queries = {|
    getByRole(string, ?{| name?: RegExp | string, level?: number |}): Element;
    getAllByRole(string, ?{| name?: RegExp | string |}): $ReadOnlyArray<Element>;
    findByRole(string, ?{| name?: RegExp | string |}): Promise<Element>;
    findAllByRole(string, ?{|name?: RegExp | string |}): Promise<$ReadOnlyArray<Element>>;
    queryByRole(string, ?{|name?: RegExp | string |}): null | Element;
    queryAllByRole(string, ?{|name?: RegExp | string |}): $ReadOnlyArray<Element>;

    getByLabelText(string): Element;
    getAllByLabelText(string): $ReadOnlyArray<Element>;

    getByText(string | RegExp): Element;
    getAllByText(string | RegExp): $ReadOnlyArray<Element>;
    findAllByText(string | RegExp): Promise<$ReadOnlyArray<Element>>;
    queryByText(string | RegExp): null | Element;
    queryAllByText(string | RegExp): $ReadOnlyArray<Element>;

    getByTestId(string): Element;

    getByDisplayValue(string): Element;
  |}
  declare export function render(Node): {|
    container: {|
      ...Queries
    |},
    baseElement: Element,
  |};

  declare export const screen: {|
    ...Queries,
  |};

  declare export function within(Element): Queries;

  // use user-event instead in almost all cases
  declare export const fireEvent: {|
    click(Element): void,
    mouseDown(Element): void,
    input(Element, {|
      target?: {| value: string |},
      name?: string,
      checkValidity?: () => boolean,
    |}): void,
    change(Element, {|
      target?: {| value: string |},
    |}): void,
    keyDown(Element): void,
    submit(Element): void,
  |};

  declare export function act<T: void | Promise<void>>(() => T): T;

  declare export function waitFor<T: void | Promise<void>>(() => T, ?{| timeout: number |}): T;

  declare export function cleanup(): void;
}
