import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Card, Button, Typography, Space, Tag, Tooltip, message } from 'antd';
import { StopOutlined, ClearOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useWebSocket } from '../../hooks/useWebSocket';
import { getWebSocketUrl } from '../../utils/websocket';

const { Title, Text } = Typography;

interface ShellOutput {
  id: number;
  type: 'command' | 'output' | 'error' | 'welcome' | 'system';
  content: string;
  timestamp: Date;
}

interface InteractiveShellProps {
  onFilesystemChange?: (imageName: string) => void;
  selectedImage?: string;
}

function InteractiveShell({ onFilesystemChange, selectedImage }: InteractiveShellProps) {
  const [mountedImage, setMountedImage] = useState<string | undefined>();
  const [currentPath, setCurrentPath] = useState<string>('/');
  const [fatType, setFatType] = useState<string>('');
  const [output, setOutput] = useState<ShellOutput[]>([]);
  const [command, setCommand] = useState<string>('');
  const [commandHistory, setCommandHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState<number>(-1);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isFocused, setIsFocused] = useState<boolean>(false);
  
  const terminalRef = useRef<HTMLDivElement>(null);
  const nextIdRef = useRef<number>(1);
  
  // WebSocket connection with dynamic URL
  const { isConnected, lastMessage, sendMessage } = useWebSocket(getWebSocketUrl());
  
  // Auto-mount when selectedImage changes
  useEffect(() => {
    if (selectedImage && selectedImage !== mountedImage) {
      mountImage(selectedImage);
    } else if (!selectedImage && mountedImage) {
      unmountImage();
    }
  }, [selectedImage]);
  
  // Auto-scroll to bottom when new output is added
  useEffect(() => {
    if (terminalRef.current) {
      terminalRef.current.scrollTop = terminalRef.current.scrollHeight;
    }
  }, [output, command]);
  
  // Focus terminal when component mounts
  useEffect(() => {
    if (terminalRef.current) {
      terminalRef.current.focus();
    }
  }, []);
  
  // Handle WebSocket messages
  useEffect(() => {
    if (!lastMessage) return;
    
    console.log('🔌 Shell received WebSocket message:', lastMessage);
    
    switch (lastMessage.type) {
      case 'shell_mounted':
        handleShellMounted(lastMessage.payload);
        break;
      case 'shell_output':
        handleShellOutput(lastMessage.payload);
        break;
      case 'shell_error':
        handleShellError(lastMessage.payload);
        break;
      case 'shell_unmounted':
        handleShellUnmounted();
        break;
      case 'filesystem_changed':
        handleFilesystemChanged(lastMessage.payload);
        break;
    }
  }, [lastMessage]);
  
  const handleShellMounted = (payload: any) => {
    setMountedImage(payload.imageName);
    setCurrentPath(payload.currentPath || '/');
    setFatType(payload.fatType || '');
    setIsLoading(false);
    
    message.success(`Shell mounted: ${payload.imageName} (${payload.fatType})`);
  };
  
  const handleShellOutput = (output: string) => {
    if (output === 'CLEAR_SCREEN') {
      setOutput([]);
      return;
    }
    
    setOutput(prev => [...prev, {
      id: nextIdRef.current++,
      type: 'output',
      content: output,
      timestamp: new Date()
    }]);
    setIsLoading(false);
  };
  
  const handleShellError = (error: string) => {
    setOutput(prev => [...prev, {
      id: nextIdRef.current++,
      type: 'error',
      content: error,
      timestamp: new Date()
    }]);
    setIsLoading(false);
  };
  
  const handleShellUnmounted = () => {
    setMountedImage(undefined);
    setCurrentPath('/');
    setFatType('');
    setIsLoading(false);
    
    addSystemMessage('Shell session closed');
  };
  
  const handleFilesystemChanged = (payload: any) => {
    if (onFilesystemChange && payload.imageName === mountedImage) {
      onFilesystemChange(payload.imageName);
      addSystemMessage(`Filesystem changed: ${payload.change} at ${payload.path}`);
    }
  };
  
  const addSystemMessage = (message: string) => {
    setOutput(prev => [...prev, {
      id: nextIdRef.current++,
      type: 'system',
      content: message,
      timestamp: new Date()
    }]);
  };
  
  const mountImage = useCallback((imageName: string) => {
    if (!isConnected) {
      message.error('WebSocket not connected');
      return;
    }
    
    setIsLoading(true);
    sendMessage({
      type: 'shell_mount',
      payload: imageName
    });
  }, [isConnected, sendMessage]);
  
  const unmountImage = useCallback(() => {
    if (!isConnected) {
      message.error('WebSocket not connected');
      return;
    }
    
    sendMessage({
      type: 'shell_unmount',
      payload: ''
    });
  }, [isConnected, sendMessage]);
  
  const executeCommand = useCallback((cmd: string) => {
    if (!mountedImage) {
      message.error('No image mounted');
      return;
    }
    
    if (!isConnected) {
      message.error('WebSocket not connected');
      return;
    }
    
    // Add command to output
    setOutput(prev => [...prev, {
      id: nextIdRef.current++,
      type: 'command',
      content: `${currentPath}> ${cmd}`,
      timestamp: new Date()
    }]);
    
    // Add to command history
    if (cmd.trim()) {
      setCommandHistory(prev => {
        const newHistory = [cmd, ...prev.filter(h => h !== cmd)].slice(0, 50); // Keep last 50 commands
        return newHistory;
      });
      setHistoryIndex(-1);
    }
    
    setIsLoading(true);
    sendMessage({
      type: 'shell_command',
      payload: cmd
    });
  }, [mountedImage, isConnected, currentPath, sendMessage]);
  
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (command.trim()) {
        executeCommand(command.trim());
        setCommand('');
      }
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (historyIndex < commandHistory.length - 1) {
        const newIndex = historyIndex + 1;
        setHistoryIndex(newIndex);
        setCommand(commandHistory[newIndex] || '');
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (historyIndex > 0) {
        const newIndex = historyIndex - 1;
        setHistoryIndex(newIndex);
        setCommand(commandHistory[newIndex] || '');
      } else if (historyIndex === 0) {
        setHistoryIndex(-1);
        setCommand('');
      }
    } else if (e.key === 'Backspace') {
      e.preventDefault();
      setCommand(prev => prev.slice(0, -1));
    } else if (e.key.length === 1) {
      // Regular character input
      e.preventDefault();
      setCommand(prev => prev + e.key);
    }
  };
  
  const clearOutput = () => {
    setOutput([]);
  };
  
  const renderOutputLine = (item: ShellOutput) => {
    const getTextColor = () => {
      switch (item.type) {
        case 'command': return '#1890ff';
        case 'error': return '#ff4d4f';
        case 'system': return '#52c41a';
        default: return 'inherit';
      }
    };
    
    const getPrefix = () => {
      switch (item.type) {
        case 'command': return '';
        case 'error': return '❌ ';
        case 'system': return '🔄 ';
        default: return '';
      }
    };
    
    return (
      <div 
        key={item.id} 
        style={{ 
          color: getTextColor(),
          fontFamily: 'Consolas, Monaco, "Courier New", monospace',
          fontSize: '13px',
          lineHeight: '1.4',
          marginBottom: '2px',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all'
        }}
      >
        {getPrefix()}{item.content}
      </div>
    );
  };
  
  const renderCommandPrompt = () => {
    if (!mountedImage) return null;
    
    return (
      <div style={{
        fontFamily: 'Consolas, Monaco, "Courier New", monospace',
        fontSize: '13px',
        lineHeight: '1.4',
        color: '#ffffff',
        display: 'flex',
        alignItems: 'center'
      }}>
        <span style={{ color: '#1890ff' }}>{currentPath}&gt;</span>
        <span style={{ marginLeft: '8px' }}>{command}</span>
        <span style={{ 
          marginLeft: '2px',
          animation: isFocused ? 'blink 1s infinite' : 'none',
          backgroundColor: '#ffffff',
          color: '#1f1f1f',
          width: '8px',
          display: 'inline-block'
        }}>
          _
        </span>
      </div>
    );
  };
  
  return (
    <>
      <style>
        {`
          @keyframes blink {
            0%, 50% { opacity: 1; }
            51%, 100% { opacity: 0; }
          }
        `}
      </style>
      <Card
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Space>
              <Title level={4} style={{ margin: 0 }}>💻 Interactive Shell</Title>
              {!isConnected && (
                <Tag color="red">Disconnected</Tag>
              )}
              {mountedImage && (
                <Tag color="green">{mountedImage} ({fatType})</Tag>
              )}
            </Space>
            <Space>
              <Tooltip title="Clear screen">
                <Button 
                  icon={<ClearOutlined />} 
                  size="small" 
                  onClick={clearOutput}
                />
              </Tooltip>
              {mountedImage && (
                <Tooltip title="Unmount image">
                  <Button 
                    icon={<StopOutlined />}
                    size="small"
                    danger
                    onClick={unmountImage}
                    disabled={!isConnected}
                  />
                </Tooltip>
              )}
              <Tooltip title="Shell commands: DIR, CD, MD, RD, DEL, TYPE, PWD, HELP">
                <InfoCircleOutlined style={{ color: '#666' }} />
              </Tooltip>
            </Space>
          </div>
        }
        size="small"
        style={{ height: '600px', display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '16px' }}
      >
        {/* Terminal Output with Integrated Command Input */}
        <div
          ref={terminalRef}
          tabIndex={0}
          onKeyDown={handleKeyDown}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          style={{
            flex: 1,
            backgroundColor: '#1f1f1f',
            color: '#ffffff',
            padding: '12px',
            borderRadius: '4px',
            overflowY: 'auto',
            fontFamily: 'Consolas, Monaco, "Courier New", monospace',
            fontSize: '13px',
            border: isFocused ? '2px solid #1890ff' : '1px solid #d9d9d9',
            outline: 'none',
            cursor: 'text'
          }}
        >
          {!selectedImage ? (
            <div style={{ color: '#888', fontStyle: 'italic' }}>
              Select an image in the graph visualizer to start using the shell...
            </div>
          ) : !mountedImage ? (
            <div style={{ color: '#888', fontStyle: 'italic' }}>
              Mounting {selectedImage}...
            </div>
          ) : (
            <>
              {output.map(renderOutputLine)}
              {isLoading && (
                <div style={{ color: '#888' }}>...</div>
              )}
              {renderCommandPrompt()}
            </>
          )}
        </div>
        
        {/* Help Text */}
        {mountedImage && (
          <div style={{ marginTop: '8px', fontSize: '12px', color: '#666' }}>
            <Text type="secondary">
              Click in terminal to focus • Use ↑/↓ for command history • Available commands: DIR, CD, MD, RD, DEL, TYPE, PWD, HELP, CLS
            </Text>
          </div>
        )}
      </Card>
    </>
  );
}

export default InteractiveShell; 