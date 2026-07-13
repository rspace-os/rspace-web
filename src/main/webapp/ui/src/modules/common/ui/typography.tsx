import { mergeProps } from "@base-ui/react/merge-props";
import { useRender } from "@base-ui/react/use-render";
import { cva, type VariantProps } from "class-variance-authority";
import type * as React from "react";

import { cn } from "@/modules/common/utils/cn";

type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

const textVariants = cva("", {
  variants: {
    variant: {
      default: "",
      lead: "text-xl text-muted-foreground",
      large: "text-lg font-semibold",
      small: "text-sm font-medium leading-none",
      muted: "text-sm text-muted-foreground",
    },
  },
  defaultVariants: {
    variant: "default",
  },
});

const headingStyles: Record<HeadingLevel, string> = {
  1: "text-4xl font-extrabold tracking-tight text-balance",
  2: "text-3xl font-semibold tracking-tight",
  3: "text-2xl font-semibold tracking-tight",
  4: "text-xl font-semibold tracking-tight",
  5: "text-lg font-semibold tracking-tight",
  6: "text-base font-semibold tracking-tight",
};

// `level` controls the visual styling; `as` controls the rendered tag (defaults
// to the matching h1–h6) so the semantic level can differ from the visual one.
function Heading({
  level = 1,
  as,
  variant,
  className,
  ...props
}: React.ComponentProps<"h1"> & {
  level?: HeadingLevel;
  as?: keyof React.JSX.IntrinsicElements;
} & VariantProps<typeof textVariants>) {
  const tag = as ?? `h${level}`;
  const Tag = tag as React.ElementType;
  return (
    <Tag data-slot={tag} className={cn(headingStyles[level], textVariants({ variant }), className)} {...props} />
  );
}

function P({ className, ...props }: React.ComponentProps<"p">) {
  return <p data-slot="p" className={cn("leading-7 [&:not(:first-child)]:mt-6", className)} {...props} />;
}

function Text({
  as = "p",
  variant,
  className,
  ...props
}: React.ComponentProps<"p"> & { as?: keyof React.JSX.IntrinsicElements } & VariantProps<typeof textVariants>) {
  const Tag = as as React.ElementType;
  return <Tag data-slot="text" className={cn(textVariants({ variant }), className)} {...props} />;
}

function Blockquote({ className, ...props }: React.ComponentProps<"blockquote">) {
  return <blockquote data-slot="blockquote" className={cn("mt-6 border-l-2 pl-6 italic", className)} {...props} />;
}

function List({ className, ...props }: React.ComponentProps<"ul">) {
  return <ul data-slot="list" className={cn("my-2 ml-6 list-disc [&>li]:mt-2", className)} {...props} />;
}

function InlineCode({ className, ...props }: React.ComponentProps<"code">) {
  return (
    <code
      data-slot="inline-code"
      className={cn("relative rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono text-sm font-semibold", className)}
      {...props}
    />
  );
}

const linkClassName =
  "rounded-xs font-medium text-link underline underline-offset-4 hover:text-link/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

function Link({ className, render, ...props }: useRender.ComponentProps<"a">) {
  return useRender({
    defaultTagName: "a",
    props: mergeProps<"a">({ className: cn(linkClassName, className) }, props),
    render,
    state: { slot: "link" },
  });
}

export { Blockquote, Heading, InlineCode, Link, List, P, Text, textVariants };
