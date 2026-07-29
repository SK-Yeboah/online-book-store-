variable "name_prefix" {
  type = string
}

variable "app_subnet_ids" {
  type = list(string)
}

variable "app_security_group_id" {
  type = string
}

variable "target_group_arn" {
  type = string
}

variable "db_endpoint" {
  type = string
}

variable "db_port" {
  type = number
}

variable "db_name" {
  type = string
}

variable "db_username" {
  type = string
}

variable "db_master_user_secret_arn" {
  type = string
}

variable "redis_endpoint" {
  type = string
}

variable "redis_port" {
  type = number
}

variable "container_image" {
  type        = string
  description = "Full image URI (e.g. ghcr.io/org/online-book-store:sha)"
}

variable "container_name" {
  type    = string
  default = "bookstore-app"
}

variable "cpu" {
  type    = number
  default = 1024
}

variable "memory" {
  type    = number
  default = 3072
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "ghcr_username" {
  type    = string
  default = ""
}

variable "ghcr_token" {
  type      = string
  default   = ""
  sensitive = true
}

variable "paystack_secret_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "paystack_public_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "payment_callback_url" {
  type    = string
  default = "http://localhost:3000/payment/callback"
}

variable "payment_webhook_secret" {
  type      = string
  default   = "change-me"
  sensitive = true
}

variable "cors_allowed_origins" {
  type    = string
  default = "*"
}

variable "payment_provider" {
  type    = string
  default = "mock"
}

variable "payment_currency" {
  type    = string
  default = "GHS"
}

variable "rate_limit_capacity" {
  type    = number
  default = 60
}

variable "rate_limit_refill" {
  type    = number
  default = 60
}

variable "tags" {
  type    = map(string)
  default = {}
}
