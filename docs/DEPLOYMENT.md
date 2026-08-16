# Deployment

This describes how JobFit is meant to run in AWS: ECS Fargate for both
containers, RDS for Postgres, S3 for resume files, Secrets Manager for
credentials. Nothing here is deployed automatically - `deploy.yml` is a
manual (`workflow_dispatch`) GitHub Actions workflow that only does
anything once the AWS resources below already exist and the repo is
configured with the variables/secrets it expects. `ci.yml`, by contrast,
runs on every push and needs no AWS access at all.

## Architecture

```
Internet
   |
   v
Application Load Balancer (HTTPS, ACM cert)
   |            \
   v             v
ECS Fargate   ECS Fargate
(frontend,    (backend,
 nginx:80)     Spring Boot:8080)
                   |
                   v
              RDS Postgres (private subnet)
                   |
                   v
              S3 bucket (resume files)
```

Both services run as separate ECS Fargate services behind one ALB, routing
by host header or path (e.g. `app.example.com` -> frontend,
`api.example.com` -> backend, or `/api/*` path-based routing to the backend
target group - either works, path-based avoids needing a second
subdomain/cert). The backend talks to RDS and S3 over private subnets;
only the ALB is internet-facing.

## One-time AWS setup

None of this is scripted (deliberately - see the top of this doc); it's a
checklist for setting it up by hand or via your own Terraform/CDK, since
the specific choices (VPC CIDR, existing infra, cost constraints) are
yours to make:

1. **VPC** with at least 2 public subnets (ALB) and 2 private subnets (ECS
   tasks, RDS), NAT gateway for the private subnets' outbound traffic
   (image pulls, S3, Secrets Manager).
2. **ECR repositories**: `jobfit-backend`, `jobfit-frontend`.
3. **RDS Postgres** (16.x) in the private subnets. Note the endpoint,
   database name, username, password.
4. **S3 bucket** for resume files (versioning on, block all public access,
   default SSE-S3 or SSE-KMS encryption).
5. **Secrets Manager** entries, one secret per value (matches
   `infra/ecs-task-definition.json`'s `secrets` block):
   - `jobfit/db-url` - `jdbc:postgresql://<rds-endpoint>:5432/jobfit`
   - `jobfit/db-username`, `jobfit/db-password`
   - `jobfit/jwt-secret` - a random 32+ byte string (`openssl rand -base64 48`)
   - `jobfit/ai-api-key` - blank/omit if using the default `AI_PROVIDER=stub`
6. **IAM roles**:
   - `jobfit-ecs-execution-role` - `AmazonECSTaskExecutionRolePolicy` plus
     `secretsmanager:GetSecretValue` scoped to the `jobfit/*` secrets above.
   - `jobfit-ecs-task-role` - the backend container's runtime identity;
     needs `s3:GetObject`/`PutObject`/`DeleteObject` scoped to the resume
     bucket only.
   - A GitHub Actions OIDC deploy role (trust policy scoped to this repo,
     via GitHub's OIDC provider) with permission to push to the two ECR
     repos and register/update the two ECS services - nothing broader.
7. **ECS cluster** (`jobfit-cluster`) and two services
   (`jobfit-backend-service`, `jobfit-frontend-service`), each pointing at
   an ALB target group, initially created from the task definitions in
   `infra/` with placeholder values filled in by hand for the first deploy.
8. **CloudWatch log groups**: `/ecs/jobfit-backend`, `/ecs/jobfit-frontend`
   (or let the first task run auto-create them, if the execution role has
   `logs:CreateLogGroup`).

## GitHub repository configuration

`deploy.yml` reads these (Settings -> Secrets and variables -> Actions):

**Variables** (`vars.*` - not secret, but repo-specific):
- `AWS_REGION`, `AWS_ACCOUNT_ID`
- `AWS_DEPLOY_ROLE_ARN` - the OIDC deploy role from step 6 above
- `API_BASE_URL` - the backend's public URL, baked into the frontend
  build (`https://api.example.com`, or `https://app.example.com/api` if
  using path-based routing)

No AWS access keys are stored as GitHub secrets - the workflow
authenticates via OIDC (`aws-actions/configure-aws-credentials`), which is
short-lived and scoped to the single deploy role.

## Running a deploy

1. Push to `main` (or have a commit you want deployed already merged).
2. Actions tab -> "Deploy to AWS" -> Run workflow. Leave `image_tag`
   blank to tag the images with the current commit SHA, or set it
   explicitly to re-deploy a specific previously-built tag.
3. The workflow builds and pushes both images to ECR, then registers new
   ECS task definition revisions and updates both services
   (`wait-for-service-stability: true`, so the run doesn't report success
   until the new tasks are actually healthy and the old ones have drained).

Database schema changes ship as ordinary Flyway migrations
(`backend/src/main/resources/db/migration`) and run automatically on
backend container startup - no separate migration step in the pipeline.

## Rollback

ECS keeps every task definition revision. Fastest rollback: in the ECS
console (or `aws ecs update-service --task-definition jobfit-backend:<N>`),
point the service back at the previous revision. No image rebuild needed
as long as that revision's image tag is still in ECR (ECR lifecycle
policies should keep at least the last several tags for this reason).

## Observability

- `GET /actuator/health` (status only, no dependency detail - see
  `application.yml`) is the ALB target group health check.
- `GET /actuator/health/liveness` / `/readiness` back the container-level
  ECS health check in `infra/ecs-task-definition.json`.
- Application logs go to stdout/stderr and land in the CloudWatch log
  groups configured on each task definition - no separate log shipper.

## What's intentionally out of scope here

- No autoscaling policy is defined - a portfolio-scale deployment doesn't
  need it; `desiredCount` on each ECS service is a manual/console setting.
- No WAF/CDN in front of the ALB - would add for a real production launch
  with public traffic, not needed to demonstrate the architecture.
- `RateLimitingFilter` (see `common/security`) is in-memory and
  per-instance - correct for the single-task deployment here, but would
  need a shared store (Redis) or to move to an edge control (WAF rate
  rule) the moment this runs with `desiredCount > 1`.
