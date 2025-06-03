import React from 'react';
import { Select, Space, Tag, Alert } from 'antd';
import { DatabaseOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useMountedImages } from '../../hooks/useMountedImages';
import { ImageSelectorProps } from '../../types';

const ImageSelector: React.FC<ImageSelectorProps> = ({ 
  selectedImage, 
  onImageSelect, 
  className 
}) => {
  const { mountedImages } = useMountedImages();

  if (mountedImages.length === 0) {
    return (
      <Alert
        message="No mounted images"
        description="Please mount an image from the Image Manager to proceed."
        type="warning"
        showIcon
        icon={<InfoCircleOutlined />}
        className={className}
        style={{ marginBottom: 16 }}
      />
    );
  }

  return (
    <div className={className} style={{ marginBottom: 16 }}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <DatabaseOutlined />
          <span>Select mounted image:</span>
        </div>
        <Select
          style={{ width: '100%', minWidth: 200 }}
          placeholder="Choose an image to analyze"
          value={selectedImage}
          onChange={onImageSelect}
          allowClear
          optionLabelProp="label"
        >
          {mountedImages.map(image => (
            <Select.Option 
              key={image.name} 
              value={image.name}
              label={`${image.name} (${image.fatType})`}
            >
              <Space>
                <span>{image.name}</span>
                <Tag color="blue">{image.fatType}</Tag>
                <Tag color="orange">{image.sizeMB}MB</Tag>
              </Space>
            </Select.Option>
          ))}
        </Select>
      </Space>
    </div>
  );
};

export default ImageSelector; 