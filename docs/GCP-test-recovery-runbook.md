# GCP TEST recovery runbook

This runbook covers two pending operational checks for the GCP TEST VM:

1. Verify that the MariaDB data survives a controlled VM reboot.
2. Create a logical SQL backup and verify it by restoring it into an isolated,
   temporary MariaDB container.

These procedures have **not yet been run or accepted**. Use synthetic TEST data
only. Do not run `docker compose down --volumes`, remove Docker volumes, or
restore over the live application databases during these checks.

## Environment

- Checkout: `/opt/srm-toolkit`
- Private Compose environment: `/etc/srm-toolkit/compose.env`
- Compose project: `srm-test`
- Compose files: `docker-compose.yml` and `docker-compose.gcp-test-http.yml`
- MariaDB container: `srm-test-mariadb-1`
- Persistent data disk: `/srv/srm-data`
- Backup directory: `/srv/srm-data/backups`

Run commands on `srm-test-vm` over IAP SSH. Keep credentials and SQL dumps
private. A dump can contain personal data even when the environment is intended
for synthetic records.

## A. Verify persistence after VM reboot

Schedule a short maintenance window. Before rebooting, confirm the persistent
disk is mounted and MariaDB stores its data on that disk:

```bash
findmnt /srv/srm-data
sudo docker inspect srm-test-mariadb-1 --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Source}}{{end}}{{end}}'
sudo docker compose --env-file /etc/srm-toolkit/compose.env -p srm-test -f docker-compose.yml -f docker-compose.gcp-test-http.yml ps
```

The MariaDB mount source should be under `/srv/srm-data/docker/volumes/`. Record
the registration code and current status of an existing synthetic registration
in Biuro. Do not record participant personal details in the deployment log.
If either disk check fails, do not reboot as part of this test; investigate the
mount configuration first.

Reboot the VM:

```bash
sudo reboot
```

The SSH connection will close. Reconnect through IAP after the VM starts, then
verify the data disk, Docker root and Compose service health:

```bash
findmnt /srv/srm-data
sudo docker info --format '{{.DockerRootDir}}'
sudo docker compose --env-file /etc/srm-toolkit/compose.env -p srm-test -f docker-compose.yml -f docker-compose.gcp-test-http.yml ps
```

Expected: `/srv/srm-data` is mounted, Docker's root is
`/srv/srm-data/docker`, and all services return to their expected running or
healthy state. Verify both domain routes load and the recorded synthetic
registration still exists in Biuro with the same status. If anything is missing
or unhealthy, stop and investigate; do not recreate the database or delete
volumes.

Record the date, deployed commit, service status, and result in the deployment
checklist or WIP. A successful VM reboot check verifies persistence/startup only;
it does not verify backups.

## B. Create a logical MariaDB backup

First confirm the VM has enough free space and the expected MariaDB container is
running:

```bash
df -h /srv/srm-data
sudo docker compose --env-file /etc/srm-toolkit/compose.env -p srm-test -f docker-compose.yml -f docker-compose.gcp-test-http.yml ps mariadb
```

Create a private backup directory and dump both application schemas. The
MariaDB root password is read inside the database container from its existing
runtime environment; do not source `compose.env` as a shell script because
Compose env files may contain unquoted values with spaces.

```bash
sudo install -d -m 700 /srv/srm-data/backups
BACKUP_FILE="/srv/srm-data/backups/srm-test-$(date +%Y%m%d-%H%M%S).sql"
set -o pipefail
sudo docker exec srm-test-mariadb-1 sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump --user=root --single-transaction --routines --events --databases registration_db biuro_db' | sudo tee "$BACKUP_FILE" >/dev/null && sudo chmod 600 "$BACKUP_FILE" && sudo test -s "$BACKUP_FILE" && sudo ls -lh "$BACKUP_FILE" && sudo sha256sum "$BACKUP_FILE"
```

If the dump command fails or produces an empty file, treat that file as invalid
and do not use it for restore. Check the MariaDB logs and correct the cause
before retrying. Retain the successful dump's filename, byte size, checksum and
creation time without recording its contents.

The dump is currently stored on the same persistent disk as MariaDB. This is
useful for a restore drill and protection from some logical mistakes, but it is
not an independent disaster-recovery copy. Before relying on backups, define and
verify a separate protected destination, retention, access controls and restore
procedure (for example, a restricted Cloud Storage location).

## C. Verify restore in an isolated temporary MariaDB container

Run this only after a non-empty dump has been verified. The temporary container
has no published ports and uses `--network none`; the dump recreates the original
schema names only inside that isolated MariaDB instance. It must never be
imported into `srm-test-mariadb-1` for this test.

The VM has approximately 8 GiB RAM. Check available capacity first and perform
the drill during a quiet period; defer if the application is already under
memory pressure. Confirm the image tag matches the running database image:

```bash
sudo docker inspect srm-test-mariadb-1 --format '{{.Config.Image}}'
free -h
```

Set `BACKUP_FILE` to the verified path from section B. The following commands
read the existing root password from the MariaDB container configuration into a
shell variable without printing it, then start a temporary unexposed instance.
The password is passed through the process environment rather than appearing as
a Docker command-line argument. Run all commands in the same shell session:

```bash
ROOT_PASSWORD="$(sudo docker inspect srm-test-mariadb-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^MARIADB_ROOT_PASSWORD=//p')"
test -n "$ROOT_PASSWORD" && echo "Database credential loaded"
RESTORE_CONTAINER="srm-restore-check-$(date +%s)"
MARIADB_ROOT_PASSWORD="$ROOT_PASSWORD" sudo --preserve-env=MARIADB_ROOT_PASSWORD docker run --detach --rm --name "$RESTORE_CONTAINER" --network none --env MARIADB_ROOT_PASSWORD mariadb:11.4
```

Wait until the temporary database is ready, then import the verified dump:

```bash
READY=false
for attempt in $(seq 1 60); do if sudo docker exec "$RESTORE_CONTAINER" healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; then READY=true; break; fi; sleep 2; done
test "$READY" = true || { echo "Temporary MariaDB did not become ready; stop and inspect it"; exit 1; }
sudo cat "$BACKUP_FILE" | MYSQL_PWD="$ROOT_PASSWORD" sudo --preserve-env=MYSQL_PWD docker exec -i --env MYSQL_PWD "$RESTORE_CONTAINER" mariadb --user=root
```

Check that both schemas and their expected tables exist in the temporary
instance. Choose representative tables based on the current migrations; do not
print row contents:

```bash
MYSQL_PWD="$ROOT_PASSWORD" sudo --preserve-env=MYSQL_PWD docker exec --env MYSQL_PWD "$RESTORE_CONTAINER" mariadb --user=root -e 'SHOW DATABASES; SHOW TABLES FROM registration_db; SHOW TABLES FROM biuro_db;'
```

After confirming the schemas/tables are present, remove only the named temporary
restore container. Its anonymous database volume is removed with the container
because it was started with `--rm`:

```bash
sudo docker stop "$RESTORE_CONTAINER"
unset ROOT_PASSWORD RESTORE_CONTAINER
```

Record the dump checksum, restore result and verification evidence. If restore
or verification fails, preserve the dump for investigation and do not touch the
live MariaDB container or its volume.

## Completion status

- [ ] Controlled VM reboot and post-reboot application/data checks
- [ ] Logical backup created and checked
- [ ] Restore verified in isolated MariaDB instance
- [ ] Independent backup destination, retention and access controls established
