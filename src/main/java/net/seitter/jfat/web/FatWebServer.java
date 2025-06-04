package net.seitter.jfat.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.seitter.jfat.web.api.ImageApiController;
import net.seitter.jfat.web.api.FilesystemApiController;
import net.seitter.jfat.web.api.GraphApiController;
import net.seitter.jfat.web.websocket.FatWebSocketHandler;

import java.io.File;

/**
 * Main web server for JFAT filesystem management
 */
public class FatWebServer {
    
    private final Javalin app;
    private final int port;
    private final boolean developmentMode;
    private final ImageApiController imageApi;
    private final FilesystemApiController filesystemApi;
    private final GraphApiController graphApi;
    private final FatWebSocketHandler webSocketHandler;
    
    public FatWebServer(int port, boolean developmentMode) {
        this.port = port;
        this.developmentMode = developmentMode;
        
        // Initialize controllers
        this.imageApi = new ImageApiController();
        this.filesystemApi = new FilesystemApiController();
        this.graphApi = new GraphApiController();
        this.webSocketHandler = new FatWebSocketHandler();
        
        // Configure JSON mapper
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Create Javalin app
        this.app = Javalin.create(config -> {
            // Configure JSON
            config.jsonMapper(new JacksonJsonMapper(mapper));
            
            // CORS configuration
            // we use this for development mode only
            if(developmentMode) {
            config.plugins.enableCors(cors -> {
                cors.add(rule -> {
                    rule.allowHost("localhost:3000"); // For development
                    rule.allowCredentials = true;
                    });
                });
            }
            
            // Static files
            if (developmentMode) {
                System.out.println("Running in development mode - serving static files from filesystem");
                // In development, let Vite handle static files
            } else {
                // In production, serve only assets from static directory
                // This maps /assets/* to static/assets/* in the JAR
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";        // Serve at /
                    staticFiles.directory = "/static";  // From /static in JAR  
                    staticFiles.location = Location.CLASSPATH;
                });
            }
            
            // Enable request logging in development
            if (developmentMode) {
                config.plugins.enableDevLogging();
            }
        });
        
        setupRoutes();
    }
    
    private void setupRoutes() {
        // Health check
        app.get("/api/health", ctx -> {
            ctx.json(new HealthResponse("OK", System.currentTimeMillis()));
        });
        
        // Image API routes
        app.get("/api/images", imageApi::listImages);
        app.post("/api/images", imageApi::createImage);
        app.delete("/api/images/{name}", imageApi::deleteImage);
        app.get("/api/images/{name}/info", imageApi::getImageInfo);
        
        // Filesystem API routes
        app.get("/api/fs/{image}/**", filesystemApi::listDirectory);
        app.post("/api/fs/{image}/**", filesystemApi::createEntry);
        app.put("/api/fs/{image}/**", filesystemApi::updateFile);
        app.delete("/api/fs/{image}/**", filesystemApi::deleteEntry);
        
        // File upload/download
        app.post("/api/fs/{image}/upload/**", filesystemApi::uploadFile);
        app.get("/api/fs/{image}/download/**", filesystemApi::downloadFile);
        
        // Graph and analysis API routes
        app.get("/api/graph/{image}", graphApi::getGraph);
        app.get("/api/graph/{image}/expert", graphApi::getExpertGraph);
        app.get("/api/analysis/{image}", graphApi::getAnalysis);
        app.get("/api/fragmentation/{image}", graphApi::getFragmentationAnalysis);
        
        // WebSocket endpoint
        app.ws("/ws", this::configureWebSocket);
        
        // Serve index.html at root path
        app.get("/", ctx -> {
            if (developmentMode) {
                ctx.redirect("http://localhost:3000/");
            } else {
                ctx.result(getClass().getClassLoader().getResourceAsStream("static/index.html"));
                ctx.contentType("text/html");
            }
        });
        
        // SPA fallback for common frontend routes
        app.get("/dashboard", ctx -> {
            if (developmentMode) {
                ctx.redirect("http://localhost:3000/dashboard");
            } else {
                ctx.result(getClass().getClassLoader().getResourceAsStream("static/index.html"));
                ctx.contentType("text/html");
            }
        });
        
        app.get("/images", ctx -> {
            if (developmentMode) {
                ctx.redirect("http://localhost:3000/images");
            } else {
                ctx.result(getClass().getClassLoader().getResourceAsStream("static/index.html"));
                ctx.contentType("text/html");
            }
        });
        
        app.get("/filesystem/*", ctx -> {
            if (developmentMode) {
                ctx.redirect("http://localhost:3000" + ctx.path());
            } else {
                ctx.result(getClass().getClassLoader().getResourceAsStream("static/index.html"));
                ctx.contentType("text/html");
            }
        });
        
        app.get("/graph/*", ctx -> {
            if (developmentMode) {
                ctx.redirect("http://localhost:3000" + ctx.path());
            } else {
                ctx.result(getClass().getClassLoader().getResourceAsStream("static/index.html"));
                ctx.contentType("text/html");
            }
        });
        
        // Global exception handler
        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("Unhandled exception: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500).json(new ErrorResponse("Internal server error", e.getMessage()));
        });
    }
    
    private void configureWebSocket(WsConfig ws) {
        ws.onConnect(ctx -> webSocketHandler.onConnect(ctx));
        ws.onMessage(ctx -> webSocketHandler.onMessage(ctx, ctx.message()));
        ws.onClose(ctx -> webSocketHandler.onClose(ctx, ctx.status(), ctx.reason()));
        ws.onError(ctx -> webSocketHandler.onError(ctx, ctx.error()));
    }
    
    public void start() {
        System.out.println("Starting JFAT Web Server...");
        System.out.println("Port: " + port);
        System.out.println("Development mode: " + developmentMode);
        
        if (developmentMode) {
            System.out.println("\nDevelopment mode instructions:");
            System.out.println("1. Start the frontend dev server: cd src/main/frontend && npm run dev");
            System.out.println("2. Access the application at: http://localhost:3000");
            System.out.println("3. API will be available at: http://localhost:" + port + "/api");
        } else {
            System.out.println("\nAccess the application at: http://localhost:" + port);
        }
        
        app.start(port);
        
        System.out.println("JFAT Web Server started successfully!");
    }
    
    public void stop() {
        app.stop();
    }
    
    // Response DTOs
    public static class HealthResponse {
        public final String status;
        public final long timestamp;
        
        public HealthResponse(String status, long timestamp) {
            this.status = status;
            this.timestamp = timestamp;
        }
    }
    
    public static class ErrorResponse {
        public final String error;
        public final String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }
    
    public static void main(String[] args) {
        int port = 8080;
        boolean developmentMode = false;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--dev":
                case "-d":
                    developmentMode = true;
                    break;
                default:
                    // Try to parse as port number
                    try {
                        port = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Unknown argument: " + args[i]);
                    }
                    break;
            }
        }
        
        FatWebServer server = new FatWebServer(port, developmentMode);
        
        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down JFAT Web Server...");
            server.stop();
        }));
        
        server.start();
    }
} 