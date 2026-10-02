# Publishing to GitHub

The source does not contain a user's Google credentials, Device Access identifiers, home address, or readings. Each user enters their own connection details in the app. The default weather location is a city, rather than a private street address.

## Files that stay local

`.gitignore` excludes downloaded Google Home SDK files, local Android paths, IDE and Gradle caches, signing keys, environment files, common OAuth/service-account credential files, logs, databases, CSV exports, Beads records, agent configuration, and private backup bundles. Ignoring a file does not remove an older committed copy; check history as well as the current files.

Keep private data in those ignored locations. Generic filenames cannot catch every credential file. Screenshots and artwork must be reviewed visually before upload; hide account details, home addresses, unrelated notifications, and personal home-screen content. Only the app icon artwork is included in this checkout.

The local `.private-backups/` directory can contain an original history bundle. That bundle contains private author and issue metadata and must remain local. Do not upload a ZIP of the full working directory, mirror the private backup, or restore the old history into the public repository.

## Pre-upload check

With Python 3 and Git installed, run:

```powershell
python .github/scripts/check-publication.py --history
git status --short
```

The check examines tracked filenames against the ignore rules, common credential patterns, email addresses in text, commit messages, and commit author/committer metadata. It reports filenames and categories without printing matched values. It is a useful check, not a substitute for reviewing new files and images. Add project-specific private identifiers to your own local checks when introducing new integrations.

The author has chosen `Benjamin Kar <benjkar@hotmail.com>` for public commit attribution. This is the only personal email explicitly allowed by the privacy checker; other personal addresses are still flagged. Commit metadata is public too. Contributors can use their GitHub-provided private `noreply` address.

## Upload

Create an empty GitHub repository without an initial README, license, or `.gitignore`, then use the URL GitHub provides:

```powershell
git remote add origin https://github.com/BenK22/NestClimateMonitor.git
git push -u origin main
```

Push the reviewed `main` branch. The app runs directly on the phone; publishing the source does not require putting any personal Google credentials in GitHub. Optional signed-release automation uses GitHub Actions secrets as described in [RELEASING.md](RELEASING.md).

After uploading, verify the repository's files and commit author details in GitHub. If a credential was ever made public, remove it from history and rotate it with its provider; `.gitignore` alone cannot undo disclosure.
