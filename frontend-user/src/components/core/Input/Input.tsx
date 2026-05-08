import React, { useId } from 'react';

/**
 * Props for the Input component.
 * Extends standard HTML input attributes.
 */
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /**
   * The text label displayed above the input field.
   */
  label?: string;
  /**
   * Error message to be displayed below the input field.
   * When present, the input border will turn red.
   */
  error?: string;
  /**
   * The size of the input.
   */
  inputSize?: 'sm' | 'default';
}

/**
 * A reusable, styled input component with optional label and error messaging.
 */
const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, inputSize = 'default', className = '', ...props }, ref) => {
    const generatedId = useId();
    const inputId = props.id || generatedId;

    const sizeClasses = inputSize === 'sm' ? 'h-8 px-2 py-1' : 'h-10 px-3 py-2';

    return (
      <div className="flex flex-col gap-1.5 w-full">
        {label && (
          <label
            htmlFor={inputId}
            className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 text-gray-700"
          >
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={`flex w-full rounded-md border border-gray-300 bg-white text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${sizeClasses} ${
            error ? 'border-red-500 focus-visible:ring-red-600' : ''
          } ${className}`}
          {...props}
          id={inputId}
        />
        {error && <p className="text-xs font-medium text-red-500">{error}</p>}
      </div>
    );
  }
);

Input.displayName = 'Input';

export default Input;
