import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { 
  Card, 
  Table, 
  Breadcrumb, 
  Button, 
  Space, 
  Typography, 
  Tag, 
  Modal,
  message,
  Popconfirm,
  Tooltip
} from 'antd';
import { 
  FolderOutlined, 
  FileOutlined, 
  ArrowUpOutlined,
  DownloadOutlined,
  DeleteOutlined,
  PlusOutlined,
  HomeOutlined
} from '@ant-design/icons';
import { filesystemApi } from '../../services/api';
import ImageSelector from '../common/ImageSelector';
import { FileSystemEntry, DirectoryListing } from '../../types';

const { Title, Text } = Typography;

function FilesystemBrowser() {
  const { imageName, '*': pathParam } = useParams<{ imageName: string; '*': string }>();
  const navigate = useNavigate();
  const location = useLocation();
  
  const [selectedImage, setSelectedImage] = useState<string | undefined>(imageName);
  const [currentPath, setCurrentPath] = useState<string>('/');
  const [directoryListing, setDirectoryListing] = useState<DirectoryListing | null>(null);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);

  // Initialize path from URL parameter
  useEffect(() => {
    if (pathParam) {
      const decodedPath = '/' + decodeURIComponent(pathParam);
      setCurrentPath(decodedPath);
    } else {
      setCurrentPath('/');
    }
  }, [pathParam]);

  // Load directory when image or path changes
  useEffect(() => {
    if (selectedImage) {
      loadDirectory(currentPath);
    } else {
      setDirectoryListing(null);
    }
  }, [selectedImage, currentPath]);

  // Update URL when image selection changes (avoid infinite loops)
  useEffect(() => {
    if (selectedImage && selectedImage !== imageName) {
      const pathForUrl = currentPath === '/' ? '' : currentPath.substring(1);
      navigate(`/filesystem/${selectedImage}${pathForUrl ? '/' + pathForUrl : ''}`);
    }
  }, [selectedImage, navigate, imageName]);

  // Update URL when path changes within the same image
  useEffect(() => {
    if (selectedImage && selectedImage === imageName) {
      const pathForUrl = currentPath === '/' ? '' : currentPath.substring(1);
      const targetUrl = `/filesystem/${selectedImage}${pathForUrl ? '/' + pathForUrl : ''}`;
      if (location.pathname !== targetUrl) {
        navigate(targetUrl, { replace: true });
      }
    }
  }, [currentPath, selectedImage, imageName, navigate, location.pathname]);

  // Sync selectedImage with URL parameter
  useEffect(() => {
    if (imageName && imageName !== selectedImage) {
      setSelectedImage(imageName);
    }
  }, [imageName, selectedImage]);

  const loadDirectory = async (path: string) => {
    if (!selectedImage) return;
    
    console.log('🔍 Loading directory:', { selectedImage, path });
    setLoading(true);
    try {
      const listing = await filesystemApi.listDirectory(selectedImage, path);
      console.log('📡 Directory listing received:', {
        path: listing.path,
        entriesCount: listing.entries?.length || 0,
        entries: listing.entries
      });
      
      // Debug each entry
      if (listing.entries && listing.entries.length > 0) {
        listing.entries.forEach((entry, index) => {
          console.log(`  📁 Entry ${index + 1}:`, {
            name: entry.name,
            type: entry.type,
            size: entry.size,
            path: entry.path,
            modified: entry.modified
          });
        });
      } else {
        console.warn('⚠️ No entries found in directory listing!');
      }
      
      setDirectoryListing(listing);
    } catch (error) {
      console.error('❌ Failed to load directory:', error);
      message.error('Failed to load directory: ' + (error as Error).message);
      setDirectoryListing(null);
    } finally {
      setLoading(false);
    }
  };

  const handleNavigateToPath = (path: string) => {
    setCurrentPath(path);
  };

  const handleEntryClick = (entry: FileSystemEntry) => {
    if (entry.type === 'directory') {
      const newPath = currentPath === '/' ? `/${entry.name}` : `${currentPath}/${entry.name}`;
      console.log('Navigating to directory:', { currentPath, entryName: entry.name, newPath });
      setCurrentPath(newPath);
    }
  };

  const handleDownload = async (entry: FileSystemEntry) => {
    if (!selectedImage || entry.type === 'directory') return;
    
    try {
      console.log('Downloading file:', { selectedImage, entryPath: entry.path });
      const blob = await filesystemApi.downloadFile(selectedImage, entry.path);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = entry.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      message.success(`Downloaded ${entry.name}`);
    } catch (error) {
      console.error('Download failed:', error);
      message.error('Failed to download file: ' + (error as Error).message);
    }
  };

  const handleDelete = async (entry: FileSystemEntry) => {
    if (!selectedImage) return;
    
    try {
      console.log('Deleting entry:', { selectedImage, entryPath: entry.path });
      await filesystemApi.deleteEntry(selectedImage, entry.path);
      message.success(`Deleted ${entry.name}`);
      // Reload the current directory to reflect changes
      await loadDirectory(currentPath);
    } catch (error) {
      console.error('Delete failed:', error);
      message.error('Failed to delete entry: ' + (error as Error).message);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateString?: string) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  };

  const getBreadcrumbItems = () => {
    const pathParts = currentPath.split('/').filter(part => part);
    const items = [
      {
        title: (
          <span style={{ cursor: 'pointer' }}>
            <HomeOutlined />
          </span>
        ),
        onClick: () => handleNavigateToPath('/'),
      }
    ];
    
    let accumulatedPath = '';
    pathParts.forEach((part, index) => {
      accumulatedPath += `/${part}`;
      const pathToNavigate = accumulatedPath;
      const isLast = index === pathParts.length - 1;
      items.push({
        title: (
          <span style={{ cursor: isLast ? 'default' : 'pointer' }}>
            {part}
          </span>
        ),
        onClick: () => {
          if (!isLast) {
            handleNavigateToPath(pathToNavigate);
          }
        },
      });
    });
    
    return items;
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: FileSystemEntry) => (
        <Space 
          style={{ cursor: 'pointer' }}
          onClick={() => handleEntryClick(record)}
        >
          {record.type === 'directory' ? <FolderOutlined /> : <FileOutlined />}
          <Text>{name}</Text>
          {record.type === 'directory' && <Tag color="blue">DIR</Tag>}
        </Space>
      ),
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      render: (size: number, record: FileSystemEntry) => 
        record.type === 'file' ? formatFileSize(size) : '-',
    },
    {
      title: 'Modified',
      dataIndex: 'modified',
      key: 'modified',
      render: (date: string) => formatDate(date),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: FileSystemEntry) => (
        <Space>
          {record.type === 'file' && (
            <Tooltip title="Download">
              <Button 
                type="text" 
                size="small"
                icon={<DownloadOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  handleDownload(record);
                }}
              />
            </Tooltip>
          )}
          <Popconfirm
            title={`Delete ${record.type}`}
            description={`Are you sure you want to delete "${record.name}"?`}
            onConfirm={(e) => {
              e?.stopPropagation();
              handleDelete(record);
            }}
            okText="Yes"
            cancelText="No"
          >
            <Tooltip title="Delete">
              <Button 
                type="text" 
                danger 
                size="small"
                icon={<DeleteOutlined />}
                onClick={(e) => e.stopPropagation()}
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>📁 Filesystem Browser</Title>
        <Text type="secondary">
          Browse and manage files in the FAT filesystem images.
        </Text>
      </div>

      <ImageSelector
        selectedImage={selectedImage}
        onImageSelect={(imageName) => setSelectedImage(imageName || undefined)}
      />

      {selectedImage && (
        <Card>
          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Space direction="vertical" size="small" style={{ flex: 1 }}>
              <Breadcrumb items={getBreadcrumbItems()} />
              <Text type="secondary">
                Current Path: <Text code>{currentPath}</Text>
              </Text>
            </Space>
            <Space>
              {currentPath !== '/' && (
                <Button 
                  icon={<ArrowUpOutlined />}
                  onClick={() => {
                    const parentPath = currentPath.substring(0, currentPath.lastIndexOf('/')) || '/';
                    handleNavigateToPath(parentPath);
                  }}
                >
                  Up
                </Button>
              )}
              <Button 
                type="primary" 
                icon={<PlusOutlined />}
                onClick={() => setCreateModalVisible(true)}
              >
                New
              </Button>
            </Space>
          </div>

          {directoryListing ? (
            <Table
              columns={columns}
              dataSource={directoryListing.entries}
              rowKey={(record) => record.path}
              loading={loading}
              pagination={false}
              locale={{
                emptyText: 'This directory is empty'
              }}
            />
          ) : (
            <Text type="secondary">Select a mounted image to browse its filesystem.</Text>
          )}
        </Card>
      )}

      <Modal
        title="Create New File or Directory"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={null}
      >
        {/* TODO: Implement create file/directory form */}
        <div style={{ padding: 20, textAlign: 'center' }}>
          <Text type="secondary">Create functionality will be implemented here.</Text>
        </div>
      </Modal>
    </div>
  );
}

export default FilesystemBrowser; 