# API Reference

This is a code-verified overview of the backend controller surface. It is not a generated API schema.

## Public Read API

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/public/hair-salon` | Current salon profile |
| `GET` | `/api/v1/public/employees` | Public employee list |
| `GET` | `/api/v1/public/employees/{id}` | Single public employee |
| `GET` | `/api/v1/public/treatments` | Public treatment list |
| `GET` | `/api/v1/public/treatments/{id}` | Single public treatment |
| `GET` | `/api/v1/public/availability` | Available booking slots |
| `GET` | `/api/v1/public/booking-checkout-config` | Stripe publishable key and checkout currency |

## Public Booking Lifecycle API

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/public/bookings/{id}` | Fetch a public booking summary |
| `POST` | `/api/v1/public/bookings` | Create a public booking |
| `POST` | `/api/v1/public/bookings/hold` | Hold a public slot before checkout |
| `POST` | `/api/v1/public/bookings/{id}/checkout/validate` | Validate checkout details for a held booking |
| `POST` | `/api/v1/public/bookings/{id}/checkout` | Prepare/authorize checkout |
| `POST` | `/api/v1/public/bookings/{id}/confirm` | Confirm a held booking after payment |
| `DELETE` | `/api/v1/public/bookings/{id}` | Cancel a public booking/hold |

Sensitive public booking lifecycle operations are publicly reachable by design. The backend protects them with hold ownership tokens, payment validation, lifecycle validation, and rate limiting in the service/security layer.

## Authentication and Admin Session API

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/api/v1/public/auth/login` | Admin login through the public auth endpoint |
| `GET` | `/api/v1/admin/auth/session` | Current admin session and CSRF token |
| `POST` | `/api/v1/admin/auth/logout` | Admin logout |

Admin login returns a JWT-backed `HttpOnly` cookie. Unsafe admin cookie requests require the `X-Admin-CSRF` header.

## Admin Booking API

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/admin/bookings` | Admin booking overview |
| `GET` | `/api/v1/admin/bookings/customer-lookup` | Customer lookup by phone |
| `POST` | `/api/v1/admin/bookings` | Create an admin booking |
| `POST` | `/api/v1/admin/bookings/hold` | Hold a slot for admin booking work |
| `POST` | `/api/v1/admin/bookings/{id}/hold-refresh` | Refresh an admin booking hold |
| `DELETE` | `/api/v1/admin/bookings/hold/{id}` | Release an admin booking hold |
| `PUT` | `/api/v1/admin/bookings/{id}` | Update a booking |
| `POST` | `/api/v1/admin/bookings/{id}/cancel` | Cancel a booking |
| `GET` | `/api/v1/admin/bookings/blacklist` | List blacklist entries |
| `POST` | `/api/v1/admin/bookings/blacklist` | Create a blacklist entry |
| `DELETE` | `/api/v1/admin/bookings/blacklist/{id}` | Delete a blacklist entry |

## Admin Management API

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `PUT` | `/api/v1/admin/hair-salon` | Update salon profile |
| `GET` | `/api/v1/admin/hair-salons/{hairSalonId}/hours` | Read salon hours |
| `PUT` | `/api/v1/admin/hair-salons/{hairSalonId}/hours/{dayOfWeek}` | Update salon hours for one day |
| `POST` | `/api/v1/admin/employees` | Create employee |
| `PUT` | `/api/v1/admin/employees/{id}` | Update employee |
| `DELETE` | `/api/v1/admin/employees/{id}` | Delete/deactivate employee |
| `POST` | `/api/v1/admin/employees/reorder` | Reorder employees |
| `GET` | `/api/v1/admin/employees/{employeeId}/schedule` | Read employee schedule |
| `PUT` | `/api/v1/admin/employees/{employeeId}/schedule` | Update employee schedule day |
| `GET` | `/api/v1/admin/employees/schedule/period` | Read schedule period settings |
| `PUT` | `/api/v1/admin/employees/schedule/period` | Update schedule period settings |
| `POST` | `/api/v1/admin/treatments` | Create treatment |
| `PUT` | `/api/v1/admin/treatments/{id}` | Update treatment |
| `DELETE` | `/api/v1/admin/treatments/{id}` | Delete/deactivate treatment |
| `POST` | `/api/v1/admin/treatments/reorder` | Reorder treatments |

Admin endpoints are protected by Spring Security and require the admin role.

## Stripe Webhook

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/webhook` | Stripe signed webhook callback |

The webhook handler verifies the `Stripe-Signature` header with `APP_STRIPE_WEBHOOK_SECRET`, rejects oversized payloads, rate-limits invalid webhook attempts in memory, and processes supported PaymentIntent lifecycle events.
