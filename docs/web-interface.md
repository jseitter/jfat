# JFAT Web Interface

## Overview

The JFAT Web Interface provides a modern, browser-based interface for managing FAT filesystem images. It combines a Java backend built with Javalin and a React frontend with TypeScript.

## Architecture

### Backend (Java + Javalin)
- **Web Server**: `FatWebServer.java` - Main server with REST API and WebSocket support
- **REST API Controllers**:
  - `ImageApiController` - Disk image management (create, list, delete, info)
  - `FilesystemApiController` - File and directory operations within images
  - `GraphApiController` - Filesystem visualization and analysis
- **WebSocket Handler**: `FatWebSocketHandler` - Real-time communication for progress updates
- **JSON Mapping**: Jackson-based JSON serialization/deserialization

### Frontend (React + TypeScript)
- **Framework**: React 18 with TypeScript
- **UI Library**: Ant Design for professional components
- **Visualization**: D3.js for filesystem graphs
- **Build Tool**: Vite for fast development and optimized builds
- **Routing**: React Router for SPA navigation

### Integration
- **Development Mode**: Frontend dev server (port 3000) proxies API calls to backend (port 8080)
- **Production Mode**: Frontend assets bundled into JAR for single-file deployment
- **WebSocket**: Real-time updates for operations and filesystem changes

## API Endpoints

### Image Management
- `GET /api/images` - List all disk images
- `POST /api/images` - Create new disk image
- `DELETE /api/images/{name}` - Delete disk image
- `GET /api/images/{name}/info` - Get detailed image information

### Filesystem Operations
- `GET /api/fs/{image}/**` - List directory contents
- `POST /api/fs/{image}/**` - Create file or directory
- `PUT /api/fs/{image}/**` - Update file content
- `DELETE /api/fs/{image}/**` - Delete file or directory
- `GET /api/fs/{image}/download/**` - Download file
- `POST /api/fs/{image}/upload/**` - Upload file

### Analysis and Visualization
- `GET /api/graph/{image}` - Get basic filesystem graph
- `GET /api/graph/{image}/expert` - Get detailed expert graph
- `GET /api/analysis/{image}` - Get filesystem analysis and metrics

### System
- `GET /api/health` - Health check endpoint

## WebSocket Messages

The WebSocket endpoint (`/ws`) supports real-time communication:

### Client → Server
```json
{"type": "subscribe_image", "payload": "imageName"}
{"type": "operation_request", "payload": "operationData"}
{"type": "ping", "payload": ""}
```

### Server → Client
```json
{"type": "connected", "payload": "message"}
{"type": "filesystem_changed", "payload": {"imageName": "...", "path": "...", "change": "..."}}
{"type": "graph_updated", "payload": {"imageName": "...", "content": "..."}}
{"type": "operation_progress", "payload": {"progress": 50, "message": "..."}}
```

## Running the Web Interface

### Using the CLI (Recommended)
The easiest way to start the web server is through the JFAT CLI:

```bash
# Start web server on port 8080 (production mode)
java -jar jfat.jar webserver 8080

# Start web server on port 3000 (development mode)
java -jar jfat.jar web 3000 --dev

# Alternative short form
java -jar jfat.jar web 8080
```

### Using Gradle Tasks
For development and building:

### Development Mode
1. Start backend: `./gradlew runWebServerDev`
2. Start frontend: `cd src/main/frontend && npm run dev`
3. Access at: http://localhost:3000

### Production Mode
1. Build: `./gradlew build`
2. Run: `./gradlew runWebServer`
3. Access at: http://localhost:8080

## Build Tasks

- `./gradlew installFrontendDeps` - Install npm dependencies
- `./gradlew buildFrontend` - Build React frontend
- `./gradlew copyFrontend` - Copy built assets to resources
- `./gradlew runWebServer` - Run production server
- `./gradlew runWebServerDev` - Run development server

## Features

### ✅ Implemented
- Backend REST API with all endpoints
- Frontend application structure with routing
- WebSocket communication infrastructure
- Build integration (frontend → JAR)
- Image creation and listing APIs
- TypeScript type definitions
- Professional UI layout with Ant Design

### 🚧 In Progress
- Filesystem browser implementation
- Graph visualization with D3.js
- File upload/download functionality
- Expert mode analysis features

### 📋 TODO
- Complete filesystem operations (create, edit, delete files)
- Implement D3.js graph rendering
- Add file upload with drag-and-drop
- Real-time progress indicators
- Error handling and user feedback
- Authentication and security features
- Mobile responsive design

## Development

### Prerequisites
- Java 11+
- Node.js 18+
- npm 9+

### Adding New Features
1. **Backend**: Add controller methods in appropriate API controller
2. **Frontend**: Create React components in `src/main/frontend/src/components/`
3. **API Integration**: Update `src/main/frontend/src/services/api.ts`
4. **Types**: Add TypeScript interfaces for new data structures

### Testing APIs
```bash
# Health check
curl http://localhost:8080/api/health

# List images
curl http://localhost:8080/api/images

# Create image
curl -X POST http://localhost:8080/api/images \
  -H "Content-Type: application/json" \
  -d '{"name":"test", "fatType":"FAT32", "sizeMB":10}'
```

## Security Considerations

- CORS properly configured for development
- Path traversal protection in filesystem APIs
- File upload size limits
- WebSocket connection limits
- Input validation and sanitization

## Performance

- Lazy loading of React components
- Code splitting by vendor/feature
- Gzip compression enabled
- WebSocket connection pooling
- Efficient cluster analysis algorithms 