# Tillora Backend — AI Development Specification

## 1. Project Overview

Tillora is a simple, web-based business management and pre-accounting application designed primarily for small businesses, local merchants, and small-scale commercial operations.

The product must remain intentionally simpler than traditional ERP products such as Logo, SAP, or Netsis. It is not intended to become a large enterprise ERP.

The primary goals are:

- Easy to use
- Accessible from anywhere through a web interface
- Fast and responsive
- Secure
- Financially consistent
- Modular and maintainable
- Suitable for small businesses
- Ready for future integrations without unnecessary complexity

The initial version will provide:

- Authentication and authorization
- Company management
- User and permission management
- Chart of accounts
- Current account/customer/supplier management
- Cash and bank accounts
- Voucher management
- Invoice management
- Product catalog management
- Reporting
- Dashboard summaries
- Audit logging

The frontend will be developed later. The first phase is backend-only.

---

## 2. Core Architecture

The backend must be implemented as a **modular monolith**.

Microservices must not be used.

The codebase must be divided into independent business modules with explicit boundaries. Modules must not bypass each other's public APIs or access another module's internal implementation directly.

Recommended root package:

```text
com.yigitcicekci.tillora
```

Recommended module structure:

```text
com.yigitcicekci.tillora
├── TilloraApplication.java
├── auth
├── company
├── user
├── currentaccount
├── chartofaccount
├── finance
├── voucher
├── invoice
├── product
├── dashboard
├── reporting
├── audit
└── shared
```

Each module should follow a layered internal structure:

```text
module-name
├── api
│   ├── controller
│   ├── request
│   └── response
├── application
│   ├── service
│   ├── command
│   └── query
├── domain
│   ├── entity
│   ├── valueobject
│   ├── enum
│   ├── event
│   └── repository
└── infrastructure
    ├── persistence
    ├── client
    ├── configuration
    └── mapper
```

Module rules:

- A module may expose public use cases through its `api` or clearly defined application-facing interfaces.
- A module must not access another module's JPA repository directly.
- A module must not import another module's internal entity unless explicitly exposed as part of a public contract.
- Cross-module operations should use application services, module APIs, domain events, or explicit interfaces.
- Shared code must be minimal.
- The `shared` module must not become a dumping ground.
- Do not create cyclic module dependencies.
- Use Spring Modulith to validate module boundaries.
- Add architecture tests where useful.

---

## 3. Required Technology Stack

Use the following technologies:

```text
Java 25 LTS
Spring Boot 4.x
Maven
Spring MVC
Spring Security
Spring Data JPA
Hibernate
Spring Modulith
PostgreSQL
Redis
Flyway
Spring WebClient
Resilience4j
MapStruct
Bean Validation
Springdoc OpenAPI
Spring Boot Actuator
Micrometer
Prometheus
Grafana
JUnit 5
Mockito
AssertJ
Testcontainers
Docker
Docker Compose
MinIO or an S3-compatible object store
```

Optional supporting technologies:

```text
ShedLock
ArchUnit
Awaitility
Logback JSON encoder
```

Do not add the following unless there is a proven requirement:

```text
Kafka
RabbitMQ
Elasticsearch
Kubernetes
Full reactive WebFlux architecture
Microservices
GraphQL
CQRS infrastructure frameworks
Event sourcing
```

Spring MVC should be the primary web stack.

`WebClient` may be used for external HTTP integrations such as transactional email providers.

JPA is blocking, so do not introduce a fully reactive architecture around it.

---

## 4. AI Coding Rules

These rules are mandatory for all generated code.

### 4.1 Code Style

- Write the shortest code that remains readable, safe, and maintainable.
- Prefer simple solutions over abstract or over-engineered solutions.
- Do not create unnecessary classes, interfaces, wrappers, factories, or layers.
- Do not generate dead code.
- Do not generate placeholder code unless explicitly requested.
- Do not add comments to the code.
- Do not add JavaDoc.
- Do not add TODO comments.
- Use meaningful class, method, and variable names.
- Avoid duplicated logic.
- Avoid magic strings and magic numbers.
- Prefer Java records for immutable request and response DTOs.
- Do not use Lombok `@Data` on JPA entities.
- Use constructor injection.
- Prefer `final` fields where possible.
- Use `BigDecimal` for monetary and quantity calculations.
- Never use `double` or `float` for money.
- Use `UUID` for internal primary keys.
- Use separate human-readable business numbers where needed.
- Keep controller methods thin.
- Keep business logic in application/domain services.
- Do not return JPA entities directly from controllers.
- Do not expose internal database IDs unnecessarily.
- Do not silently catch exceptions.
- Do not use generic `RuntimeException` for business errors.
- Do not use field injection.
- Do not use static mutable state.

