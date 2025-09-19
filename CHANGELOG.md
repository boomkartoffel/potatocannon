# Changelog

All notable changes to this project will be documented here.

## [0.1.0-alpha2] - 2025-09-11
### Changed
- Compiler settings updated for backwards compatibility with **Java 11**
- Refactored naming conventions for clarity
- Improved structured logging system
- Changed the HttpClient from Java's built-in to Apache HttpClient
- Ensured HTTP/1.1 and HTTP/2 support with Apache HttpClient

### Added
- Shaded **Jackson library** and **Apache HttpClient** via ShadowJar to avoid dependency conflicts
- BackoffPolicy for retries

## [0.1.0-alpha] - 2025-09-01
### Added
- Initial alpha release
- Core support for **Potatoes**, **Cannons**, and **Expectations**
- Structured request/response **logging system**