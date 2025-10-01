# Changelog

All notable changes to this project will be documented here.

## [0.1.0-alpha2] - 2025-10-01
### Changed
- Compiler settings updated for backwards compatibility with **Java 11**
- Refactored naming conventions for clarity
- Improved structured logging system
- Changed the HttpClient from Java's built-in to Apache HttpClient
- Ensured HTTP/1.1 and HTTP/2 support with Apache HttpClient

### Added
- Shaded **Jackson library**, **Apache HttpClient**, **Jayway JsonPath** via ShadowJar to avoid dependency conflicts
- JsonPath support for advanced JSON querying
- BackoffPolicy for retries
- Global and Session contextual variables and access to them
- Convenience serialization/deserialization methods for JSON and XML including annotations for basic customization

## [0.1.0-alpha] - 2025-09-01
### Added
- Initial alpha release
- Core support for **Potatoes**, **Cannons**, and **Expectations**
- Structured request/response **logging system**