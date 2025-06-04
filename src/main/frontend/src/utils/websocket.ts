/**
 * Utility functions for WebSocket connections
 */

/**
 * Gets the appropriate WebSocket URL based on the current window location
 * This handles both development and production environments dynamically
 */
export function getWebSocketUrl(path: string = '/ws'): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  
  // In development mode, we might be on localhost:3000 but need to connect to localhost:8080
  if (window.location.port === '3000' && window.location.hostname === 'localhost') {
    // Development mode: frontend on 3000, backend on 8080
    return `ws://localhost:8080${path}`;
  }
  
  // Production or same-port mode: use current host
  return `${protocol}//${host}${path}`;
}

/**
 * Gets the base API URL for HTTP requests
 */
export function getApiBaseUrl(): string {
  // In development mode, Vite proxy handles this
  if (window.location.port === '3000' && window.location.hostname === 'localhost') {
    return ''; // Use relative URLs, Vite proxy will handle
  }
  
  // Production mode
  return '';
} 