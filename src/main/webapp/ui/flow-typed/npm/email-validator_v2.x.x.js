// flow-typed signature: 8f46f37377ddcd1f3c9d7218cd38673d
// flow-typed version: 402ea9a8cc/email-validator_v2.x.x/flow_>=v0.83.x

declare module 'email-validator' {
  declare module.exports: {|
    /**
     * Validate an email address.
     * @param {string} email - The email address to validate.
     * @returns {boolean}
     */
    validate(email: string): boolean,
  |};
}
