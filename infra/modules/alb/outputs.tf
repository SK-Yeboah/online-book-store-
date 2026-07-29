output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "alb_arn" {
  value = aws_lb.this.arn
}

output "alb_zone_id" {
  value = aws_lb.this.zone_id
}

output "target_group_arn" {
  value = aws_lb_target_group.this.arn
}

output "enable_https" {
  value = var.enable_https
}

output "domain_name" {
  value = var.enable_https ? var.domain_name : null
}

output "acm_certificate_arn" {
  value = var.enable_https ? aws_acm_certificate_validation.this[0].certificate_arn : null
}

output "app_base_url" {
  description = "Public base URL for the API (https when enabled, else http ALB DNS)"
  value       = var.enable_https ? "https://${var.domain_name}" : "http://${aws_lb.this.dns_name}"
}

output "health_url" {
  value = var.enable_https ? "https://${var.domain_name}/actuator/health" : "http://${aws_lb.this.dns_name}/actuator/health"
}

output "paystack_webhook_url" {
  value = var.enable_https ? "https://${var.domain_name}/api/payments/webhook/paystack" : "http://${aws_lb.this.dns_name}/api/payments/webhook/paystack"
}
