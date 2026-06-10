import "@testing-library/jest-dom";
import { vi } from "vitest";
import * as React from "react";

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(), // Deprecated
    removeListener: vi.fn(), // Deprecated
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

vi.mock("@/components/ui/select", () => {
  const SelectContext = React.createContext<any>({});

  const Select = ({ children, value, onValueChange }: any) => {
    const [id, setId] = React.useState(undefined);
    return React.createElement(
      SelectContext.Provider,
      { value: { value, onValueChange, id, setId } },
      children
    );
  };

  const SelectTrigger = ({ children, id, className, ...props }: any) => {
    const ctx = React.useContext(SelectContext);
    React.useEffect(() => {
      if (id && ctx.setId) {
        ctx.setId(id);
      }
    }, [id]);
    return React.createElement("div", { ...props, "data-slot": "select-trigger" }, children);
  };

  const SelectValue = () => null;

  const SelectContent = ({ children }: any) => {
    const ctx = React.useContext(SelectContext);
    return React.createElement(
      "select",
      {
        id: ctx.id,
        value: ctx.value,
        onChange: (e: any) => ctx.onValueChange?.(e.target.value),
        className: "w-full",
      },
      children
    );
  };

  const SelectItem = ({ value, children }: any) => {
    return React.createElement("option", { value }, children);
  };

  const SelectGroup = ({ children }: any) => children;
  const SelectLabel = ({ children }: any) => React.createElement("optgroup", { label: children });
  const SelectScrollUpButton = () => null;
  const SelectScrollDownButton = () => null;
  const SelectSeparator = () => null;

  return {
    Select,
    SelectTrigger,
    SelectValue,
    SelectContent,
    SelectItem,
    SelectGroup,
    SelectLabel,
    SelectScrollUpButton,
    SelectScrollDownButton,
    SelectSeparator,
  };
});

vi.mock("@/components/ui/radio-group", () => {
  const RadioGroupContext = React.createContext<any>({});

  const RadioGroup = ({ children, value, onValueChange, className, ...props }: any) => {
    return React.createElement(
      RadioGroupContext.Provider,
      { value: { value, onValueChange } },
      React.createElement("div", { ...props, className }, children)
    );
  };

  const RadioGroupItem = ({ value, id, className, ...props }: any) => {
    const ctx = React.useContext(RadioGroupContext);
    return React.createElement("input", {
      type: "radio",
      id,
      value,
      checked: ctx.value === value,
      onChange: () => ctx.onValueChange?.(value),
      className,
      ...props,
    });
  };

  return {
    RadioGroup,
    RadioGroupItem,
  };
});

vi.mock("@/components/ui/dropdown-menu", () => {
  const DropdownMenu = ({ children }: any) => {
    const [open, setOpen] = React.useState(false);
    return React.createElement(
      "div",
      { "data-slot": "dropdown-menu" },
      React.Children.map(children, (child) => {
        if (!child) return null;
        if (child.type === DropdownMenuTrigger) {
          return React.cloneElement(child, { onClick: () => setOpen(!open) });
        }
        if (child.type === DropdownMenuContent) {
          return open ? child : null;
        }
        return child;
      })
    );
  };

  const DropdownMenuTrigger = ({ children, asChild, onClick, ...props }: any) => {
    if (asChild && React.isValidElement(children)) {
      return React.cloneElement(children as any, { onClick, ...props });
    }
    return React.createElement("button", { onClick, ...props }, children);
  };

  const DropdownMenuContent = ({ children }: any) => {
    return React.createElement("div", { "data-slot": "dropdown-menu-content" }, children);
  };

  const DropdownMenuItem = ({ children, onClick, disabled, variant, ...props }: any) => {
    return React.createElement(
      "button",
      {
        onClick: (e: any) => {
          if (!disabled) {
            onClick?.(e);
          }
        },
        disabled,
        ...props,
      },
      children
    );
  };

  return {
    DropdownMenu,
    DropdownMenuTrigger,
    DropdownMenuContent,
    DropdownMenuItem,
  };
});

vi.mock("@/components/ui/switch", () => {
  return {
    Switch: ({ checked, onCheckedChange, disabled, className, ...props }: any) => {
      return React.createElement("button", {
        type: "button",
        role: "switch",
        "aria-checked": checked,
        onClick: () => onCheckedChange?.(!checked),
        disabled,
        className,
        ...props,
      });
    },
  };
});

vi.mock("@/components/ui/tabs", () => {
  const TabsContext = React.createContext<any>({});

  const Tabs = ({ children, value, onValueChange, className, ...props }: any) => {
    return React.createElement(
      TabsContext.Provider,
      { value: { value, onValueChange } },
      React.createElement("div", { ...props, className }, children)
    );
  };

  const TabsList = ({ children, className, ...props }: any) => {
    return React.createElement("div", { ...props, className }, children);
  };

  const TabsTrigger = ({ value, children, className, ...props }: any) => {
    const ctx = React.useContext(TabsContext);
    const isActive = ctx.value === value;
    return React.createElement(
      "button",
      {
        type: "button",
        "aria-selected": isActive,
        onClick: () => ctx.onValueChange?.(value),
        className,
        ...props,
      },
      children
    );
  };

  const TabsContent = ({ value, children, className, ...props }: any) => {
    const ctx = React.useContext(TabsContext);
    const isActive = ctx.value === value;
    if (!isActive) return null;
    return React.createElement("div", { ...props, className }, children);
  };

  return {
    Tabs,
    TabsList,
    TabsTrigger,
    TabsContent,
  };
});
