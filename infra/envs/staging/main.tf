terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Lab default: local state. For shared/team use, configure an S3 backend:
  # backend "s3" {
  #   bucket         = "your-tf-state-bucket"
  #   key            = "bookstore/staging/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "online-book-store"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name_prefix = var.name_prefix
  tags        = {}
}

module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  vpc_cidr    = var.vpc_cidr
  tags        = local.tags
}

module "data" {
  source = "../../modules/data"

  name_prefix             = local.name_prefix
  data_subnet_ids         = module.network.data_subnet_ids
  db_security_group_id    = module.network.db_security_group_id
  redis_security_group_id = module.network.redis_security_group_id
  db_name                 = var.db_name
  db_username             = var.db_username
  db_instance_class       = var.db_instance_class
  tags                    = local.tags
}

module "alb" {
  source = "../../modules/alb"

  name_prefix           = local.name_prefix
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = module.network.alb_security_group_id
  tags                  = local.tags
}

module "ecs" {
  source = "../../modules/ecs"

  name_prefix               = local.name_prefix
  app_subnet_ids            = module.network.app_subnet_ids
  app_security_group_id     = module.network.app_security_group_id
  target_group_arn          = module.alb.target_group_arn
  db_endpoint               = module.data.db_endpoint
  db_port                   = module.data.db_port
  db_name                   = module.data.db_name
  db_username               = module.data.db_username
  db_master_user_secret_arn = module.data.db_master_user_secret_arn
  redis_endpoint            = module.data.redis_endpoint
  redis_port                = module.data.redis_port
  container_image           = var.container_image
  container_name            = var.container_name
  cpu                       = var.cpu
  memory                    = var.memory
  desired_count             = var.desired_count
  ghcr_username             = var.ghcr_username
  ghcr_token                = var.ghcr_token
  paystack_secret_key       = var.paystack_secret_key
  paystack_public_key       = var.paystack_public_key
  payment_callback_url      = var.payment_callback_url
  payment_webhook_secret    = var.payment_webhook_secret
  cors_allowed_origins      = var.cors_allowed_origins
  payment_provider          = var.payment_provider
  payment_currency          = var.payment_currency
  rate_limit_capacity       = var.rate_limit_capacity
  rate_limit_refill         = var.rate_limit_refill
  tags                      = local.tags
}
