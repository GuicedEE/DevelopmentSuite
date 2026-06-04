FROM debian:bookworm-slim

# Install minimal runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r app && useradd -r -g app -d /app app

# Copy the jlink runtime image (must be built on Linux/amd64)
COPY target/maven-jlink/default/bin /opt/app/bin
COPY target/maven-jlink/default/conf /opt/app/conf
COPY target/maven-jlink/default/lib /opt/app/lib
COPY target/maven-jlink/default/release /opt/app/release

# Set permissions
RUN chmod +x /opt/app/bin/*

# Switch to non-root user
USER app
WORKDIR /app

# Expose default Vert.x port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD /opt/app/bin/java -version || exit 1

# Launch via the jlink launcher script
ENTRYPOINT ["/opt/app/bin/site-launcher"]






