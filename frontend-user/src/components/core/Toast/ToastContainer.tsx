import { useToastStore } from '../../../store/toastStore';
import { X, CheckCircle, AlertCircle, Info } from 'lucide-react';

const ToastContainer = () => {
  const { toasts, removeToast } = useToastStore();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-[100] space-y-2">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg border text-sm min-w-[300px] animate-in slide-in-from-right-full ${
            toast.type === 'success'
              ? 'bg-green-50 border-green-200 text-green-800'
              : toast.type === 'error'
              ? 'bg-red-50 border-red-200 text-red-800'
              : 'bg-blue-50 border-blue-200 text-blue-800'
          }`}
        >
          {toast.type === 'success' && <CheckCircle size={18} className="text-green-600" />}
          {toast.type === 'error' && <AlertCircle size={18} className="text-red-600" />}
          {toast.type === 'info' && <Info size={18} className="text-blue-600" />}
          
          <p className="flex-1 font-medium">{toast.message}</p>
          
          <button
            onClick={() => removeToast(toast.id)}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X size={16} />
          </button>
        </div>
      ))}
    </div>
  );
};

export default ToastContainer;
