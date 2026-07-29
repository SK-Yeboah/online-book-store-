output "alb_dns_name" {
  description = "Public ALB DNS - curl http://THIS/actuator/health"
  value       = module.alb.alb_dns_name
}

output "health_url" {
  value = "http://${module.alb.alb_dns_name}/actuator/health"
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
