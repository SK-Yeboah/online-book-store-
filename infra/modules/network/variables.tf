variable "name_prefix" {
  type        = string
  description = "Prefix for resource names (e.g. bookstore)"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "tags" {
  type    = map(string)
  default = {}
}
