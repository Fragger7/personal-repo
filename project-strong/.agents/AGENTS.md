# Project Strong Workspace Rules

## Git Sync on Session Startup
At the start of every session, before editing workspace files or making any commits:
1. The agent MUST check the remote GitHub repository (`https://github.com/Fragger7/personal-repo.git`) for any changes on the `main` branch.
2. If there are remote updates inside `project-strong/` that are not present locally, sync those changes into the local workspace (`C:\Development\Apps\Project Strong`) first to ensure you are building on the latest code.
