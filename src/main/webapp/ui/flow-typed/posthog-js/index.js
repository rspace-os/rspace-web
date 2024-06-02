//@flow strict

declare module "posthog-js" {
  declare export default class Posthog {
    static init(clientId: string, opts: {
      api_host: string,
    }): void;
  }
}
