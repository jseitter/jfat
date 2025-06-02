import { Layout, Typography, Badge, Space } from 'antd';
import { WifiOutlined, DisconnectOutlined } from '@ant-design/icons';

const { Header: AntHeader } = Layout;
const { Title } = Typography;

interface HeaderProps {
  isConnected: boolean;
}

function Header({ isConnected }: HeaderProps) {
  return (
    <AntHeader
      style={{
        background: '#001529',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <Title level={3} style={{ color: 'white', margin: 0 }}>
        🗂️ JFAT - FAT Filesystem Manager
      </Title>
      
      <Space>
        <Badge
          status={isConnected ? 'success' : 'error'}
          text={
            <span style={{ color: 'white' }}>
              {isConnected ? 'Connected' : 'Disconnected'}
            </span>
          }
        />
        {isConnected ? (
          <WifiOutlined style={{ color: '#52c41a', fontSize: '16px' }} />
        ) : (
          <DisconnectOutlined style={{ color: '#ff4d4f', fontSize: '16px' }} />
        )}
      </Space>
    </AntHeader>
  );
}

export default Header; 