//@flow

/*
 * These are browser exceptions that Flow does not recognise
 */

declare interface DOMException {
  +code: number;
  +message: string;
  +name: string;
};
