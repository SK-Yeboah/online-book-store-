# AWS Restore Runbook — Online Book Store

> **Purpose**: Recreate the full AWS lab environment from scratch.  
> **Region**: `us-east-1`  
> **Account**: `177555066761`  
> **Last captured**: 2026-03-25

> **Preferred path:** use Terraform instead of this manual checklist — see [`infra/README.md`](../infra/README.md).  
> This runbook remains the human-readable reference for what Terraform creates.

---

## Architecture Diagram

```
Internet
    │
  [ALB]  sg_alb (port 80/443 from 0.0.0.0/0)
    │    public subnets: 10.0.1.0/24 (1a), 10.0.2.0/24 (1b)
    │
  [ECS Fargate]  sg_app (port 8080 from sg_alb only)
    │            private-app subnets: 10.0.10.0/24 (1a), 10.0.11.0/24 (1b)
    │
    ├──► [RDS MySQL 8.0]  sg_db (port 3306 from sg_app only)
    │                     private-data subnets: 10.0.20.0/24 (1a), 10.0.21.0/24 (1b)
    │
    └──► [ElastiCache Redis Serverless]  sg_redis (port 6379 from sg_app only)
                                          private-data subnets (same as RDS)
```

---

## 1. VPC & Networking

### 1.1 Create VPC
| Field | Value |
|-------|-------|
| Name | `bookstore-vpc` |
| IPv4 CIDR | `10.0.0.0/16` |
| IPv6 | Disabled |
| Tenancy | Default |

### 1.2 Create Subnets (6 total)

| Name | CIDR | AZ | Public |
|------|------|----|--------|
| `bookstore-public-1a` | `10.0.1.0/24` | us-east-1a | ✅ Yes |
| `bookstore-public-1b` | `10.0.2.0/24` | us-east-1b | ✅ Yes |
| `bookstore-app-1a` | `10.0.10.0/24` | us-east-1a | ❌ No |
| `bookstore-app-1b` | `10.0.11.0/24` | us-east-1b | ❌ No |
| `bookstore-data-1a` | `10.0.20.0/24` | us-east-1a | ❌ No |
| `bookstore-data-1b` | `10.0.21.0/24` | us-east-1b | ❌ No |

Enable **Auto-assign public IPv4** on both public subnets.

### 1.3 Internet Gateway
- Name: `bookstore-igw`
- Attach to `bookstore-vpc`

### 1.4 NAT Gateway
- Name: `bookstore-nat`
- Subnet: `bookstore-public-1a`
- Connectivity: **Public**
- Allocate a new Elastic IP

### 1.5 Route Tables

**bookstore-rt-public**
- Associate: `bookstore-public-1a`, `bookstore-public-1b`
- Routes: `0.0.0.0/0` → Internet Gateway

**bookstore-rt-private**
- Associate: `bookstore-app-1a`, `bookstore-app-1b`, `bookstore-data-1a`, `bookstore-data-1b`
- Routes: `0.0.0.0/0` → NAT Gateway

---

## 2. Security Groups (all in `bookstore-vpc`)

### sg_alb
| Direction | Protocol | Port | Source |
|-----------|----------|------|--------|
| Inbound | TCP | 80 | `0.0.0.0/0` |
| Inbound | TCP | 443 | `0.0.0.0/0` |

### sg_app
| Direction | Protocol | Port | Source |
|-----------|----------|------|--------|
| Inbound | TCP | 8080 | `sg_alb` |

### sg_db
| Direction | Protocol | Port | Source |
|-----------|----------|------|--------|
| Inbound | TCP | 3306 | `sg_app` |

### sg_redis
| Direction | Protocol | Port | Source |
|-----------|----------|------|--------|
| Inbound | TCP | 6379 | `sg_app` |

> All SGs: leave default outbound rule (`0.0.0.0/0` all traffic).

---

## 3. Secrets Manager

Create these three secrets (type: **Other type of secret**, plaintext):

