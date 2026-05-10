import React from "react";

export type BadgeVariant = "owner" | "admin" | "member" | "default" | "temp" | "success" | "warning";

interface BadgeProps {
  variant?: BadgeVariant;
  children: React.ReactNode;
  className?: string;
}

const Badge = ({
  variant = "default",
  children,
  className = "",
}: BadgeProps) => {
  const baseStyles =
    "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium";

  const variants: Record<BadgeVariant, string> = {
    owner: "bg-purple-100 text-purple-800 border border-purple-200",
    admin: "bg-blue-100 text-blue-800 border border-blue-200",
    member: "bg-gray-100 text-gray-800 border border-gray-200",
    temp: "bg-orange-100 text-orange-800 border border-orange-200",
    success: "bg-green-100 text-green-800 border border-green-200",
    warning: "bg-yellow-100 text-yellow-800 border border-yellow-200",
    default: "bg-gray-100 text-gray-800",
  };

  return (
    <span className={`${baseStyles} ${variants[variant]} ${className}`}>
      {children}
    </span>
  );
};

export default Badge;
