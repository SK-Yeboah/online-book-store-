variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "enable_https" {
  type        = bool
  description = "Provision ACM cert, HTTPS:443 listener, HTTP->HTTPS redirect, and Route53 alias"
  default     = false
}

variable "domain_name" {
  type        = string
  description = "FQDN for the API (e.g. api.example.com). Required when enable_https=true."
  default     = ""

  validation {
    condition     = !var.enable_https || length(var.domain_name) > 0
    error_message = "domain_name is required when enable_https is true."
  }
}

variable "route53_zone_id" {
  type        = string
  description = "Route53 hosted zone ID that can create records for domain_name. Required when enable_https=true."
  default     = ""

  validation {
    condition     = !var.enable_https || length(var.route53_zone_id) > 0
    error_message = "route53_zone_id is required when enable_https is true."
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}
