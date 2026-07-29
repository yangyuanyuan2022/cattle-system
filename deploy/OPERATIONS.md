# Production operations

1. Copy `.env.production.example` into the service manager's secret environment and replace every placeholder. Never commit the populated file.
2. Start the API with `SPRING_PROFILES_ACTIVE=prod`. Production startup intentionally fails when database variables are absent.
3. Configure `VITE_API_BASE_URL=https://your-domain/api/v1` before building the miniapp. Add the HTTPS domain to the WeChat request-domain allowlist.
4. Schedule `backup-mysql.ps1` daily with Windows Task Scheduler. Keep backups on a different disk or remote storage.
5. Perform a restore drill with `restore-mysql.ps1 -BackupZip <file> -ConfirmRestore` against a non-production database before release.
6. Change the initial administrator password before exposing the service and keep API docs disabled in production.

Logs rotate daily or at 50 MB, retain 30 days, and are capped at 5 GB under `LOG_PATH`.
