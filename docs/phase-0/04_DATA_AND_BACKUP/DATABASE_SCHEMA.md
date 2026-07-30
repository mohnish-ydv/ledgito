# Database Schema

## Storage rules

- SQLite through Room.
- All money stored as signed 64-bit integer minor units plus ISO 4217 currency code.
- Timestamps stored as UTC epoch milliseconds; user-facing local date stored where period semantics require it.
- UUID text identifiers generated locally.
- Soft deletion only where needed for sync-ready audit history; ordinary v1 deletion uses undo window then purge.
- Foreign keys enabled.

## Tables

### profiles
`id`, `name`, `base_currency`, `locale_tag`, `created_at`, `updated_at`

### accounts
`id`, `profile_id`, `name`, `type`, `currency_code`, `opening_balance_minor`, `opening_date`, `include_in_total`, `is_archived`, `sort_order`, `created_at`, `updated_at`

### categories
`id`, `profile_id`, `parent_id`, `name`, `kind`, `icon_key`, `colour_argb`, `is_system`, `is_archived`, `sort_order`

### merchants
`id`, `profile_id`, `name`, `normalised_name`, `last_used_at`

### transactions
`id`, `profile_id`, `type`, `account_id`, `to_account_id`, `category_id`, `merchant_id`, `amount_minor`, `received_amount_minor`, `currency_code`, `transaction_time_utc`, `local_date`, `status`, `note`, `recurrence_rule_id`, `recurrence_occurrence_key`, `created_at`, `updated_at`, `deleted_at`

Constraints:
- Expense/income requires `account_id`.
- Transfer requires distinct `account_id` and `to_account_id`.
- `recurrence_occurrence_key` unique when non-null.

### transaction_splits
`id`, `transaction_id`, `category_id`, `amount_minor`, `note`

Constraint: split sum equals parent absolute amount before save.

### tags
`id`, `profile_id`, `name`, `normalised_name`, `colour_argb`

### transaction_tags
`transaction_id`, `tag_id`

### attachments
`id`, `profile_id`, `transaction_id`, `display_name`, `mime_type`, `vault_relative_path`, `size_bytes`, `sha256`, `created_at`

### budget_definitions
`id`, `profile_id`, `name`, `amount_minor`, `currency_code`, `scope_type`, `period_type`, `start_local_date`, `interval_count`, `carry_mode`, `alert_percent`, `effective_from`, `effective_until`, `is_paused`

### budget_scope_categories
`budget_definition_id`, `category_id`

### budget_periods
`id`, `budget_definition_id`, `period_start`, `period_end`, `limit_minor`, `carry_in_minor`, `closed_at`

Unique: `(budget_definition_id, period_start, period_end)`.

### recurrence_rules
`id`, `profile_id`, `template_transaction_json`, `frequency`, `interval_count`, `days_of_week`, `day_of_month`, `invalid_day_policy`, `start_local_date`, `end_local_date`, `max_occurrences`, `posting_mode`, `next_run_local_date`, `is_paused`, `created_at`, `updated_at`

### saved_filters
`id`, `profile_id`, `name`, `filter_json`, `sort_order`

### backup_history
`id`, `profile_id`, `created_at`, `destination_uri`, `package_name`, `size_bytes`, `sha256`, `status`, `record_counts_json`, `error_code`

### import_jobs
`id`, `profile_id`, `source_uri`, `format`, `started_at`, `completed_at`, `status`, `summary_json`, `error_log_path`

### audit_events
`id`, `profile_id`, `entity_type`, `entity_id`, `action`, `timestamp`, `metadata_json`

## Required indexes

- Transactions by `(profile_id, local_date DESC)`
- Transactions by `(account_id, local_date DESC)`
- Transactions by `(category_id, local_date DESC)`
- Transactions by `merchant_id`
- Transactions by recurrence occurrence key
- Budget periods by definition and start date
- Attachments by transaction
- Normalised merchant and tag names

## Migration policy

- Every schema version has an explicit migration.
- No destructive fallback in production.
- Pre-migration backup attempted when possible.
- Migration tests start from every released schema version.
