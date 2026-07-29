output "cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "cluster_arn" {
  value = aws_ecs_cluster.this.arn
}

output "service_name" {
  value = aws_ecs_service.this.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.this.family
}

output "task_definition_arn" {
  value = aws_ecs_task_definition.this.arn
}

output "container_name" {
  value = var.container_name
}

output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt.arn
}

output "app_config_secret_arn" {
  value = aws_secretsmanager_secret.app.arn
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.app.name
}
