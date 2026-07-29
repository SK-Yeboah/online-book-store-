output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_port" {
  value = aws_db_instance.this.port
}

output "db_name" {
  value = aws_db_instance.this.db_name
}

output "db_username" {
  value = aws_db_instance.this.username
}

output "db_master_user_secret_arn" {
  description = "Secrets Manager ARN for RDS-managed master password (JSON key: password)"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "redis_endpoint" {
  value = aws_elasticache_serverless_cache.this.endpoint[0].address
}

output "redis_port" {
  value = aws_elasticache_serverless_cache.this.endpoint[0].port
}
