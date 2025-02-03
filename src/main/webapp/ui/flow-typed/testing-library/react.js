//@flow strict

declare module "@testing-library/react" {
  import type { Node } from "react";

  declare export type Element = {|
    checked: boolean,
    disabled: boolean,
    value: string,
    textContent: string,
    className: string,
  |};

  declare export type Queries = {|
    getByRole(string, ?{| name?: RegExp | string, level?: number |}): Element,
    getAllByRole(
      string,
      ?{| name?: RegExp | string | ((string) => boolean) |}
    ): $ReadOnlyArray<Element>,
    findByRole(string, ?{| name?: RegExp | string |}): Promise<Element>,
    findAllByRole(
      string,
      ?{| name?: RegExp | string |}
    ): Promise<$ReadOnlyArray<Element>>,
    queryByRole(string, ?{| name?: RegExp | string |}): null | Element,
    queryAllByRole(
      string,
      ?{| name?: RegExp | string |}
    ): $ReadOnlyArray<Element>,

    getByLabelText(string): Element,
    getAllByLabelText(string): $ReadOnlyArray<Element>,
    findByLabelText(string): Promise<Element>,

    getByText(string | RegExp | ((string) => boolean)): Element,
    getAllByText(string | RegExp): $ReadOnlyArray<Element>,
    findByText(string | RegExp): Promise<Element>,
    findAllByText(string | RegExp): Promise<$ReadOnlyArray<Element>>,
    queryByText(string | RegExp): null | Element,
    queryAllByText(string | RegExp): $ReadOnlyArray<Element>,

    getByTestId(string): Element,

    getByDisplayValue(string | number): Element,
  |};

  declare export var queries: Queries;

  declare export function render(Node): {|
    ...Queries,
    container: {|
      ...Queries,
    |} & Element,
    baseElement: Element,
  |};

  declare export var screen: {|
    ...Queries,
    debug: typeof console.debug,
  |};

  declare export function within<MoreQueries: { ... } = {||}>(
    Element,
    ?MoreQueries
  ): Queries & {| ...Queries, ...MoreQueries |};

  // use user-event instead in almost all cases
  declare export var fireEvent: {|
    click(Element): void,
    mouseDown(Element): void,
    input(
      Element,
      {|
        target?: {|
          value?: string | number,
          checkValidity?: () => boolean,
        |},
        name?: string,
      |}
    ): void,
    change(
      Element,
      {|
        target?: {| value: string |},
      |}
    ): void,
    keyDown(Element, ?{||}): void,
    submit(
      Element,
      ?{|
        target?: {| value: string |},
      |}
    ): void,
  |};

  declare export function act<T: void | Promise<void>>(() => T): T;

  declare export function waitFor<T: void | Promise<void>>(
    () => T,
    ?{| timeout: number |}
  ): T;

  declare export function cleanup(): void;
}
