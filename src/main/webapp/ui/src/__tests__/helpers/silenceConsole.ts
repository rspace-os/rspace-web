import { vi } from "vitest";

type ConsoleMethod = "log" | "info" | "warn" | "error";
type ProcessStream = "stderr" | "stdout";
type Matcher = string | RegExp;

const normalizeMatcher = (matcher: Matcher): Matcher => {
  if (typeof matcher === "string") {
    return matcher;
  }
  const flags = matcher.flags.replace("g", "");
  return new RegExp(matcher.source, flags);
};

const shouldSilence = (
  args: unknown[],
  matchers: Matcher[]
): boolean => {
  if (matchers.length === 0) return false;
  return args.some((arg) => {
    const value = String(arg);
    return matchers.some((matcher) => {
      if (typeof matcher === "string") {
        return value.includes(matcher);
      }
      return matcher.test(value);
    });
  });
};

export const silenceConsole = (
  methods: ConsoleMethod[],
  matchers: Matcher[]
): (() => void) => {
  const normalizedMatchers = matchers.map(normalizeMatcher);
  const restores = methods.map((method) => {
    const original = console[method].bind(console);
    const spy = vi.spyOn(console, method).mockImplementation((...args) => {
      if (shouldSilence(args, normalizedMatchers)) {
        return;
      }
      original(...(args as any[]));
    });
    return () => spy.mockRestore();
  });

  return () => {
    restores.forEach((restore) => restore());
  };
};

export const silenceProcessOutput = (
  streams: ProcessStream[],
  matchers: Matcher[]
): (() => void) => {
  if (typeof process === "undefined") {
    return () => {};
  }
  const normalizedMatchers = matchers.map(normalizeMatcher);
  const restores = streams.map((stream) => {
    const target = process[stream];
    const originalWrite = target.write.bind(target);
    const spy = vi.spyOn(target, "write").mockImplementation(
      (chunk: unknown, encoding?: any, callback?: () => void) => {
        const text = String(chunk);
        if (shouldSilence([text], normalizedMatchers)) {
          if (typeof callback === "function") {
            callback();
          }
          return true;
        }
        return originalWrite(chunk as any, encoding as any, callback as any);
      }
    );
    return () => spy.mockRestore();
  });

  return () => {
    restores.forEach((restore) => restore());
  };
};
