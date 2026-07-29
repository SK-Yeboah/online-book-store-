resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnet-group"
  subnet_ids = var.data_subnet_ids

  tags = merge(var.tags, { Name = "${var.name_prefix}-db-subnet-group" })
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-db"
  engine         = "mysql"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp2"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username

  # AWS-managed master password in Secrets Manager (rds!db-*)
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.db_security_group_id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true

  tags = merge(var.tags, { Name = "${var.name_prefix}-db" })
}

resource "aws_elasticache_serverless_cache" "this" {
  engine = "redis"
  name   = "${var.name_prefix}-redis"

  security_group_ids = [var.redis_security_group_id]
  subnet_ids         = var.data_subnet_ids

  tags = merge(var.tags, { Name = "${var.name_prefix}-redis" })
}
