import { Routes, Route } from 'react-router-dom';
import { Layout } from 'antd';
import Header from './components/layout/Header';
import Sidebar from './components/layout/Sidebar';
import Dashboard from './components/Dashboard';
import FilesystemBrowser from './components/filesystem/FilesystemBrowser';
import GraphVisualizer from './components/graph/GraphVisualizer';
import ImageManager from './components/operations/ImageManager';
import { useWebSocket } from './hooks/useWebSocket';
import { getWebSocketUrl } from './utils/websocket';

const { Content } = Layout;

function App() {
  const { isConnected } = useWebSocket(getWebSocketUrl());

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header isConnected={isConnected} />
      <Layout>
        <Sidebar />
        <Layout style={{ padding: '24px' }}>
          <Content
            style={{
              background: '#fff',
              padding: 24,
              margin: 0,
              minHeight: 280,
              borderRadius: 8,
            }}
          >
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/images" element={<ImageManager />} />
              <Route path="/filesystem" element={<FilesystemBrowser />} />
              <Route path="/filesystem/:imageName/*" element={<FilesystemBrowser />} />
              <Route path="/graph" element={<GraphVisualizer />} />
              <Route path="/graph/:imageName" element={<GraphVisualizer />} />
            </Routes>
          </Content>
        </Layout>
      </Layout>
    </Layout>
  );
}

export default App; 