| Secret Name | Value | Notes |
|-------------|-------|-------|
| `bookstore/jwt-secret` | `<your 64+ char random string>` | Plaintext string |
| `bookstore/db-password` | `<DB master password>` | Plaintext string |
| `bookstore/ghcr-credentials` | `{"username":"sk-yeboah","password":"<GitHub PAT>"}` | JSON object |

> The RDS-managed secret (`rds!db-...`) is created automatically when you enable Secrets Manager on the RDS instance.

---

## 4. RDS MySQL

| Field | Value |
|-------|-------|
| Engine | MySQL 8.0.40 |
| Template | Free tier |
| DB identifier | `bookstore-db` |
| Master username | `admin` |
| Master password | Managed by Secrets Manager (tick the checkbox) |
| Instance class | `db.t4g.micro` |
| Storage | 20 GiB, gp2 |
| Multi-AZ | No |
| VPC | `bookstore-vpc` |
| Subnet group | `bookstore-db-subnet-group` (data subnets: `bookstore-data-1a`, `bookstore-data-1b`) |
| Public access | No |
| VPC security group | `sg_db` |
| Initial DB name | *(leave blank — app creates it via `createDatabaseIfNotExist=true`)* |
| Backup retention | 1 day |
| Deletion protection | Disabled (lab only) |

**RDS Endpoint** (will change when recreated — update ECS task def env vars):  
`bookstore-db.<new-id>.us-east-1.rds.amazonaws.com`

---

## 5. ElastiCache Redis (Serverless)

| Field | Value |
|-------|-------|
| Name | `bookstore-redis` |
| Engine | Redis OSS |
| Mode | **Serverless** |
| VPC | `bookstore-vpc` |
| Subnets | `bookstore-data-1a`, `bookstore-data-1b` |
| Security group | `sg_redis` |
| TLS | **Enabled** (required for serverless) |

**Redis Endpoint** (will change when recreated — update ECS task def env vars):  
`bookstore-redis-<id>.serverless.use1.cache.amazonaws.com`

---

## 6. IAM Roles

