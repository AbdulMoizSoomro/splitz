import { Home, Users, Folder, CreditCard, Settings } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

const Sidebar = ({ isOpen, onClose }: SidebarProps) => {
  const location = useLocation();
  const menuItems = [
    { icon: Home, label: 'Dashboard', href: '/' },
    { icon: Users, label: 'Friends', href: '/friends' },
    { icon: Folder, label: 'Groups', href: '/groups' },
    { icon: CreditCard, label: 'Activity', href: '/activity' },
    { icon: Settings, label: 'Settings', href: '/settings' },
  ];

  return (
    <>
      <aside
        className={`fixed top-0 left-0 z-40 w-64 h-screen pt-20 transition-transform ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        } bg-white border-r border-gray-200 lg:translate-x-0`}
        aria-label="Sidebar"
      >
        <div className="h-full px-3 pb-4 overflow-y-auto bg-white">
          <ul className="space-y-2 font-medium">
            {menuItems.map((item) => {
              const isActive = location.pathname === item.href;
              return (
                <li key={item.label}>
                  <Link
                    to={item.href}
                    onClick={onClose}
                    className={`flex items-center p-2 rounded-lg group transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-600'
                        : 'text-gray-900 hover:bg-gray-100'
                    }`}
                  >
                    <item.icon
                      size={20}
                      className={`transition duration-75 ${
                        isActive ? 'text-blue-600' : 'text-gray-500 group-hover:text-gray-900'
                      }`}
                    />
                    <span className="ml-3">{item.label}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      </aside>
      {isOpen && (
        <div
          className="fixed inset-0 z-30 bg-gray-900/50 lg:hidden"
          onClick={onClose}
        ></div>
      )}
    </>
  );
};

export default Sidebar;
