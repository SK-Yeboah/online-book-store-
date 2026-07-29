variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  type    = string
  default = "bookstore"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "db_name" {
  type    = string
  default = "online_book_store"
}

variable "db_username" {
  type    = string
  default = "admin"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "container_image" {
  type        = string
  description = "Initial image; CI will roll forward after first deploy"
  default     = "ghcr.io/sk-yeboah/online-book-store:latest"
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
  type        = string
  description = "GHCR username for private image pulls (leave empty if image is public)"
  default     = ""
}

variable "ghcr_token" {
  type        = string
  description = "GitHub PAT with read:packages"
  default     = ""
  sensitive   = true
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
  default   = "staging-webhook-secret"
  sensitive = true
}

variable "cors_allowed_origins" {
  type    = string
  default = "*"
}

variable "payment_provider" {
  type        = string
  description = "mock for lab smoke; paystack for real test payments"
  default     = "mock"
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