### ecsTaskExecutionRole
- **Trust**: `ecs-tasks.amazonaws.com`
- **Managed policy**: `AmazonECSTaskExecutionRolePolicy`
- **Inline policy** `BookstoreSecretsAccess`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:bookstore/*",
        "arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:rds!db-*"
      ]
    }
  ]
}
```

> Use wildcard `rds!db-*` so the ARN suffix doesn't need updating after recreation.

### bookstore-ecs-task-role
- **Trust**: `ecs-tasks.amazonaws.com`
- **Managed policy**: `AmazonECSTaskExecutionRolePolicy`
- *(Add more policies here if the app needs to call other AWS services)*

---

## 7. Application Load Balancer

### Target Group
| Field | Value |
|-------|-------|
| Name | `bookstore-tg` |
| Target type | IP |
| Protocol | HTTP |
| Port | 8080 |
| VPC | `bookstore-vpc` |
| Health check path | `/actuator/health` |
| Healthy threshold | 2 |
| Unhealthy threshold | 3 |

### ALB
| Field | Value |
|-------|-------|
| Name | `bookstore-alb` |
| Scheme | Internet-facing |
| Subnets | `bookstore-public-1a`, `bookstore-public-1b` |
| Security group | `sg_alb` |

### Listener
| Port | Protocol | Default action |
|------|----------|----------------|
| 80 | HTTP | Forward → `bookstore-tg` |

---

## 8. ECS

### Cluster
| Field | Value |
|-------|-------|
| Name | `bookstore-cluster` |
| Infrastructure | AWS Fargate |

### Task Definition — `bookstore-task`

| Field | Value |
|-------|-------|
| Family | `bookstore-task` |
| CPU | 1 vCPU (1024) |
| Memory | 3 GiB (3072) |
| Task execution role | `ecsTaskExecutionRole` |
| Task role | `bookstore-ecs-task-role` |
| Container name | `bookstore-app` |
| Image | `ghcr.io/sk-yeboah/online-book-store:latest` |
| Container port | 8080 |

**Environment variables** (container):

| Name | Value |
|------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_USERNAME` | `admin` |
| `DB_HOST` | `<new RDS endpoint>` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `online_book_store` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<new RDS endpoint>:3306/online_book_store?useUnicode=true&characterEncoding=utf8&useSSL=true&requireSSL=true&createDatabaseIfNotExist=true` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `none` *(temporary — change to `validate` after V6 migration merges to main)* |
| `REDIS_HOST` | `<new ElastiCache endpoint>` |
| `REDIS_PORT` | `6379` |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `true` |
| `CORS_ALLOWED_ORIGINS` | `*` |

**Secrets** (from Secrets Manager via `valueFrom`):

| Env var name | SecretARN:jsonKey |
|---|---|
| `DB_PASSWORD` | `arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:rds!db-<new-id>:password::` |
| `JWT_SECRET` | `arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:bookstore/jwt-secret-<suffix>` |

> ⚠️ **Critical**: DB_PASSWORD and JWT_SECRET must go in the **Secrets** array with `valueFrom`, NOT in the Environment array. Putting the ARN in Environment passes it as a literal string.

**Log configuration**:
- Driver: `awslogs`
- Group: `/ecs/bookstore-task`
- Region: `us-east-1`
- Stream prefix: `ecs`

### ECS Service
| Field | Value |
|-------|-------|
| Name | `bookstore-service` |
| Cluster | `bookstore-cluster` |
| Task definition | `bookstore-task` (latest revision) |
| Launch type | Fargate |
| Desired count | 1 |
| Subnets | `bookstore-app-1a`, `bookstore-app-1b` |
| Security group | `sg_app` |
| Auto-assign public IP | Enabled |
| Load balancer | `bookstore-alb` |
| Container to load balance | `bookstore-app:8080` |
| Target group | `bookstore-tg` |

---

## 9. GitHub Actions Secrets (repository settings)

These must be set in the GitHub repo → Settings → Secrets and variables → Actions:

| Secret name | Value |
|-------------|-------|
| `AWS_ACCESS_KEY_ID` | Your IAM user access key |
| `AWS_SECRET_ACCESS_KEY` | Your IAM user secret key |
| `GHCR_PAT` | GitHub Personal Access Token (scope: `write:packages`) |

---

## 10. Post-Restore Checklist

1. **Update task definition** with the new RDS endpoint and new ElastiCache endpoint.
2. **Update `BookstoreSecretsAccess` inline policy** with the new `rds!db-<new-id>` ARN.
3. **Force new deployment** of `bookstore-service` to pull the updated task definition.
4. **Verify health**: `curl http://<new-ALB-DNS>/actuator/health`
5. **Merge PR #4** (V6 migration) to `main` so Flyway fixes the `orders.status` column type, then change `SPRING_JPA_HIBERNATE_DDL_AUTO` back to `validate`.
6. **Wire deploy job** in `.github/workflows/ci-cd.yml`:
   - Uncomment the `deploy` job.
   - Set `ECS_SERVICE`, `ECS_CLUSTER`, `CONTAINER_NAME` to the values above.

---

## 11. Teardown Checklist (save costs)

Delete in this order to avoid dependency errors:

1. ECS Service (set desired count → 0, then delete service)
2. ECS Cluster
3. ALB (delete load balancer)
4. Target Group
5. RDS instance (no final snapshot needed for lab)
6. ElastiCache serverless cache
7. NAT Gateway (⚠️ wait for it to reach `deleted` state — costs ~$0.045/hr)
8. Release Elastic IP
9. Secrets (`bookstore/db-password`, `bookstore/jwt-secret`, `bookstore/ghcr-credentials`)  
   *(The `rds!db-*` secret is deleted automatically with RDS)*
10. IAM inline policy `BookstoreSecretsAccess` from `ecsTaskExecutionRole`
11. IAM roles `bookstore-ecs-task-role` (optional, harmless to keep)
12. Security groups: `sg_app`, `sg_db`, `sg_redis`, `sg_alb`
13. Route tables: `bookstore-rt-public`, `bookstore-rt-private`
14. Subnets (all 6)
15. Internet Gateway (detach then delete)
16. VPC

> The ECS task definitions and CloudWatch log groups are free and can be left.
