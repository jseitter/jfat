# GitHub Environments Configuration

This document describes the GitHub environments used in the JFAT project CI/CD pipeline.

## Environments

### Staging
- **Purpose**: Build verification and artifact validation
- **Workflow**: CI (`.github/workflows/ci.yml`)
- **Protection Rules**: None (automatic deployment)
- **Reviewers**: None required
- **URL**: https://github.com/jseitter/jfat/actions

**Triggers:**
- Successful completion of all test jobs
- Used for build verification before potential releases

### Production
- **Purpose**: Package publication and release distribution
- **Workflow**: Release (`.github/workflows/release.yml`)
- **Protection Rules**: Manual approval recommended
- **Reviewers**: Repository maintainers
- **URL**: https://github.com/jseitter/jfat/packages

**Triggers:**
- Release publication
- Manual workflow dispatch

## Setting Up Environment Protection Rules

To configure environment protection rules in your repository:

1. Go to **Settings** > **Environments**
2. Click on the environment name (e.g., "production")
3. Configure protection rules:

### Recommended Production Environment Settings:
- ✅ **Required reviewers**: Add repository maintainers
- ✅ **Wait timer**: 0 minutes (or add delay if needed)
- ✅ **Deployment branches**: Only protected branches (main)
- ✅ **Environment secrets**: Add any production-specific secrets

### Environment Variables and Secrets

#### Production Environment
- `GITHUB_TOKEN`: Automatically provided by GitHub Actions
- `GITHUB_ACTOR`: Automatically provided by GitHub Actions

#### Staging Environment
- No additional secrets required
- Uses standard GitHub Actions environment variables

## Benefits

1. **Deployment Safety**: Manual approval gates for production releases
2. **Audit Trail**: Clear visibility of who approved deployments
3. **Branch Protection**: Only allow deployments from protected branches
4. **Environment Isolation**: Separate staging and production environments

## Usage in Workflows

### CI Workflow (Staging)
```yaml
environment:
  name: staging
  url: https://github.com/jseitter/jfat/actions
```

### Release Workflow (Production)
```yaml
environment:
  name: production
  url: https://github.com/jseitter/jfat/packages
```

## Notes

- As of [GitHub's May 15, 2025 update](https://github.blog/changelog/2025-05-15-new-releases-for-github-actions/), Actions environments are now available for all plans in private repositories
- Environment protection rules provide an additional layer of security for production deployments
- The staging environment serves as a final verification step before artifacts could potentially be released 