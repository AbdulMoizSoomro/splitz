import React, { useEffect, useId } from "react";
import { X } from "lucide-react";

/**
 * Props for the Modal component.
 */
interface ModalProps {
  /**
   * Whether the modal is currently visible.
   */
  isOpen: boolean;
  /**
   * Callback function to be called when the modal should be closed.
   */
  onClose: () => void;
  /**
   * The title displayed in the modal header.
   */
  title: string;
  /**
   * The content to be rendered inside the modal body.
   */
  children: React.ReactNode;
}

/**
 * A responsive, accessible modal (dialog) component.
 * Centers itself on the screen and traps focus when open.
 */
const Modal = ({ isOpen, onClose, title, children }: ModalProps) => {
  const titleId = useId();
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };

    if (isOpen) {
      document.addEventListener("keydown", handleEscape);
      document.body.style.overflow = "hidden";
    }

    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = "unset";
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
      <div
        className="relative w-full max-w-lg bg-white rounded-lg shadow-xl overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 id={titleId} className="text-xl font-semibold text-gray-900">
            {title}
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-md text-gray-500 hover:text-gray-700 hover:bg-gray-100 transition-colors"
            aria-label="Close"
          >
            <X size={24} />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
};

export default Modal;
