import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { 
  Card, 
  Button, 
  Space, 
  Typography, 
  Tag, 
  Alert,
  Spin,
  Tabs,
  Switch,
  message,
  Tooltip,
  Statistic,
  Row,
  Col,
  Divider
} from 'antd';
import { 
  BarChartOutlined,
  SettingOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  ClusterOutlined,
  WarningOutlined
} from '@ant-design/icons';
import * as d3 from 'd3';
import { graphApi } from '../../services/api';
import ImageSelector from '../common/ImageSelector';
import InteractiveShell from '../shell/InteractiveShell';
import FragmentationAnalysisDisplay from '../analysis/FragmentationAnalysisDisplay';
import { GraphResponse, AnalysisResponse, FragmentationAnalysis } from '../../types';

const { Title, Text, Paragraph } = Typography;
const { TabPane } = Tabs;

interface GraphNode {
  id: string;
  label: string;
  type: 'directory' | 'file' | 'cluster' | 'info' | 'fat';
  fillColor?: string;
  x?: number;
  y?: number;
  fx?: number | null;
  fy?: number | null;
}

interface GraphLink {
  source: string;
  target: string;
  type: 'hierarchy' | 'cluster-chain' | 'reference';
}

interface ParsedGraph {
  nodes: GraphNode[];
  links: GraphLink[];
}

