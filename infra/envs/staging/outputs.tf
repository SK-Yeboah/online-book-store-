output "alb_dns_name" {
  description = "Raw ALB DNS (HTTP). Prefer app_base_url when HTTPS is enabled."
  value       = module.alb.alb_dns_name
}

output "app_base_url" {
  description = "Public API base URL"
  value       = module.alb.app_base_url
}

output "health_url" {
  description = "Health check URL"
  value       = module.alb.health_url
}

output "paystack_webhook_url" {
  description = "Paste into Paystack Test Webhook URL (requires HTTPS)"
  value       = module.alb.paystack_webhook_url
}

output "acm_certificate_arn" {
  value = module.alb.acm_certificate_arn
}

output "ecs_cluster_name" {
  description = "GitHub Actions var: STAGING_ECS_CLUSTER"
  value       = module.ecs.cluster_name
}

output "ecs_service_name" {
  description = "GitHub Actions var: STAGING_ECS_SERVICE"
  value       = module.ecs.service_name
}

output "ecs_task_definition_family" {
  description = "GitHub Actions var: STAGING_ECS_TASK_DEFINITION"
  value       = module.ecs.task_definition_family
}

output "container_name" {
  value = module.ecs.container_name
}

output "db_endpoint" {
  value = module.data.db_endpoint
}

output "redis_endpoint" {
  value = module.data.redis_endpoint
}

output "log_group_name" {
  value = module.ecs.log_group_name
}

output "github_actions_vars" {
  description = "Copy these into GitHub → Settings → Variables"
  value = {
    STAGING_ECS_CLUSTER         = module.ecs.cluster_name
    STAGING_ECS_SERVICE         = module.ecs.service_name
    STAGING_ECS_TASK_DEFINITION = module.ecs.task_definition_family
  }
}