### 4.2 Performance

Every query and data access implementation must be checked for:

- N+1 query problems
- Unnecessary joins
- Unbounded result sets
- Missing pagination
- Missing indexes
- Excessive object loading
- Repeated database reads
- Incorrect eager loading
- Slow aggregate queries
- Large transaction scopes

Use:

- Explicit fetch joins where appropriate
- Entity graphs where appropriate
- DTO projections for read-heavy endpoints
- Pagination for list endpoints
- Batch operations where appropriate
- PostgreSQL indexes for frequently filtered and joined columns
- Database constraints instead of application-only validation
- Redis cache only for data that can be rebuilt from PostgreSQL

Never solve N+1 problems by changing every relationship to `EAGER`.

Default JPA relationship loading should be `LAZY`.

### 4.3 Concurrency and Race Conditions

Every write operation must be reviewed for:

- Race conditions
- Lost updates
- Duplicate records
- Double submissions
- Concurrent sequence generation
- Double invoice creation
- Double voucher approval
- Concurrent balance updates

Use the correct protection depending on the use case:

- PostgreSQL unique constraints
- Optimistic locking using `@Version`
- Pessimistic locking for critical counters
- Atomic PostgreSQL operations
- Idempotency keys
- Redis distributed locks only as an additional layer
- Transactional boundaries
- Retry only when safe
- Database-backed uniqueness as the final authority

Redis locks must never be the only protection against duplicate financial operations.

### 4.4 Transactions

Financially related operations must be atomic.

Use `@Transactional` for use cases such as:

- Voucher approval
- Invoice creation
- Invoice approval
- Current account movement creation
- Automatic account code generation
- Reversal operations
- Cancellation workflows

Transaction boundaries must be placed at the application service use-case level.

Do not keep transactions open while waiting for slow external HTTP requests unless there is no safer alternative.

For external integrations:

1. Persist the internal operation state.
2. Commit.
3. Call the external system.
4. Persist the result in a new transaction.
5. Use idempotency and reconciliation.

---

## 5. Data Storage Strategy

### 5.1 PostgreSQL

PostgreSQL is the single source of truth.

The following must always be stored in PostgreSQL:

- Companies
- Users
- Roles
- Permissions
- Sessions or refresh-token metadata where persistence is needed
- Current accounts
- Customers
- Suppliers
- Chart of accounts
- Account code sequences
- Cash accounts
- Bank accounts
- Vouchers
- Voucher lines
- Invoices
- Invoice lines
- Products
- Current account movements
- Cash and bank movements
- Audit logs
- Approval history
- Cancellation and reversal records

Financial values must never exist only in Redis.

### 5.2 Redis

Redis is a supporting technology, not the primary financial database.

Redis may be used for:

- User sessions
- Refresh token tracking
- Permission cache
- Company settings cache
- Chart of accounts cache
- Selectable account cache
- Dashboard summary cache
- Rate limiting
- Distributed locks
- Short-lived idempotency markers
- Frequently read reference data
- Request throttling
- Short-lived workflow state

Example Redis keys:

```text
session:{sessionId}
refresh-token:{tokenId}
permission:user:{userId}
chart-of-accounts:{companyId}
selectable-accounts:{companyId}:{voucherType}
dashboard:summary:{companyId}:{period}
rate-limit:login:{ip}
```

Redis rules:

- Every cache must have a clear invalidation strategy.
- Every cache value must be reconstructible from PostgreSQL.
- Use TTL where appropriate.
- Never store passwords in Redis.
- Never log Redis values containing tokens.
- Do not store official invoice records only in Redis.
- Do not use Redis counters as the only source for accounting sequence generation.
- Do not use Redis as the final source of current account balance, cash balance, or invoice state.

---

## 6. Database Migration Rules

Use Flyway for all schema changes.

Production configuration:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Do not use Hibernate schema generation in production.

