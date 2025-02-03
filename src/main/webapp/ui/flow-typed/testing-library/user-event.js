//@flow strict

declare module "@testing-library/user-event" {
  import type { Element } from "@testing-library/react";

  declare export type User = {|
    click(Element): void,
    dblClick(Element): void,
    hover(Element): void,
    unhover(Element): void,

    /**
     * Moves the tab focus
     */
    tab(?{| shift?: boolean |}): void,

    /**
     * Clears a text field
     */
    clear(Element): Promise<void>,

    /**
     * Type in a textfield
     */
    type(Element, string): Promise<void>,

    /**
     * Upload a file using a input[type="file"]
     */
    upload(Element, File | $ReadOnlyArray<File>): Promise<void>,

    /**
     * Simulate keyboard presses
     * For information on how to encode keys, see https://testing-library.com/docs/user-event/keyboard
     */
    keyboard(string): Promise<void>,
  |};

  declare export default {|
    setup(): User,
  |};
}
