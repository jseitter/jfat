# JFAT CI/CD Guide

This guide explains JFAT's automated build, test, and release processes using GitHub Actions.

## Overview

JFAT uses GitHub Actions for continuous integration and automated releases. The CI/CD pipeline includes:

- **Continuous Integration**: Multi-Java testing on every push and pull request
- **Security Scanning**: Automated vulnerability and code quality checks
- **Automated Releases**: Dual-artifact publishing to GitHub Package Registry and GitHub Releases

## GitHub Actions Workflows

### 1. Continuous Integration (`.github/workflows/ci.yml`)

The CI workflow runs on every push and pull request to ensure code quality and compatibility.

#### Triggers
```yaml
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
```

#### Multi-Java Testing Matrix
Tests against multiple Java versions to ensure compatibility:
- **Java 11**: Minimum supported version
- **Java 17**: LTS version
- **Java 21**: Latest LTS version

#### CI Steps
1. **Checkout**: Download source code
2. **Setup Java**: Configure Java environment
3. **Cache Gradle**: Cache dependencies for faster builds
4. **Build**: Compile code and run tests
5. **Test Reporting**: Generate and upload test results
6. **Coverage**: Collect code coverage using JaCoCo
7. **Artifact Upload**: Store build artifacts for download

#### Example CI Output
```
✓ Java 11 - Build and Test (3m 45s)
✓ Java 17 - Build and Test (3m 32s)  
✓ Java 21 - Build and Test (3m 28s)
✓ Upload Test Reports
✓ Upload Coverage Report
```

### 2. Security Scanning (`.github/workflows/security.yml`)

Automated security analysis to identify vulnerabilities and code quality issues.

#### Security Features
- **OWASP Dependency Check**: Scans for vulnerable dependencies
- **CodeQL Analysis**: GitHub's semantic code analysis for security vulnerabilities
- **Scheduled Scans**: Weekly automated security audits

#### Triggers
```yaml
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  schedule:
    - cron: '0 2 * * 1'  # Weekly on Monday at 2 AM UTC
```

#### Security Steps
1. **Dependency Check**: OWASP dependency vulnerability scan
2. **CodeQL Init**: Initialize GitHub CodeQL analysis
3. **Build**: Compile code for analysis
4. **CodeQL Analysis**: Perform semantic code analysis
5. **Security Report**: Generate security findings report

### 3. Release Workflow (`.github/workflows/release.yml`)

Automated release process that publishes to both GitHub Package Registry and GitHub Releases.

#### Dual-Artifact Strategy
The release workflow implements a comprehensive dual-artifact publishing strategy:

1. **GitHub Package Registry**: 
   - Primary distribution channel for Maven/Gradle dependencies
   - Includes main JAR, sources, and Javadoc
   - Supports version resolution and dependency management

2. **GitHub Releases**:
   - User-friendly download interface
   - Direct JAR file downloads
   - Release notes and changelog
   - Binary asset distribution

#### Triggers
```yaml
on:
  push:
    tags:
      - 'v*'  # Triggered by version tags (e.g., v1.0.0)
  workflow_dispatch:
    inputs:
      version:
        description: 'Release version'
        required: true
```

#### Release Steps
1. **Version Management**: Extract version from tag or input
2. **Build Artifacts**: Create JAR, sources, and Javadoc
3. **Publish to Package Registry**: Upload to GitHub Package Registry
4. **Create GitHub Release**: Create release with artifacts
5. **Artifact Attachment**: Attach JARs to GitHub release

#### Why Both Distribution Channels?

**GitHub Package Registry Benefits:**
- **Dependency Management**: Seamless integration with Gradle/Maven
- **Version Resolution**: Automatic handling of version constraints
- **Metadata**: Rich package metadata and dependency information
- **Corporate Usage**: Enterprise-friendly package management

**GitHub Releases Benefits:**
- **Accessibility**: No authentication required for public downloads
- **User Experience**: Simple download interface for end users
- **Documentation**: Integrated release notes and changelogs
- **Discoverability**: Easy to find latest releases

## Version Management

### Single Source of Truth: `version.txt`

JFAT uses a simple and reliable versioning approach:

```
# version.txt
1.2.3
```

#### Integration Points
- **Gradle Build**: `version = file('version.txt').text.trim()`
- **Release Automation**: GitHub Actions reads and updates this file
- **Documentation**: Version displayed in CLI and documentation

#### Benefits
- **Simplicity**: One file contains the canonical version
- **Reliability**: No complex sed operations or multi-file updates
- **Automation**: Easy for scripts to read and update
- **Git Tracking**: Version changes are clearly tracked in Git history

### Release Process

#### Automated Release (Recommended)
```bash
# 1. Create and push a version tag
git tag v1.2.3
git push origin v1.2.3

# 2. GitHub Actions automatically:
#    - Updates version.txt
#    - Builds all artifacts
#    - Publishes to Package Registry
#    - Creates GitHub Release
#    - Attaches JARs to release
```

#### Manual Release
```bash
# Use GitHub Actions workflow dispatch
gh workflow run release.yml -f version=1.2.3
```

