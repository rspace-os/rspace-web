//@flow strict

export function* incrementForever(): Generator<number, void, void> {
  for (let i = 0; ; i++) yield i;
}

export function* take<T>(
  iterator: Iterable<T>,
  n: number
): Generator<T, void, void> {
  let count = n;
  // $FlowExpectedError[incompatible-use] Flow does not support Symbols
  const it = iterator[Symbol.iterator]();
  for (let x of it) {
    if (count === 0) return;
    yield x;
    count--;
  }
}

export function sum(iterator: Iterable<number>): number {
  // $FlowExpectedError[incompatible-use] Flow does not support Symbols
  const it = iterator[Symbol.iterator]();
  let result = 0;
  for (let x of it) result += x;
  return result;
}
