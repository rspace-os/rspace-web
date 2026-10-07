import { Input as InputPrimitive } from "@base-ui/react/input";
import * as React from "react";

import { cn } from "@/modules/common/utils/cn";

function Input({ className, type, onBlur, ...props }: React.ComponentProps<"input">) {
  // A half-typed native time reports an empty value; its typed digits must stay visible.
  const [partialTime, setPartialTime] = React.useState(false);
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(
        "h-9 w-full min-w-0 rounded-sm border border-transparent bg-input/50 px-3 py-1 text-base transition-[color,box-shadow,background-color] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40",
        // WebKit (the only engine supporting this font keyword) fills an empty time field with the current time,
        // which reads as a chosen value. Other engines show a `--:--` hint, so they keep it.
        type === "time" &&
          props.value === "" &&
          !partialTime &&
          "supports-[font:-apple-system-body]:text-transparent focus:text-foreground",
        className,
      )}
      {...props}
      onBlur={(event) => {
        if (type === "time") setPartialTime(event.currentTarget.validity.badInput);
        onBlur?.(event);
      }}
    />
  );
}

export { Input };