Migration directory:

```text
src/main/resources/db/migration
```

Example migrations:

```text
V1__create_companies.sql
V2__create_users_roles_permissions.sql
V3__create_chart_of_accounts.sql
V4__create_current_accounts.sql
V5__create_cash_and_bank_accounts.sql
V6__create_vouchers.sql
V7__create_products.sql
V8__create_invoices.sql
V9__create_audit_logs.sql
V10__create_audit_logs.sql
```

Migration rules:

- Add required indexes.
- Add foreign keys.
- Add check constraints where possible.
- Add unique constraints for business invariants.
- Avoid nullable columns unless the domain genuinely allows null.
- Use database defaults carefully.
- Never rely only on application-side validation.

---

## 7. Multi-Company Design

Even if the initial installation is for one company, the database model must support multiple companies.

Every business-owned record must include `company_id`.

Examples:

- Users
- Current accounts
- Chart of accounts
- Vouchers
- Invoices
- Products
- Cash accounts
- Bank accounts

All queries must be company-scoped.

A user must never access another company's data.

Do not accept `companyId` from the frontend for every request.

Resolve the active company from the authenticated session or security context.

Database uniqueness must usually be company-scoped.

Examples:

```text
UNIQUE(company_id, username)
UNIQUE(company_id, account_code)
UNIQUE(company_id, voucher_number)
UNIQUE(company_id, invoice_number)
UNIQUE(company_id, ettn)
```

---

## 8. Authentication and Authorization

### 8.1 Authentication Flow

Users log in through the web application using:

- Username
- Password

Public registration is not required.

Users are created by an administrator.

Recommended flow:

```text
Admin creates user
→ User receives temporary password
→ User logs in
→ User changes password on first login
→ Session is created
```

Required auth endpoints:

```http
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/change-password
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
GET  /api/v1/auth/me
```

Recommended auth model:

```text
Short-lived access token
+
Redis-backed refresh token or session
+
HttpOnly Secure cookies
```

Do not store long-lived authentication tokens in browser `localStorage`.

### 8.2 User Fields

Initial user fields:

```text
id
company_id
username
email
password_hash
first_name
last_name
phone
status
last_login_at
password_changed_at
must_change_password
created_at
updated_at
version
```

User status:

```java
ACTIVE
PASSIVE
LOCKED
```

### 8.3 Roles

Initial roles:

```java
ADMIN
ACCOUNTING
SALES
VIEWER
```

### 8.4 Permissions

Roles and permissions must be separate.

Example permissions:

```text
USER_READ
USER_CREATE
USER_UPDATE
USER_DISABLE

CURRENT_ACCOUNT_READ
CURRENT_ACCOUNT_CREATE
CURRENT_ACCOUNT_UPDATE

CHART_OF_ACCOUNT_READ
CHART_OF_ACCOUNT_MANAGE

VOUCHER_READ
VOUCHER_CREATE
VOUCHER_APPROVE
VOUCHER_CANCEL

INVOICE_READ
INVOICE_CREATE
INVOICE_APPROVE
INVOICE_CANCEL

PRODUCT_READ
PRODUCT_MANAGE



REPORT_VIEW
AUDIT_VIEW
```

Use method authorization:

```java
@PreAuthorize("hasAuthority('VOUCHER_CREATE')")
```

---

## 9. Company Module

Company fields:

```text
id
name
legal_name
tax_number
tax_office
address
phone
email
currency
timezone
status
created_at
updated_at
version
```

Company creation should initialize:

- Default chart of accounts
- Account code sequences
- Default currency
- Default financial settings
- Default permissions if needed

---

## 10. Chart of Accounts

Users must not manually enter accounting codes.

The user selects a human-readable account type or business entity. The backend resolves the correct account code.

Core default accounts:

```text
100 — Cash
101 — Received Cheques
102 — Banks
103 — Issued Cheques and Payment Orders
120 — Receivables
121 — Notes Receivable
153 — Trade Goods
159 — Order Advances Given
191 — Deductible VAT
320 — Payables
321 — Notes Payable
340 — Order Advances Received
391 — Calculated VAT
600 — Domestic Sales
610 — Sales Returns
621 — Cost of Goods Sold
649 — Other Ordinary Income and Profits
659 — Other Ordinary Expenses and Losses
```

