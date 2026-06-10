import { mergeProps } from "@base-ui/react/merge-props"
import { useRender } from "@base-ui/react/use-render"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 has-data-[icon=inline-end]:pr-1.5 has-data-[icon=inline-start]:pl-1.5 aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 [&>svg]:pointer-events-none [&>svg]:size-3!",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground [a]:hover:bg-primary/80",
        secondary:
          "bg-secondary text-secondary-foreground [a]:hover:bg-secondary/80",
        destructive:
          "bg-destructive/10 text-destructive focus-visible:ring-destructive/20 dark:bg-destructive/20 dark:focus-visible:ring-destructive/40 [a]:hover:bg-destructive/20",
        outline:
          "border-border text-foreground [a]:hover:bg-muted [a]:hover:text-muted-foreground",
        ghost:
          "hover:bg-muted hover:text-muted-foreground dark:hover:bg-muted/50",
        link: "text-primary underline-offset-4 hover:underline",
        owner:
          "bg-purple-100 text-purple-800 border-purple-200 border hover:bg-purple-100/80 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-800",
        admin:
          "bg-blue-100 text-blue-800 border-blue-200 border hover:bg-blue-100/80 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-800",
        member:
          "bg-gray-100 text-gray-800 border-gray-200 border hover:bg-gray-100/80 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700",
        temp:
          "bg-orange-100 text-orange-800 border-orange-200 border hover:bg-orange-100/80 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-800",
        success:
          "bg-green-100 text-green-800 border-green-200 border hover:bg-green-100/80 dark:bg-green-900/30 dark:text-green-300 dark:border-green-800",
        warning:
          "bg-yellow-100 text-yellow-800 border-yellow-200 border hover:bg-yellow-100/80 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-800",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  render,
  ...props
}: useRender.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: "span",
    props: mergeProps<"span">(
      {
        className: cn(badgeVariants({ variant }), className),
      },
      props
    ),
    render,
    state: {
      slot: "badge",
      variant,
    },
  })
}

export { Badge, badgeVariants }