#### Release Artifacts Generated
1. **jfat-1.2.3.jar**: Main application JAR with dependencies
2. **jfat-1.2.3-sources.jar**: Source code JAR
3. **jfat-1.2.3-javadoc.jar**: Javadoc documentation JAR

## GitHub Environments

JFAT uses GitHub environments for deployment safety and audit trails:

### Environment Configuration
- **Staging**: Used during CI builds (automatic approval)
- **Production**: Used for releases (optional manual approval)

### Environment Benefits
- **Deployment Protection**: Optional approval gates for sensitive releases
- **Audit Trail**: Track who approved which deployments
- **Secret Management**: Environment-specific secrets and variables
- **Access Control**: Restrict who can deploy to production

### Setup Instructions
```yaml
# .github/environments.md configuration
environments:
  staging:
    url: https://github.com/jseitter/jfat/packages
    protection_rules:
      required_reviewers: []
      
  production:
    url: https://github.com/jseitter/jfat/releases
    protection_rules:
      required_reviewers: ['@jseitter']  # Optional: require approval
```

## Monitoring and Status

### Build Status Badges
Monitor current build status with badges in README:

```markdown
[![CI](https://github.com/jseitter/jfat/actions/workflows/ci.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/ci.yml)
[![Security](https://github.com/jseitter/jfat/actions/workflows/security.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/security.yml)
[![Release](https://github.com/jseitter/jfat/actions/workflows/release.yml/badge.svg)](https://github.com/jseitter/jfat/actions/workflows/release.yml)
```

### Workflow Monitoring
- **CI Runs**: Monitor test results across Java versions
- **Security Scans**: Review security findings and recommendations
- **Release Status**: Track release deployment progress

### Failure Notifications
- **Failed Builds**: Automatic notifications to maintainers
- **Security Issues**: Alerts for new vulnerabilities
- **Release Problems**: Notifications for failed deployments

## Package Registry Usage

### Consuming from Package Registry

#### Gradle Configuration
```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/jseitter/jfat")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'net.seitter.jfat:jfat:1.2.3'
}
```

#### Maven Configuration
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/jseitter/jfat</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>net.seitter.jfat</groupId>
        <artifactId>jfat</artifactId>
        <version>1.2.3</version>
    </dependency>
</dependencies>
```

### Authentication Setup
```bash
# Environment variables (recommended)
export GITHUB_ACTOR=your-username
export GITHUB_TOKEN=ghp_your_token_here

# Or Gradle properties
echo "gpr.user=your-username" >> ~/.gradle/gradle.properties
echo "gpr.key=ghp_your_token_here" >> ~/.gradle/gradle.properties
```

## Troubleshooting CI/CD Issues

### Common Build Failures

#### Test Failures
```bash
# Check specific test failure
./gradlew test --tests "FailingTestClass" --info

# Run with debug output
./gradlew test -Djfat.debug=true --stacktrace
```

#### Dependency Issues
```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches/

# Force dependency refresh
./gradlew build --refresh-dependencies
```

#### Memory Issues
```bash
# Increase CI memory limits in workflow
env:
  GRADLE_OPTS: -Xmx2g -XX:MaxMetaspaceSize=512m
```

### Release Troubleshooting

#### Failed Package Publication
1. **Check Authentication**: Verify GITHUB_TOKEN has packages:write permission
2. **Version Conflicts**: Ensure version doesn't already exist
3. **Gradle Configuration**: Verify publishing configuration

#### Failed GitHub Release
1. **Tag Format**: Ensure tag follows vX.Y.Z format
2. **Permissions**: Verify workflow has contents:write permission
3. **Artifact Size**: Check if artifacts exceed GitHub size limits

### Security Scan Issues

#### False Positives
1. **Dependency Check**: Review and suppress known false positives
2. **CodeQL**: Add custom queries to filter irrelevant findings
3. **Update Configuration**: Tune security scanning sensitivity

#### Vulnerability Remediation
1. **Update Dependencies**: Upgrade to patched versions
2. **Alternative Packages**: Replace vulnerable dependencies
3. **Risk Assessment**: Document and track accepted risks

## Best Practices

### CI/CD Optimization
1. **Cache Dependencies**: Use GitHub Actions cache for faster builds
2. **Parallel Execution**: Run independent jobs in parallel
3. **Artifact Retention**: Set appropriate retention periods
4. **Resource Limits**: Configure appropriate timeouts and limits

### Security Best Practices
1. **Token Scoping**: Use minimal permission tokens
2. **Secret Management**: Store sensitive data in GitHub Secrets
3. **Regular Updates**: Keep Actions and dependencies updated
4. **Audit Logs**: Monitor workflow execution logs

### Release Management
1. **Semantic Versioning**: Follow semver for version numbers
2. **Release Notes**: Include comprehensive changelogs
3. **Testing**: Thoroughly test before releases
4. **Rollback Plan**: Maintain ability to rollback releases

This CI/CD guide ensures reliable, automated, and secure software delivery for JFAT across all supported environments and use cases. 