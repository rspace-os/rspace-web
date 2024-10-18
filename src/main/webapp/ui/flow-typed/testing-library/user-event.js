//@flow strict

declare module "@testing-library/user-event" {
  import type { Element } from "@testing-library/react";

  declare export type User = {|
    click(Element): void,
  |};

  //declare export function setup(): User;

  declare export default {|
    setup(): User,
  |};
}
