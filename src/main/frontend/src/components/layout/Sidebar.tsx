import { Layout, Menu } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  DashboardOutlined,
  HddOutlined,
  FolderOpenOutlined,
  PartitionOutlined,
} from '@ant-design/icons';

const { Sider } = Layout;

function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    {
      key: '/',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/images',
      icon: <HddOutlined />,
      label: 'Image Manager',
    },
    {
      key: '/filesystem',
      icon: <FolderOpenOutlined />,
      label: 'Filesystem Browser',
      disabled: true, // Will be enabled when image is selected
    },
    {
      key: '/graph',
      icon: <PartitionOutlined />,
      label: 'Graph Visualizer',
      disabled: true, // Will be enabled when image is selected
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  // Determine selected key based on current location
  const getSelectedKey = () => {
    const path = location.pathname;
    if (path.startsWith('/filesystem')) return '/filesystem';
    if (path.startsWith('/graph')) return '/graph';
    return path;
  };

  return (
    <Sider
      width={250}
      style={{
        background: '#fff',
        borderRight: '1px solid #f0f0f0',
      }}
    >
      <Menu
        mode="inline"
        selectedKeys={[getSelectedKey()]}
        style={{ height: '100%', borderRight: 0 }}
        items={menuItems}
        onClick={handleMenuClick}
      />
    </Sider>
  );
}

export default Sidebar; 