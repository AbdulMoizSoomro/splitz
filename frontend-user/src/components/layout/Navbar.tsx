import { Menu, Bell, LogOut } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../core/Button/Button';
import { useAuthStore } from '../../store/authStore';

interface NavbarProps {
  onMenuClick: () => void;
}

const Navbar = ({ onMenuClick }: NavbarProps) => {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="fixed top-0 z-40 w-full bg-white border-b border-gray-200">
      <div className="px-3 py-3 lg:px-5 lg:pl-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center justify-start">
            <button
              onClick={onMenuClick}
              className="inline-flex items-center p-2 text-sm text-gray-500 rounded-lg lg:hidden hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-gray-200"
            >
              <Menu size={24} />
            </button>
            <a href="/" className="flex ml-2 md:mr-24">
              <span className="self-center text-xl font-bold sm:text-2xl whitespace-nowrap text-blue-600">
                Splitz
              </span>
            </a>
          </div>
          <div className="flex items-center gap-2">
            {user && (
              <span className="hidden sm:inline-block text-sm font-medium text-gray-700 mr-2">
                Hi, {user.username}
              </span>
            )}
            <Button variant="ghost" size="sm" className="relative">
              <Bell size={20} />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full"></span>
            </Button>
            <Button variant="ghost" size="sm" onClick={handleLogout} title="Logout">
              <LogOut size={20} />
            </Button>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