Initial required accounts:

```text
100 — Cash
102 — Banks
120 — Receivables
153 — Trade Goods
191 — Deductible VAT
320 — Payables
391 — Calculated VAT
600 — Domestic Sales
610 — Sales Returns
621 — Cost of Goods Sold
```

Suggested table:

```text
chart_of_accounts
- id
- company_id
- code
- name
- parent_id
- level
- category
- nature
- posting_allowed
- system_key
- active
- created_at
- updated_at
- version
```

Example hierarchy:

```text
120                  Receivables
120.01               Retail Receivables
120.01.000001        Customer A
120.02               Wholesale Receivables
120.02.000001        Customer B
```

Only leaf accounts with `posting_allowed = true` may be used in voucher lines.

System account keys:

```java
CASH
BANKS
RECEIVABLES
TRADE_GOODS
DEDUCTIBLE_VAT
PAYABLES
CALCULATED_VAT
DOMESTIC_SALES
SALES_RETURNS
COST_OF_GOODS_SOLD
```

Business logic must use `system_key`, not hard-coded accounting strings.

---

## 11. Current Accounts

Use the term `CurrentAccount` or `BusinessPartner` instead of only `Customer`, because the same company may be both a customer and a supplier.

Fields:

```text
id
company_id
name
legal_name
tax_number
tax_office
identity_number
phone
email
address
trade_type
relationship_type
status
created_at
updated_at
version
```

Trade type:

```java
RETAIL
WHOLESALE
```

Relationship type:

```java
CUSTOMER
SUPPLIER
BOTH
```

Ledger roles:

```java
RECEIVABLE
PAYABLE
```

A current account may have:

```text
120.xx.xxxxxx account for receivables
320.xx.xxxxxx account for payables
```

Suggested table:

```text
current_account_ledger_accounts
- id
- company_id
- current_account_id
- role
- chart_of_account_id
- main_account_code
- group_code
- sequence_number
- full_account_code
- created_at
```

---

## 12. Automatic Account Code Generation

Users select human-readable values:

```text
Relationship: Customer / Supplier / Both
Trade Type: Retail / Wholesale
```

The backend assigns codes automatically.

Mapping:

```text
Customer + Retail     → 120.01.xxxxxx
Customer + Wholesale  → 120.02.xxxxxx
Supplier + Retail     → 320.01.xxxxxx
Supplier + Wholesale  → 320.02.xxxxxx
```

Example:

```text
120.01.000001
120.01.000002
120.02.000001
320.01.000001
320.02.000001
```

Use a dedicated sequence table:

```text
account_code_sequences
- id
- company_id
- main_account_code
- group_code
- current_value
- version
```

Unique constraint:

```text
UNIQUE(company_id, main_account_code, group_code)
```

Sequence generation must be concurrency-safe.

Use one of:

- Pessimistic row lock
- PostgreSQL sequence
- Atomic SQL update with returning
- Optimistic locking with retry

Do not use `MAX(sequence_number) + 1` without locking.

Once an account has financial movements, its account code must not be changed.

Accounts must not be deleted after use. They must be made inactive.

An old account code must never be assigned to another current account.

---

## 13. Cash and Bank Accounts

### 13.1 Cash Accounts

When a user creates a cash account:

```text
Cash Name: Main Cash
```

The backend creates:

```text
100.01 — Main Cash
100.02 — Branch Cash
```

Suggested fields:

```text
id
company_id
name
chart_of_account_id
currency
active
created_at
updated_at
version
```

### 13.2 Bank Accounts

When a user creates a bank account:

```text
Bank Name
Branch
IBAN
Account Number
Currency
```

The backend creates:

```text
102.01 — Bank A
102.02 — Bank B
```

Do not expose raw account code editing to normal users.

---

## 14. Voucher Module

Voucher types:

```java
COLLECTION
PAYMENT
OFFSET
```

Turkish labels:

```text
COLLECTION → Tahsil
PAYMENT    → Tediye
OFFSET     → Mahsup
```

Voucher header fields:

```text
id
company_id
voucher_number
voucher_type
voucher_date
movement_note
document_number
currency
exchange_rate
status
created_by
approved_by
created_at
updated_at
approved_at
cancelled_at
version
```

Voucher line fields:

