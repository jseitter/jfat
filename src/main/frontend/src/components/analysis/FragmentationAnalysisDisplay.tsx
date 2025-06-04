import React from 'react';
import { Card, Row, Col, Statistic, Table, Tabs, Tag, Progress, Alert } from 'antd';
import { FileTextOutlined, DatabaseOutlined, WarningOutlined } from '@ant-design/icons';
import { FragmentationAnalysis, FileFragmentationInfo, FragmentationRecommendation } from '../../types';

interface Props {
  data: FragmentationAnalysis;
}

export const FragmentationAnalysisDisplay: React.FC<Props> = ({ data }) => {
  const getFragmentationColor = (ratio: number) => {
    if (ratio < 10) return '#52c41a';
    if (ratio < 30) return '#faad14';
    return '#f5222d';
  };

  const getImpactColor = (score: number) => {
    if (score < 20) return '#52c41a';
    if (score < 50) return '#faad14';
    if (score < 80) return '#fa8c16';
    return '#f5222d';
  };

  const getSeverityTag = (severity: string) => {
    const colors: Record<string, string> = {
      NONE: 'green',
      LIGHT: 'blue',
      MODERATE: 'orange',
      HEAVY: 'red',
      SEVERE: 'magenta'
    };
    return <Tag color={colors[severity] || 'default'}>{severity}</Tag>;
  };

  const getPriorityTag = (priority: string) => {
    const colors: Record<string, string> = { LOW: 'blue', MEDIUM: 'orange', HIGH: 'red' };
    return <Tag color={colors[priority] || 'default'}>{priority}</Tag>;
  };

  const formatSize = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const fragmentedFilesColumns = [
    {
      title: 'File Path',
      dataIndex: 'path',
      key: 'path',
      ellipsis: true,
      width: '40%',
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      render: (size: number) => formatSize(size),
      width: '15%',
      sorter: (a: FileFragmentationInfo, b: FileFragmentationInfo) => a.size - b.size,
    },
    {
      title: 'Fragments',
      dataIndex: 'fragmentCount',
      key: 'fragments',
      width: '15%',
      sorter: (a: FileFragmentationInfo, b: FileFragmentationInfo) => a.fragmentCount - b.fragmentCount,
    },
    {
      title: 'Avg Gap',
      dataIndex: 'averageGap',
      key: 'gap',
      render: (gap: number) => `${gap.toFixed(1)} clusters`,
      width: '15%',
      sorter: (a: FileFragmentationInfo, b: FileFragmentationInfo) => a.averageGap - b.averageGap,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      render: getSeverityTag,
      width: '15%',
      filters: [
        { text: 'NONE', value: 'NONE' },
        { text: 'LIGHT', value: 'LIGHT' },
        { text: 'MODERATE', value: 'MODERATE' },
        { text: 'HEAVY', value: 'HEAVY' },
        { text: 'SEVERE', value: 'SEVERE' },
      ],
      onFilter: (value: any, record: FileFragmentationInfo) => record.severity === value,
    },
  ];

  const recommendationsColumns = [
    {
      title: 'Priority',
      dataIndex: 'priority',
      key: 'priority',
      render: getPriorityTag,
      width: '15%',
      sorter: (a: FragmentationRecommendation, b: FragmentationRecommendation) => {
        const priorities = { HIGH: 3, MEDIUM: 2, LOW: 1 };
        return priorities[a.priority] - priorities[b.priority];
      },
    },
    {
      title: 'Recommendation',
      dataIndex: 'description',
      key: 'description',
      width: '65%',
    },
    {
      title: 'Affected Files',
      dataIndex: 'affectedFiles',
      key: 'affectedFiles',
      render: (files: string[]) => files.length,
      width: '20%',
    },
  ];

  return (
    <div>
      {/* Overview Cards */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="File Fragmentation"
              value={`${data.fileFragmentation.fragmentationRatio.toFixed(1)}%`}
              valueStyle={{ color: getFragmentationColor(data.fileFragmentation.fragmentationRatio) }}
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Performance Impact"
              value={data.performanceImpact.fragmentationImpactScore.toFixed(0)}
              suffix="/100"
              valueStyle={{ color: getImpactColor(data.performanceImpact.fragmentationImpactScore) }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Avg Fragments/File"
              value={data.fileFragmentation.averageFragmentsPerFile.toFixed(1)}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Free Space Fragmentation"
              value={`${data.freeSpaceFragmentation.freeSpaceFragmentationRatio.toFixed(1)}%`}
              valueStyle={{ color: getFragmentationColor(data.freeSpaceFragmentation.freeSpaceFragmentationRatio) }}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* Performance Impact Progress */}
      <Card title="Performance Impact Assessment" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <div>
              <div style={{ marginBottom: 8, fontWeight: 'bold' }}>Seek Distance Score</div>
              <Progress
                percent={data.performanceImpact.seekDistanceScore}
                strokeColor={getImpactColor(data.performanceImpact.seekDistanceScore)}
                showInfo={false}
              />
              <div style={{ textAlign: 'center', marginTop: 4 }}>
                {data.performanceImpact.seekDistanceScore.toFixed(1)}/100
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div>
              <div style={{ marginBottom: 8, fontWeight: 'bold' }}>Read Efficiency Score</div>
              <Progress
                percent={data.performanceImpact.readEfficiencyScore}
                strokeColor="#52c41a"
                showInfo={false}
              />
              <div style={{ textAlign: 'center', marginTop: 4 }}>
                {data.performanceImpact.readEfficiencyScore.toFixed(1)}/100
              </div>
            </div>
          </Col>
          <Col span={8}>
            <div>
              <div style={{ marginBottom: 8, fontWeight: 'bold' }}>Overall Impact</div>
              <Progress
                percent={data.performanceImpact.fragmentationImpactScore}
                strokeColor={getImpactColor(data.performanceImpact.fragmentationImpactScore)}
                showInfo={false}
              />
              <div style={{ textAlign: 'center', marginTop: 4 }}>
                {data.performanceImpact.fragmentationImpactScore.toFixed(1)}/100
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* Key Metrics Summary */}
      <Card title="Detailed Metrics" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Statistic
              title="Sequential Cluster Ratio"
              value={`${data.fileFragmentation.sequentialClusterRatio.toFixed(1)}%`}
              valueStyle={{ color: data.fileFragmentation.sequentialClusterRatio > 80 ? '#52c41a' : '#faad14' }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="Max File Fragments"
              value={data.fileFragmentation.maxFileFragments}
              valueStyle={{ color: data.fileFragmentation.maxFileFragments > 10 ? '#f5222d' : '#52c41a' }}
            />
          </Col>
          <Col span={8}>
            <Statistic
              title="Largest Free Block"
              value={`${data.freeSpaceFragmentation.largestContiguousFreeBlock} clusters`}
            />
          </Col>
        </Row>
      </Card>

      {/* Detailed Analysis Tabs */}
      <Tabs defaultActiveKey="files">
        <Tabs.TabPane tab={`Fragmented Files (${data.fileFragmentation.worstFiles.length})`} key="files">
          {data.fileFragmentation.worstFiles.length > 0 ? (
            <Table
              columns={fragmentedFilesColumns}
              dataSource={data.fileFragmentation.worstFiles}
              rowKey="path"
              size="small"
              pagination={{ 
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} fragmented files`
              }}
            />
          ) : (
            <Alert
              message="No Fragmented Files"
              description="Congratulations! All files in this filesystem are stored in contiguous clusters."
              type="success"
              showIcon
            />
          )}
        </Tabs.TabPane>

        <Tabs.TabPane tab="Free Space Analysis" key="freespace">
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={8}>
              <Statistic
                title="Free Space Fragmentation"
                value={`${data.freeSpaceFragmentation.freeSpaceFragmentationRatio.toFixed(1)}%`}
                valueStyle={{ color: getFragmentationColor(data.freeSpaceFragmentation.freeSpaceFragmentationRatio) }}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="Free Blocks Count"
                value={data.freeSpaceFragmentation.freeBlockCount}
              />
            </Col>
            <Col span={8}>
              <Statistic
                title="Avg Block Size"
                value={`${data.freeSpaceFragmentation.averageFreeBlockSize.toFixed(1)} clusters`}
              />
            </Col>
          </Row>
          
          {data.freeSpaceFragmentation.freeBlockCount <= 20 && data.freeSpaceFragmentation.freeSpaceMap.length > 0 && (
            <Card title="Free Space Distribution" size="small">
              <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {data.freeSpaceFragmentation.freeSpaceMap.map((block, index) => (
                  <div key={index} style={{ marginBottom: 4 }}>
                    <Tag color="green">
                      Cluster {block.startCluster}-{block.startCluster + block.size - 1} ({block.size} clusters)
                    </Tag>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </Tabs.TabPane>

        <Tabs.TabPane tab={`Recommendations (${data.recommendations.length})`} key="recommendations">
          {data.recommendations.length > 0 ? (
            <Table
              columns={recommendationsColumns}
              dataSource={data.recommendations}
              rowKey="description"
              size="small"
              pagination={false}
              expandable={{
                expandedRowRender: (record: FragmentationRecommendation) => (
                  <div style={{ margin: 0 }}>
                    {record.affectedFiles.length > 0 && (
                      <div>
                        <strong>Affected Files:</strong>
                        <ul style={{ marginTop: 8, marginBottom: 0 }}>
                          {record.affectedFiles.slice(0, 10).map((file, index) => (
                            <li key={index}>{file}</li>
                          ))}
                          {record.affectedFiles.length > 10 && (
                            <li>... and {record.affectedFiles.length - 10} more files</li>
                          )}
                        </ul>
                      </div>
                    )}
                  </div>
                ),
                rowExpandable: (record: FragmentationRecommendation) => record.affectedFiles.length > 0,
              }}
            />
          ) : (
            <Alert
              message="No Recommendations"
              description="Your filesystem is well optimized and doesn't need defragmentation!"
              type="success"
              showIcon
            />
          )}
        </Tabs.TabPane>
      </Tabs>
    </div>
  );
};

export default FragmentationAnalysisDisplay; 