function GraphVisualizer() {
  const { imageName } = useParams<{ imageName: string }>();
  const svgRef = useRef<SVGSVGElement>(null);
  
  const [selectedImage, setSelectedImage] = useState<string | undefined>(imageName);
  const [graphData, setGraphData] = useState<GraphResponse | null>(null);
  const [analysisData, setAnalysisData] = useState<AnalysisResponse | null>(null);
  const [fragmentationData, setFragmentationData] = useState<FragmentationAnalysis | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingFragmentation, setLoadingFragmentation] = useState(false);
  const [expertMode, setExpertMode] = useState(false);
  const [activeTab, setActiveTab] = useState('graph');

  // Load graph and analysis when image or mode changes
  useEffect(() => {
    if (selectedImage) {
      loadGraphData();
      loadAnalysisData();
    } else {
      setGraphData(null);
      setAnalysisData(null);
      setFragmentationData(null);
    }
  }, [selectedImage, expertMode]);

  // Load fragmentation data when analysis tab is accessed
  useEffect(() => {
    if (selectedImage && activeTab === 'analysis' && !fragmentationData) {
      loadFragmentationAnalysis();
    }
  }, [selectedImage, activeTab]);

  // Render graph when data is available and SVG ref is ready
  useEffect(() => {
    if (graphData && graphData.content && svgRef.current) {
      console.log('🎨 SVG ref is ready, rendering graph...');
      renderGraph(graphData.content);
    }
  }, [graphData]);

  const loadGraphData = async () => {
    if (!selectedImage) return;
    
    setLoading(true);
    try {
      console.log('🔍 Loading graph data:', { selectedImage, expertMode });
      const data = expertMode 
        ? await graphApi.getExpertGraph(selectedImage)
        : await graphApi.getGraph(selectedImage);
      
      console.log('📊 Graph data received:', data);
      setGraphData(data);
      // Don't call renderGraph here - let the useEffect handle it
    } catch (error) {
      console.error('❌ Failed to load graph:', error);
      message.error('Failed to load graph: ' + (error as Error).message);
      setGraphData(null);
    } finally {
      setLoading(false);
    }
  };

  const loadAnalysisData = async () => {
    if (!selectedImage) return;
    
    try {
      console.log('🔍 Loading analysis data:', { selectedImage });
      const data = await graphApi.getAnalysis(selectedImage);
      console.log('📈 Analysis data received:', data);
      setAnalysisData(data);
    } catch (error) {
      console.error('❌ Failed to load analysis:', error);
      // Don't show error message for analysis as it's supplementary
      setAnalysisData(null);
    }
  };

  const loadFragmentationAnalysis = async () => {
    if (!selectedImage) return;
    
    setLoadingFragmentation(true);
    try {
      console.log('🔍 Loading fragmentation analysis:', { selectedImage });
      const data = await graphApi.getFragmentationAnalysis(selectedImage);
      console.log('📈 Fragmentation analysis data received:', data);
      setFragmentationData(data);
    } catch (error) {
      console.error('❌ Failed to load fragmentation analysis:', error);
      message.error('Failed to load fragmentation analysis: ' + (error as Error).message);
      setFragmentationData(null);
    } finally {
      setLoadingFragmentation(false);
    }
  };

  const parseDotGraph = (dotContent: string): ParsedGraph => {
    const nodes: GraphNode[] = [];
    const links: GraphLink[] = [];
    
    console.log('🔧 Parsing DOT graph...');
    console.log('📝 Full DOT content:');
    console.log(dotContent);
    
    // Simple DOT parser - extract nodes and edges
    const lines = dotContent.split('\n');
    console.log('📋 Processing', lines.length, 'lines');
    
    let lineNumber = 0;
    for (const line of lines) {
      lineNumber++;
      const trimmed = line.trim();
      
      if (trimmed.length === 0 || trimmed.startsWith('//') || trimmed === '{' || trimmed === '}' || trimmed.startsWith('digraph') || trimmed.startsWith('rankdir') || trimmed.startsWith('subgraph') || trimmed.startsWith('label=') || trimmed.startsWith('style=') || trimmed.startsWith('color=')) {
        continue; // Skip empty lines, comments, and structure lines
      }
      
      console.log(`Line ${lineNumber}: "${trimmed}"`);
      
      // Parse node declarations: nodeid [label="text", fillcolor=color];
      const nodeMatch = trimmed.match(/^\s*(\w+)\s*\[([^\]]+)\];?$/);
      if (nodeMatch) {
        const nodeId = nodeMatch[1];
        const attributes = nodeMatch[2];
        
        console.log('✅ Found node:', { nodeId, attributes });
        
        // Extract label
        const labelMatch = attributes.match(/label="([^"]+)"/);
        const label = labelMatch ? labelMatch[1].replace(/\\n/g, '\n') : nodeId;
        
        // Extract fillcolor
        const colorMatch = attributes.match(/fillcolor=(\w+)/);
        const fillColor = colorMatch ? colorMatch[1] : 'lightblue';
        
        // Determine node type based on label content
        let type: GraphNode['type'] = 'info';
        if (label.toLowerCase().includes('directory') || label.toLowerCase().includes('root')) {
          type = 'directory';
        } else if (label.toLowerCase().includes('cluster') || label.toLowerCase().includes('fat')) {
          type = 'fat';
        } else if (label.includes('.')) {
          type = 'file';
        }
        
        const node = {
          id: nodeId,
          label,
          type,
          fillColor
        };
        
        console.log('📦 Created node:', node);
        nodes.push(node);
        continue;
      }
      
      // Parse edges: source -> target [attributes];
      const edgeMatch = trimmed.match(/^\s*(\w+)\s*->\s*(\w+)\s*(\[([^\]]+)\])?\s*;?$/);
      if (edgeMatch) {
        const source = edgeMatch[1];
        const target = edgeMatch[2];
        const attributes = edgeMatch[4] || '';
        
        console.log('✅ Found edge:', { source, target, attributes });
        
        // Determine link type
        let linkType: GraphLink['type'] = 'reference';
        if (attributes.includes('dashed')) {
          linkType = 'reference';
        } else {
          linkType = 'hierarchy';
        }
        
        const link = {
          source,
          target,
          type: linkType
        };
        
        console.log('🔗 Created link:', link);
        links.push(link);
        continue;
      }
      
      console.log('❓ Unmatched line:', trimmed);
    }
    
    console.log('📊 Final parsed graph:', { 
      totalNodes: nodes.length, 
      totalLinks: links.length,
      nodeIds: nodes.map(n => n.id),
      linkPairs: links.map(l => `${l.source}->${l.target}`)
    });
    
    return { nodes, links };
  };

  const renderGraph = (dotContent: string) => {
    if (!svgRef.current) {
      console.error('❌ SVG ref is not available');
      return;
    }
    
    console.log('🎨 Starting graph rendering...');
    console.log('📄 DOT content preview:', dotContent.substring(0, 500));
    
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();
    
    const parsedGraph = parseDotGraph(dotContent);
    console.log('🔧 Parsed graph result:', {
      nodeCount: parsedGraph.nodes.length,
      linkCount: parsedGraph.links.length,
      nodes: parsedGraph.nodes.map(n => ({ id: n.id, label: n.label, type: n.type })),
      links: parsedGraph.links.map(l => ({ source: l.source, target: l.target, type: l.type }))
    });
    
    if (parsedGraph.nodes.length === 0) {
      console.warn('⚠️ No nodes found in graph - check DOT parsing');
      // Add a message to the SVG
      svg.append('text')
        .attr('x', 400)
        .attr('y', 300)
        .attr('text-anchor', 'middle')
        .attr('font-size', '16px')
        .attr('fill', '#666')
        .text('No graph nodes found. Check console for parsing details.');
      return;
    }
    
    const width = 800;
    const height = 600;
    
    svg
      .attr('width', width)
      .attr('height', height)
      .attr('viewBox', `0 0 ${width} ${height}`)
      .style('max-width', '100%')
      .style('height', 'auto')
      .style('border', '1px solid #ddd'); // Add border for debugging
    
    console.log('📐 SVG setup complete:', { width, height });
    
    // Create zoom behavior
    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 4])
      .on('zoom', (event) => {
        container.attr('transform', event.transform);
      });
    
    svg.call(zoom);
    
    const container = svg.append('g');
    
    // Create force simulation
    const simulation = d3.forceSimulation(parsedGraph.nodes)
      .force('link', d3.forceLink(parsedGraph.links)
        .id((d: any) => d.id)
        .distance(100)
        .strength(0.5))
      .force('charge', d3.forceManyBody().strength(-300))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide().radius(50));
    
    // Add links
    const links = container.append('g')
      .selectAll('line')
      .data(parsedGraph.links)
      .enter()
      .append('line')
      .attr('stroke', (d: GraphLink) => {
        switch (d.type) {
          case 'hierarchy': return '#666';
          case 'cluster-chain': return '#e74c3c';
          case 'reference': return '#95a5a6';
          default: return '#666';
        }
      })
      .attr('stroke-width', (d: GraphLink) => d.type === 'cluster-chain' ? 3 : 2)
      .attr('stroke-dasharray', (d: GraphLink) => d.type === 'reference' ? '5,5' : 'none')
      .attr('opacity', 0.7);
    
    // Add nodes
    const nodes = container.append('g')
      .selectAll('g')
      .data(parsedGraph.nodes)
      .enter()
      .append('g')
      .attr('class', 'node')
      .call(d3.drag<SVGGElement, GraphNode>()
        .on('start', (event, d) => {
          if (!event.active) simulation.alphaTarget(0.3).restart();
          d.fx = d.x;
          d.fy = d.y;
        })
        .on('drag', (event, d) => {
          d.fx = event.x;
          d.fy = event.y;
        })
        .on('end', (event, d) => {
          if (!event.active) simulation.alphaTarget(0);
          d.fx = null;
          d.fy = null;
        })
      );
    
    // Add node circles
    nodes.append('circle')
      .attr('r', (d: GraphNode) => {
        switch (d.type) {
          case 'directory': return 20;
          case 'file': return 15;
          case 'cluster': return 12;
          case 'fat': return 25;
          case 'info': return 30;
          default: return 15;
        }
      })
      .attr('fill', (d: GraphNode) => {
        if (d.fillColor) {
          // Convert CSS color names to hex
          const colorMap: { [key: string]: string } = {
            'lightblue': '#ADD8E6',
            'lightgreen': '#90EE90',
            'lightyellow': '#FFFFE0',
            'lightgrey': '#D3D3D3',
            'yellow': '#FFFF00',
            'orange': '#FFA500',
            'red': '#FF0000',
            'green': '#008000',
            'blue': '#0000FF'
          };
          return colorMap[d.fillColor] || d.fillColor;
        }
        
        switch (d.type) {
          case 'directory': return '#f39c12';
          case 'file': return '#3498db';
          case 'cluster': return '#e74c3c';
          case 'fat': return '#2ecc71';
          case 'info': return '#9b59b6';
          default: return '#95a5a6';
        }
      })
      .attr('stroke', '#fff')
      .attr('stroke-width', 2);
    
    // Add node labels
    nodes.append('text')
      .text((d: GraphNode) => {
        // Truncate long labels
        const lines = d.label.split('\n');
        return lines[0].length > 20 ? lines[0].substring(0, 17) + '...' : lines[0];
      })
      .attr('text-anchor', 'middle')
      .attr('dy', '.35em')
      .attr('font-size', '12px')
      .attr('font-family', 'Arial, sans-serif')
      .attr('fill', '#333')
      .attr('pointer-events', 'none');
    
    // Add tooltips
    nodes.append('title')
      .text((d: GraphNode) => d.label);
    
    // Update positions on simulation tick
    simulation.on('tick', () => {
      links
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y);
      
      nodes
        .attr('transform', (d: GraphNode) => `translate(${d.x},${d.y})`);
    });
  };

  const handleDownloadGraph = () => {
    if (!graphData) return;
    
    const blob = new Blob([graphData.content], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${selectedImage}-${expertMode ? 'expert' : 'basic'}-graph.dot`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    message.success('Graph downloaded successfully');
  };

  const renderAnalysisTab = () => {
    if (!analysisData) {
      return (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <Text type="secondary">Analysis data not available</Text>
        </div>
      );
    }

    return (
      <div>
        {/* Basic Filesystem Analysis */}
        <Title level={4}>📊 Filesystem Analysis</Title>
        <Row gutter={[16, 16]}>
          <Col span={8}>
            <Card>
              <Statistic
                title="FAT Type"
                value={analysisData.fatType}
                prefix={<ClusterOutlined />}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic
                title="Total Size"
                value={`${(analysisData.totalSize / (1024 * 1024)).toFixed(1)} MB`}
                prefix={<BarChartOutlined />}
              />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic
                title="Cluster Utilization"
                value={`${analysisData.clusterUtilization.toFixed(1)}%`}
                valueStyle={{ 
                  color: analysisData.clusterUtilization > 80 ? '#f5222d' : 
                         analysisData.clusterUtilization > 60 ? '#fa8c16' : '#52c41a'
                }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col span={12}>
            <Card title="Storage Details">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div><Text strong>Total Sectors:</Text> {analysisData.totalSectors.toLocaleString()}</div>
                <div><Text strong>Bytes per Sector:</Text> {analysisData.bytesPerSector}</div>
                <div><Text strong>Sectors per Cluster:</Text> {analysisData.sectorsPerCluster}</div>
                <div><Text strong>Cluster Size:</Text> {analysisData.clusterSize} bytes</div>
              </div>
            </Card>
          </Col>
          <Col span={12}>
            <Card title="Cluster Information">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <div><Text strong>Total Clusters:</Text> {analysisData.totalClusters.toLocaleString()}</div>
                <div><Text strong>Used Clusters:</Text> {analysisData.usedClusters.toLocaleString()}</div>
                <div><Text strong>Free Clusters:</Text> {analysisData.freeClusters.toLocaleString()}</div>
                <div>
                  <Text strong>Optimal Cluster Size:</Text> 
                  <Tag color={analysisData.isOptimalClusterSize ? 'green' : 'orange'} style={{ marginLeft: 8 }}>
                    {analysisData.isOptimalClusterSize ? 'Yes' : 'No'}
                  </Tag>
                </div>
              </div>
            </Card>
          </Col>
        </Row>

        {/* Fragmentation Summary */}
        {(analysisData.fragmentationRatio !== undefined || analysisData.defragmentationRecommended) && (
          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col span={8}>
              <Card>
                <Statistic
                  title="File Fragmentation"
                  value={`${analysisData.fragmentationRatio?.toFixed(1) || 0}%`}
                  valueStyle={{ 
                    color: (analysisData.fragmentationRatio || 0) < 10 ? '#52c41a' : 
                           (analysisData.fragmentationRatio || 0) < 30 ? '#faad14' : '#f5222d'
                  }}
                  prefix={<WarningOutlined />}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic
                  title="Performance Impact"
                  value={`${analysisData.fragmentationImpactScore?.toFixed(0) || 0}/100`}
                  valueStyle={{ 
                    color: (analysisData.fragmentationImpactScore || 0) < 20 ? '#52c41a' : 
                           (analysisData.fragmentationImpactScore || 0) < 50 ? '#faad14' : '#f5222d'
                  }}
                />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic
                  title="Defragmentation"
                  value={analysisData.defragmentationRecommended ? 'Recommended' : 'Not Needed'}
                  valueStyle={{ 
                    color: analysisData.defragmentationRecommended ? '#fa8c16' : '#52c41a'
                  }}
                />
              </Card>
            </Col>
          </Row>
        )}

        {analysisData.clusterSizeInfo && (
          <Card style={{ marginTop: 16 }}>
            <Alert
              message="Cluster Size Recommendation"
              description={analysisData.clusterSizeInfo}
              type={analysisData.isOptimalClusterSize ? 'success' : 'warning'}
              icon={<InfoCircleOutlined />}
              showIcon
            />
          </Card>
        )}

        {/* Detailed Fragmentation Analysis */}
        <Divider />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Title level={4}>🔧 Detailed Fragmentation Analysis</Title>
          <Button 
            onClick={loadFragmentationAnalysis}
            loading={loadingFragmentation}
            type="primary"
            ghost
          >
            {fragmentationData ? 'Refresh Analysis' : 'Analyze Fragmentation'}
          </Button>
        </div>

        {loadingFragmentation ? (
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <Spin size="large" />
            <div style={{ marginTop: 16 }}>
              <Text>Analyzing filesystem fragmentation...</Text>
            </div>
          </div>
        ) : fragmentationData ? (
          <FragmentationAnalysisDisplay data={fragmentationData} />
        ) : (
          <Card>
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <Text type="secondary">
                Click "Analyze Fragmentation" to get detailed insights into file fragmentation, 
                free space distribution, and performance impact.
              </Text>
            </div>
          </Card>
        )}
      </div>
    );
  };

  // Handle filesystem changes from shell
  const handleFilesystemChange = (changedImageName: string) => {
    console.log('🔄 Filesystem changed for:', changedImageName);
    if (changedImageName === selectedImage) {
      message.info('Filesystem changed - refreshing graph...');
      // Reload graph data after a short delay
      setTimeout(() => {
        loadGraphData();
      }, 500);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={2}>📊 Graph Visualizer</Title>
        <Paragraph>
          Visualize filesystem structure, cluster allocation, and analyze storage efficiency.
          Switch between basic view (filesystem structure) and expert mode (detailed cluster information).
          Use the interactive shell to modify the filesystem and see changes reflected in real-time.
        </Paragraph>
      </div>

      <ImageSelector
        selectedImage={selectedImage}
        onImageSelect={(imageName) => setSelectedImage(imageName || undefined)}
      />

      {selectedImage && (
        <Row gutter={16}>
          {/* Main Graph Panel */}
          <Col span={16}>
            <Card>
              <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Space>
                  <Text strong>Visualization Mode:</Text>
                  <Switch
                    checked={expertMode}
                    onChange={setExpertMode}
                    checkedChildren="Expert"
                    unCheckedChildren="Basic"
                    loading={loading}
                  />
                  <Tooltip title={expertMode ? 
                    "Expert mode shows detailed cluster chains, FAT table structure, and technical information" :
                    "Basic mode shows simplified filesystem hierarchy and structure"
                  }>
                    <InfoCircleOutlined style={{ color: '#666' }} />
                  </Tooltip>
                </Space>
                <Space>
                  {graphData && (
                    <Button 
                      icon={<DownloadOutlined />}
                      onClick={handleDownloadGraph}
                    >
                      Download DOT
                    </Button>
                  )}
                  <Button 
                    icon={<SettingOutlined />}
                    onClick={loadGraphData}
                    loading={loading}
                    type="primary"
                  >
                    Generate Graph
                  </Button>
                </Space>
              </div>

              <Tabs activeKey={activeTab} onChange={setActiveTab}>
                <TabPane tab="Graph Visualization" key="graph">
                  {loading ? (
                    <div style={{ textAlign: 'center', padding: '40px' }}>
                      <Spin size="large" />
                      <div style={{ marginTop: 16 }}>
                        <Text>Generating {expertMode ? 'expert' : 'basic'} graph visualization...</Text>
                      </div>
                    </div>
                  ) : graphData ? (
                    <div>
                      <div style={{ marginBottom: 16 }}>
                        <Tag color={expertMode ? 'red' : 'blue'}>
                          {expertMode ? 'Expert Mode' : 'Basic Mode'}
                        </Tag>
                        <Text type="secondary" style={{ marginLeft: 8 }}>
                          {graphData.imageName} - {graphData.format.toUpperCase()} format
                        </Text>
                      </div>
                      <div style={{ border: '1px solid #d9d9d9', borderRadius: '6px', overflow: 'hidden' }}>
                        <svg ref={svgRef} />
                      </div>
                      <div style={{ marginTop: 16 }}>
                        <Text type="secondary">
                          💡 Drag nodes to reposition, scroll to zoom, click and drag to pan
                        </Text>
                      </div>
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: '40px' }}>
                      <Text type="secondary">Click "Generate Graph" to visualize the filesystem</Text>
                    </div>
                  )}
                </TabPane>
                
                <TabPane tab="Analysis" key="analysis">
                  {renderAnalysisTab()}
                </TabPane>
              </Tabs>
            </Card>
          </Col>

          {/* Interactive Shell Panel */}
          <Col span={8}>
            <InteractiveShell
              selectedImage={selectedImage}
              onFilesystemChange={handleFilesystemChange}
            />
          </Col>
        </Row>
      )}
    </div>
  );
}

export default GraphVisualizer; 