```text
id
voucher_id
line_number
chart_of_account_id
current_account_id
movement_note
debit
credit
quantity
due_date
currency_amount
created_at
```

Business rules:

- Voucher UUID is internal.
- Voucher number is human-readable.
- Voucher date defaults to today but can be changed.
- Debit and credit must use `BigDecimal`.
- A voucher line cannot contain positive debit and positive credit simultaneously.
- Approved vouchers must be balanced.
- Total debit must equal total credit.
- Approved vouchers must not be edited directly.
- Use cancellation or reversal records.
- Voucher deletion is forbidden after approval.
- Draft vouchers may be edited.
- Every approval, cancellation, and reversal must be audited.

Voucher number examples:

```text
THS-2026-000001
TDI-2026-000001
MHS-2026-000001
```

### 14.1 Collection Voucher

User selects:

```text
Customer
Cash or Bank Account
Amount
Date
Movement Note
```

System generates:

```text
Cash/Bank Account → Debit
Customer Receivable Account → Credit
```

Example:

```text
100.01 Main Cash                 Debit  10,000
120.01.000001 Customer A         Credit 10,000
```

### 14.2 Payment Voucher

User selects:

```text
Supplier
Cash or Bank Account
Amount
Date
Movement Note
```

System generates:

```text
Supplier Payable Account → Debit
Cash/Bank Account → Credit
```

Example:

```text
320.02.000001 Supplier A         Debit  8,000
102.01 Bank A                    Credit 8,000
```

### 14.3 Offset Voucher

Offset vouchers allow multiple manually selected posting accounts.

Rule:

```text
Total Debit = Total Credit
```

All selected accounts must have `posting_allowed = true`.

---

## 15. Invoice Module

Initial invoice types:

```java
SALES
PURCHASE
```

Invoice statuses:

```java
DRAFT
APPROVED
CANCELLED
```

Invoice fields:

```text
id
company_id
invoice_number
invoice_type
current_account_id
invoice_date
due_date
currency
exchange_rate
subtotal
discount_total
tax_total
grand_total
status
created_by
approved_by
created_at
updated_at
approved_at
cancelled_at
version
```

Invoice line fields:

```text
id
invoice_id
line_number
product_id
description
quantity
unit
unit_price
discount_rate
discount_amount
vat_rate
vat_amount
line_total
created_at
```

All totals must be recalculated on the backend.

Never trust frontend-calculated totals.

### 15.1 Sales Invoice Flow

When approved:

```text
Create invoice
→ Create invoice lines
→ Create customer receivable movement
→ Create sales posting
→ Create calculated VAT posting
→ Create cost-of-goods-sold posting
→ Create audit record
→ Commit atomically
```

Typical accounts:

```text
120 — Receivables
600 — Domestic Sales
391 — Calculated VAT
153 — Trade Goods
621 — Cost of Goods Sold
```

### 15.2 Purchase Invoice Flow

When approved:

```text
Create invoice
→ Create invoice lines
→ Create supplier payable movement
→ Create trade goods posting
→ Create deductible VAT posting
→ Create audit record
→ Commit atomically
```

Typical accounts:

```text
320 — Payables
153 — Trade Goods
191 — Deductible VAT
```

---

## 16. Product Module

Product fields:

```text
id
company_id
code
barcode
name
unit
purchase_price
sale_price
vat_rate
active
created_at
updated_at
version
```

Products are catalog records used by invoice lines. The backend does not manage inventory quantities, warehouses, stock movements, stock balances, stock transfers, adjustments, minimum stock levels, or negative-stock policies.

---

## 17. Electronic Tax Documents

E-Archive, E-Invoice, GİB, UBL-TR, and electronic-document provider integrations are intentionally excluded from the open-source Tillora core.

Invoice workflows remain local sales and purchase invoice workflows backed by PostgreSQL. Downstream deployments may add tax-document integrations in a separate module without introducing those providers into the core invoice, dashboard, reporting, or audit boundaries.

## 18. File Storage

Use MinIO or an S3-compatible object store for optional report exports and other non-financial files.

Do not store large files directly in PostgreSQL unless explicitly required.

Object key example:

```text
companies/{companyId}/reports/{year}/{month}/report.csv
```

Store metadata in PostgreSQL:

```text
object_key
content_type
checksum
size
created_at
```

## 19. Dashboard

