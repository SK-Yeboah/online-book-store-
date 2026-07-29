variable "name_prefix" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "db_security_group_id" {
  type = string
}

variable "redis_security_group_id" {
  type = string
}

variable "db_name" {
  type    = string
  default = "online_book_store"
}

variable "db_username" {
  type    = string
  default = "admin"
}

variable "db_engine_version" {
  type    = string
  default = "8.0"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_max_allocated_storage" {
  type    = number
  default = 50
}

variable "tags" {
  type    = map(string)
  default = {}
}
