//@flow strict

declare module "@intercom/messenger-js-sdk" {
  declare export default function Intercom({|
    app_id: string,
    user_id: string,
    hide_default_launcher?: boolean,
  |}): void;
}