Dashboard goals:

- Show urgent actions
- Show financial summary
- Avoid information overload

Initial dashboard cards:

```text
Total Receivables
Overdue Receivables
Total Payables
Cash Balance
Bank Balance
Monthly Sales
Monthly Purchases
Collections
Payments
```

Alerts:

```text
Overdue collections
Upcoming payments
```

Recommended endpoint:

```http
GET /api/v1/dashboard/summary?period=2026-07
```

Dashboard queries should use DTO projections and optimized aggregate queries.

Dashboard results may be cached in Redis for 30–120 seconds.

Cache keys must be invalidated after relevant write operations or allowed to expire quickly.

---

## 20. Reporting

Initial reports:

- Current account statement
- Customer receivables
- Supplier payables
- Voucher list
- Cash movements
- Bank movements
- Sales report
- Purchase report
- Audit report

Reports must support:

- Date filtering
- Pagination
- Sorting
- Company scoping
- Export preparation

Avoid loading entire reports into memory.

Use streaming or paginated export for large datasets.

---

## 21. Audit Logging

Every important operation must be audited.

Audit events:

- Login success
- Login failure
- Password change
- User creation
- Role or permission change
- Current account creation
- Account code generation
- Voucher creation
- Voucher approval
- Voucher cancellation
- Voucher reversal
- Invoice creation
- Invoice approval
- Invoice cancellation
- Settings change

Suggested fields:

```text
id
company_id
user_id
action
entity_type
entity_id
correlation_id
ip_address
user_agent
before_data
after_data
created_at
```

Sensitive values must never be included.

Audit logs must be append-only.

---

## 22. Error Handling

Use a standard error format:

```json
{
  "code": "VOUCHER_NOT_BALANCED",
  "message": "Total debit and credit must be equal.",
  "path": "/api/v1/vouchers",
  "timestamp": "2026-07-14T15:30:00+03:00",
  "correlationId": "..."
}
```

Create business-specific exceptions.

Examples:

```text
CURRENT_ACCOUNT_NOT_FOUND
ACCOUNT_CODE_ALREADY_EXISTS
ACCOUNT_CODE_SEQUENCE_CONFLICT
VOUCHER_NOT_BALANCED
VOUCHER_ALREADY_APPROVED
INVOICE_ALREADY_APPROVED
```

Do not expose stack traces to clients.

---

## 23. External HTTP Integration

Use `WebClient` for external APIs.

Configure:

- Connection timeout
- Read timeout
- Response size limit
- Correlation ID
- Safe retry policy
- Circuit breaker
- Rate limiter
- Bulkhead where appropriate

Use Resilience4j.

Retry rules:

- Retry only idempotent operations automatically.
- Do not blindly retry financial document creation.
- Reconcile before retrying timed-out create requests.
- Do not retry authentication failures without limits.
- Do not retry validation errors.

---

## 24. Background Jobs

Initial background jobs may use Spring Scheduler.

Use ShedLock with PostgreSQL if multiple instances may run.

Possible jobs:

- Retry safe failed operations
- Expire old sessions
- Clear obsolete temporary states
- Refresh dashboard cache
- Prepare scheduled reports
- Verify object storage files
- Clean orphan temporary files

Do not use Redis lock as the only scheduler coordination mechanism.

---

## 25. API Standards

Base path:

```text
/api/v1
```

Examples:

```text
/api/v1/auth
/api/v1/users
/api/v1/companies
/api/v1/current-accounts
/api/v1/chart-of-accounts
/api/v1/cash-accounts
/api/v1/bank-accounts
/api/v1/vouchers
/api/v1/invoices
/api/v1/products
/api/v1/dashboard
/api/v1/reports
/api/v1/audit
```

Rules:

- Use nouns in endpoint paths.
- Use HTTP methods correctly.
- Use pagination for list endpoints.
- Use validation annotations.
- Return consistent response objects.
- Do not expose entity graphs.
- Use ISO-8601 dates.
- Use timezone-aware timestamps.
- Default business timezone is `Europe/Istanbul`.
- Use UTC for stored timestamps where practical.
- Use the company's timezone when presenting dates.

---

## 26. Testing Strategy

Required technologies:

```text
JUnit 5
Mockito
AssertJ
Testcontainers
Spring Modulith Test
ArchUnit
```

