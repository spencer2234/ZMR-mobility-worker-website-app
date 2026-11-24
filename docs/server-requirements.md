# Server requirements (proposal)

For ZMR to review. Today the app has no server: each phone keeps its own data and sends Excel
files by WhatsApp or email. A server would replace that hand-off and keep ID documents out of
chat apps. The sizes below are starting estimates for a small fleet; confirm them with the
number of cities, vehicles and users ZMR expects.

## What the server must do

- Real logins for employees, managers and upper management, with access limited by role and city.
- One shared record of customers, vehicles, payments and weekly report status.
- Secure storage for customer documents, with access logs.
- Payments that can be added but never edited or deleted, as in the app today.
- Offline support: phones keep working without signal and sync when they reconnect.
- Automatic daily backups, with a tested restore.

## Suggested setup

| Part | Starting point |
|---|---|
| Hosting region | India (Mumbai or Hyderabad) |
| Application server | 2 vCPU, 4 GB RAM |
| Database | Managed PostgreSQL, 2 vCPU, 4 GB RAM, 50 GB, daily backups |
| Document storage | Private encrypted object storage, about 20 MB per customer |
| Security | HTTPS only, encryption at rest, role-based access, audit log |
| Alternative | Firebase Auth + Firestore + Storage (India region): faster to build, less control |

## Legal points to confirm before launch

- **Personal data:** the Digital Personal Data Protection Act applies to customers' IDs and
  addresses. ZMR needs consent wording, a retention period and a deletion process.
- **Aadhaar:** official checks go through UIDAI's Offline Verification Seeking Entity (OVSE)
  registration. UIDAI says verifiers should generally not store the Aadhaar number afterward.
  The app asks for masked copies only and does not verify Aadhaar.
- **Sahamati:** it runs the Account Aggregator network for financial data. Confirm what check
  ZMR intended, because it does not appear to be an Aadhaar verification service.

## Decisions needed from ZMR

1. Who owns and pays for the hosting account?
2. Custom server, or Firebase?
3. How long should customer documents be kept after a rental ends?
4. Expected number of cities, vehicles and app users in the next 12 months?
