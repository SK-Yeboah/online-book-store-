data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id   = data.aws_caller_identity.current.account_id
  region       = data.aws_region.current.name
  create_ghcr  = var.ghcr_username != "" && var.ghcr_token != ""
}

# ── Secrets ──────────────────────────────────────────────────────────────────

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${var.name_prefix}/jwt-secret"
  recovery_window_in_days = 0
  tags                    = merge(var.tags, { Name = "${var.name_prefix}/jwt-secret" })
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}

resource "aws_secretsmanager_secret" "ghcr" {
  count = local.create_ghcr ? 1 : 0

  name                    = "${var.name_prefix}/ghcr-credentials"
  recovery_window_in_days = 0
  tags                    = merge(var.tags, { Name = "${var.name_prefix}/ghcr-credentials" })
}

resource "aws_secretsmanager_secret_version" "ghcr" {
  count = local.create_ghcr ? 1 : 0

  secret_id = aws_secretsmanager_secret.ghcr[0].id
  secret_string = jsonencode({
    username = var.ghcr_username
    password = var.ghcr_token
  })
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name_prefix}/app-config"
  recovery_window_in_days = 0
  tags                    = merge(var.tags, { Name = "${var.name_prefix}/app-config" })
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    PAYSTACK_SECRET_KEY     = var.paystack_secret_key
    PAYSTACK_PUBLIC_KEY     = var.paystack_public_key
    PAYMENT_CALLBACK_URL    = var.payment_callback_url
    PAYMENT_WEBHOOK_SECRET  = var.payment_webhook_secret
    CORS_ALLOWED_ORIGINS    = var.cors_allowed_origins
  })
}

# ── IAM ──────────────────────────────────────────────────────────────────────

resource "aws_iam_role" "execution" {
  name = "${var.name_prefix}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = var.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "${var.name_prefix}-secrets-access"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = compact([
          "arn:aws:secretsmanager:${local.region}:${local.account_id}:secret:${var.name_prefix}/*",
          "arn:aws:secretsmanager:${local.region}:${local.account_id}:secret:rds!db-*",
          var.db_master_user_secret_arn,
        ])
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name = "${var.name_prefix}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })

  tags = var.tags
}

# ── Logs / Cluster / Task / Service ──────────────────────────────────────────

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.name_prefix}-task"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-cluster" })
}

resource "aws_ecs_task_definition" "this" {
  family                   = "${var.name_prefix}-task"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    merge(
      {
        name      = var.container_name
        image     = var.container_image
        essential = true
        portMappings = [{
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }]
        environment = [
          { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
          { name = "DB_HOST", value = var.db_endpoint },
          { name = "DB_PORT", value = tostring(var.db_port) },
          { name = "DB_NAME", value = var.db_name },
          { name = "DB_USERNAME", value = var.db_username },
          { name = "REDIS_HOST", value = var.redis_endpoint },
          { name = "REDIS_PORT", value = tostring(var.redis_port) },
          { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
          { name = "PAYMENT_PROVIDER", value = var.payment_provider },
          { name = "PAYMENT_CURRENCY", value = var.payment_currency },
          { name = "SECURITY_RATE_LIMIT_CAPACITY", value = tostring(var.rate_limit_capacity) },
          { name = "SECURITY_RATE_LIMIT_REFILL_PER_MINUTE", value = tostring(var.rate_limit_refill) },
        ]
        secrets = [
          { name = "DB_PASSWORD", valueFrom = "${var.db_master_user_secret_arn}:password::" },
          { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
          { name = "PAYSTACK_SECRET_KEY", valueFrom = "${aws_secretsmanager_secret.app.arn}:PAYSTACK_SECRET_KEY::" },
          { name = "PAYSTACK_PUBLIC_KEY", valueFrom = "${aws_secretsmanager_secret.app.arn}:PAYSTACK_PUBLIC_KEY::" },
          { name = "PAYMENT_CALLBACK_URL", valueFrom = "${aws_secretsmanager_secret.app.arn}:PAYMENT_CALLBACK_URL::" },
          { name = "PAYMENT_WEBHOOK_SECRET", valueFrom = "${aws_secretsmanager_secret.app.arn}:PAYMENT_WEBHOOK_SECRET::" },
          { name = "CORS_ALLOWED_ORIGINS", valueFrom = "${aws_secretsmanager_secret.app.arn}:CORS_ALLOWED_ORIGINS::" },
        ]
        logConfiguration = {
          logDriver = "awslogs"
          options = {
            "awslogs-group"         = aws_cloudwatch_log_group.app.name
            "awslogs-region"        = local.region
            "awslogs-stream-prefix" = "ecs"
          }
        }
      },
      local.create_ghcr ? {
        repositoryCredentials = {
          credentialsParameter = aws_secretsmanager_secret.ghcr[0].arn
        }
      } : {}
    )
  ])

  tags = var.tags
}

resource "aws_ecs_service" "this" {
  name            = "${var.name_prefix}-service"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.app_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = var.container_name
    container_port   = 8080
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  depends_on = [aws_iam_role_policy.execution_secrets]

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-service" })
}
