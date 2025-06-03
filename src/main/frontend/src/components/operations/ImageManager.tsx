import { useState, useEffect } from 'react';
import { 
  Button, 
  Table, 
  Modal, 
  Form, 
  Input, 
  Select, 
  InputNumber, 
  message, 
  Popconfirm, 
  Space, 
  Tag, 
  Card,
  Typography,
  Alert
} from 'antd';
import { 
  PlusOutlined, 
  DeleteOutlined, 
  PlayCircleOutlined, 
  StopOutlined,
  DatabaseOutlined
} from '@ant-design/icons';
import { imageApi } from '../../services/api';
import { useMountedImages } from '../../hooks/useMountedImages';
import { ImageInfo, CreateImageRequest } from '../../types';

const { Title, Text } = Typography;

function ImageManager() {
  const [images, setImages] = useState<ImageInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [form] = Form.useForm();
  
  const { 
    mountedImages, 
    mountImage, 
    unmountImage, 
    unmountAll, 
    isMounted 
  } = useMountedImages();

  const loadImages = async () => {
    setLoading(true);
    try {
      const imageList = await imageApi.listImages();
      setImages(imageList);
    } catch (error) {
      message.error('Failed to load images: ' + (error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadImages();
  }, []);

  const handleCreateImage = async (values: CreateImageRequest) => {
    try {
      await imageApi.createImage(values);
      message.success(`Image "${values.name}" created successfully`);
      setCreateModalVisible(false);
      form.resetFields();
      loadImages();
    } catch (error) {
      message.error('Failed to create image: ' + (error as Error).message);
    }
  };

  const handleDeleteImage = async (imageName: string) => {
    try {
      await imageApi.deleteImage(imageName);
      message.success(`Image "${imageName}" deleted successfully`);
      // Unmount if currently mounted
      if (isMounted(imageName)) {
        unmountImage(imageName);
      }
      loadImages();
    } catch (error) {
      message.error('Failed to delete image: ' + (error as Error).message);
    }
  };

  const handleMountToggle = (imageInfo: ImageInfo) => {
    if (isMounted(imageInfo.name)) {
      unmountImage(imageInfo.name);
      message.success(`Image "${imageInfo.name}" unmounted`);
    } else {
      mountImage(imageInfo);
      message.success(`Image "${imageInfo.name}" mounted`);
    }
  };

  const formatFileSize = (sizeMB: number) => {
    if (sizeMB < 1024) {
      return `${sizeMB} MB`;
    }
    return `${(sizeMB / 1024).toFixed(1)} GB`;
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, _: ImageInfo) => (
        <Space>
          <DatabaseOutlined />
          <Text strong>{name}</Text>
          {isMounted(name) && <Tag color="green">Mounted</Tag>}
        </Space>
      ),
    },
    {
      title: 'FAT Type',
      dataIndex: 'fatType',
      key: 'fatType',
      render: (fatType: string) => <Tag color="blue">{fatType}</Tag>,
    },
    {
      title: 'Size',
      dataIndex: 'sizeMB',
      key: 'sizeMB',
      render: (sizeMB: number) => formatFileSize(sizeMB),
    },
    {
      title: 'Cluster Size',
      dataIndex: 'clusterSize',
      key: 'clusterSize',
      render: (size: number) => `${size} bytes`,
    },
    {
      title: 'Created',
      dataIndex: 'created',
      key: 'created',
      render: (date: string) => formatDate(date),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: ImageInfo) => (
        <Space>
          <Button
            type={isMounted(record.name) ? "default" : "primary"}
            size="small"
            icon={isMounted(record.name) ? <StopOutlined /> : <PlayCircleOutlined />}
            onClick={() => handleMountToggle(record)}
          >
            {isMounted(record.name) ? 'Unmount' : 'Mount'}
          </Button>
          <Popconfirm
            title="Are you sure you want to delete this image?"
            description="This action cannot be undone."
            onConfirm={() => handleDeleteImage(record.name)}
            okText="Yes"
            cancelText="No"
          >
            <Button 
              type="text" 
              danger 
              size="small"
              icon={<DeleteOutlined />}
            >
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>📀 Image Manager</Title>
        <Text type="secondary">
          Create, manage, and mount FAT filesystem images for analysis.
        </Text>
      </div>

      {mountedImages.length > 0 && (
        <Card 
          title="Currently Mounted Images" 
          size="small" 
          style={{ marginBottom: 24 }}
          extra={
            <Button 
              size="small" 
              onClick={unmountAll}
              type="link"
            >
              Unmount All
            </Button>
          }
        >
          <Space wrap>
            {mountedImages.map(img => (
              <Tag 
                key={img.name} 
                color="green" 
                closable
                onClose={() => unmountImage(img.name)}
              >
                {img.name} ({img.fatType})
              </Tag>
            ))}
          </Space>
        </Card>
      )}

      <Card>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>Available Images</Title>
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={() => setCreateModalVisible(true)}
          >
            Create New Image
          </Button>
        </div>

        {images.length === 0 && !loading ? (
          <Alert
            message="No images found"
            description="Create a new image to get started with filesystem analysis."
            type="info"
            showIcon
            style={{ marginTop: 16 }}
          />
        ) : (
          <Table
            columns={columns}
            dataSource={images}
            rowKey="name"
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>

      <Modal
        title="Create New FAT Image"
        open={createModalVisible}
        onCancel={() => {
          setCreateModalVisible(false);
          form.resetFields();
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateImage}
        >
          <Form.Item
            label="Image Name"
            name="name"
            rules={[
              { required: true, message: 'Please enter an image name' },
              { pattern: /^[a-zA-Z0-9_-]+$/, message: 'Name can only contain letters, numbers, underscores, and hyphens' }
            ]}
          >
            <Input placeholder="e.g., test_image" />
          </Form.Item>

          <Form.Item
            label="FAT Type"
            name="fatType"
            rules={[{ required: true, message: 'Please select a FAT type' }]}
          >
            <Select placeholder="Select FAT type">
              <Select.Option value="FAT12">FAT12 (≤ 32MB)</Select.Option>
              <Select.Option value="FAT16">FAT16 (32MB - 2GB)</Select.Option>
              <Select.Option value="FAT32">FAT32 (≥ 32MB)</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="Size (MB)"
            name="sizeMB"
            rules={[
              { required: true, message: 'Please enter the image size' },
              { type: 'number', min: 1, max: 10240, message: 'Size must be between 1MB and 10GB' }
            ]}
          >
            <InputNumber 
              style={{ width: '100%' }}
              placeholder="e.g., 100"
              min={1}
              max={10240}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, marginTop: 24 }}>
            <Space>
              <Button onClick={() => {
                setCreateModalVisible(false);
                form.resetFields();
              }}>
                Cancel
              </Button>
              <Button type="primary" htmlType="submit">
                Create Image
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default ImageManager; 