Use Testcontainers for:

- PostgreSQL
- Redis

Important test scenarios:

```text
Two current accounts created concurrently must not receive the same account code.
A voucher with unequal debit and credit must not be approved.
An approved voucher must not be edited.
A cancelled voucher must preserve its original record.
A failed invoice transaction must roll back account movements.
A user from one company must not access another company's data.
Permission changes must invalidate Redis permission cache.
Dashboard cache must not become the financial source of truth.
```

Use integration tests for financial workflows.

Do not rely only on unit tests for transactional behavior.

---

## 27. Observability

Use:

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
Structured JSON logging
Correlation IDs
```

Health endpoints:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Readiness checks may include:

- PostgreSQL
- Redis
- MinIO
- Migration status


Track metrics such as:

- Login failures
- Voucher creation time
- Invoice approval time
- Redis cache hit rate
- Database query latency
- Slow query count
- Duplicate prevention count

Never log:

- User passwords
- Access tokens
- Refresh tokens
- SMS codes
- Full VKN/TCKN
- Full invoice XML
- Sensitive personal data

---

## 28. Security Requirements

- Use BCrypt or Argon2 for user passwords.
- Use secure cookies.
- Enable CSRF protection for cookie-based auth.
- Validate all input.
- Mask tax numbers in logs and UI responses where full value is unnecessary.
- Use least-privilege database credentials.
- Do not expose Actuator endpoints publicly.
- Use TLS in production.
- Restrict object storage access.
- Use signed download URLs where appropriate.
- Apply company-level authorization to every business query.
- Perform authorization checks in the backend, not only the frontend.
- Validate uploaded/generated file types and sizes.
- Maintain an append-only audit trail.

---

## 29. Initial Docker Compose Services

The local development environment should include:

```text
tillora-api
postgres
redis
```

Optional:

```text
minio (storage profile)
prometheus (monitoring profile)
grafana (monitoring profile)
mailpit
```

Use environment variables for configuration.

Do not commit secrets.

Provide:

```text
.env.example
docker-compose.yml
application.yml
application-local.yml
application-test.yml
```

---

## 30. Suggested Initial Implementation Order

Build the project in this order:

```text
1. Spring Boot project skeleton
2. Docker Compose
3. PostgreSQL connection
4. Redis connection
5. Flyway configuration
6. Spring Modulith boundaries
7. Shared error handling
8. Security foundation
9. Company module
10. User, role, and permission modules
11. Authentication and session management
12. Default chart of accounts
13. Account code sequence generator
14. Current account module
15. Cash account module
16. Bank account module
17. Voucher module
18. Product module
19. Invoice module
20. Dashboard queries
21. Reporting
22. Audit improvements
23. Observability and production hardening
```

---

## 31. First Infrastructure Deliverable

The first generated backend foundation should include:

- Maven project
- Java 25
- Spring Boot 4.x
- Modular monolith package structure
- Spring Modulith dependency
- PostgreSQL configuration
- Redis configuration
- Flyway configuration
- Docker Compose
- Base security configuration
- Standard error response
- Correlation ID filter
- Actuator
- OpenAPI
- Testcontainers setup
- Initial company module
- Initial user module
- Initial auth module
- Default chart-of-accounts migration
- Account sequence migration
- Architecture verification test
- PostgreSQL and Redis integration tests

Do not implement the full business domain in one large generation.

Generate the project incrementally and keep every step compilable and testable.

---

## 32. Final AI Instruction

When generating code for Tillora:

- Respect the modular monolith boundaries.
- Keep code concise.
- Do not add comments.
- Do not over-engineer.
- Protect financial consistency.
- Use PostgreSQL as the source of truth.
- Use Redis only for cache, session, lock, rate limit, and temporary state.
- Keep electronic tax-document integrations outside the open-source core.
- Prevent N+1 queries.
- Prevent race conditions.
- Prevent duplicate financial operations.
- Use transactions correctly.
- Use database constraints.
- Use idempotency.
- Keep list endpoints paginated.
- Keep external integrations isolated.
- Keep all company data scoped.
- Keep every generated step compilable.
- Add tests for critical concurrency and financial rules.
- Never sacrifice correctness for brevity.
- Never write queries in services. Write queries in repository.
- If you change anything in a endpoint, update POSTMAN collection.
