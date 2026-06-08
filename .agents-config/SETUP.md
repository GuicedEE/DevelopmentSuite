# Global Agents & Skills Configuration Setup

## Overview

This directory contains the global enterprise skills catalog and agent configuration that can be installed to `~/.agents` for use across all AI development tools (GitHub Copilot, Claude, Cursor, ChatGPT, etc.).

## Contents

- **`agents-global.yaml`** — Master agent registry with 100+ skills across curated and system catalogs
- **`setup-agents.sh`** — Installation script to deploy to `~/.agents`
- **`test-agents-config.sh`** — Validation script to verify installation
- **`SETUP.md`** — This file

## Quick Install

### Option 1: Automated Setup (Recommended)

```bash
# Navigate to this directory
cd /home/gedmarc/java/devsuite/.agents-config

# Run setup script
bash setup-agents.sh

# Verify installation
bash test-agents-config.sh
```

### Option 2: Manual Setup

```bash
# Create directories
mkdir -p ~/.agents/config
mkdir -p ~/.agents/skills

# Copy configuration
cp agents-global.yaml ~/.agents/

# Link AIRules skills
ln -s /home/gedmarc/java/devsuite/AIRules/skills ~/.agents/skills/airules

# Create environment file
cp setup-agents.sh ~/.agents/.agents.env  # (You'll adapt this)
```

## Configuration Structure

After setup, your `~/.agents` directory will contain:

```
~/.agents/
├── agents-global.yaml              # Master agent registry (100+ skills)
├── .agents.env                      # Environment variables to source
├── README.md                        # Quick start guide
├── SKILLS-INDEX.txt                 # Human-readable skill index
│
├── skills/
│   └── airules/ → (symlink)         # Points to AIRules skills repository
│       ├── .curated/                # General-purpose skills (39+)
│       └── .system/                 # GuicedEE, JWebMP, ActivityMaster (70+)
│
└── config/
    ├── github-copilot/
    │   └── copilot.yaml            # GitHub Copilot integration
    ├── claude/
    │   └── claude.yaml              # Claude integration
    └── cursor/
        └── cursor.yaml              # Cursor editor integration
```

## Environment Setup

After installation, always source the environment variables:

```bash
source ~/.agents/.agents.env
```

This sets:
- `AIRULES_HOME` — Path to AIRules repository
- `GUICEDEE_HOME` — Path to GuicedEE project
- `JWEBMP_HOME` — Path to JWebMP project
- `ACTIVITYMASTER_HOME` — Path to ActivityMaster project
- `AGENTS_CONFIG_DIR` — Path to agents configuration
- `AGENTS_SKILLS_PATH` — Path to skills catalog

Add to your shell profile (`.bashrc`, `.zshrc`, etc.) for automatic loading:

```bash
[ -f ~/.agents/.agents.env ] && source ~/.agents/.agents.env
```

## Using Skills in AI Tools

### GitHub Copilot

Reference skills by their ID:

```
"Use the 'senior-architect' skill to design this system"
"Apply the 'terraform-validator' skill to check this configuration"
"Reference 'guicedee-rest' skill for REST endpoint examples"
```

### Claude

Ask about available skills:

```
"What skills do you have available?"
"Show me the architecture skill"
"Use the security-compliance skill for this audit"
```

### Cursor Editor

Skills are available via inline hints and quick reference commands.

## Discovering Skills

### View All Available Skills

```bash
# List all skill IDs
yq eval '.agents[].id' ~/.agents/agents-global.yaml

# Count total skills
yq eval '.agents | length' ~/.agents/agents-global.yaml
```

### Find Skills by Category

```bash
# Architecture skills
yq eval '.agents[] | select(.category[] | contains("Architecture")).name' ~/.agents/agents-global.yaml

# All Terraform skills
yq eval '.agents[] | select(.id | contains("terraform")).name' ~/.agents/agents-global.yaml

# GuicedEE framework skills
yq eval '.agents[] | select(.id | contains("guicedee")).name' ~/.agents/agents-global.yaml
```

### View Skill Details

```bash
# Full details for a skill
yq eval '.agents[] | select(.id == "senior-architect")' ~/.agents/agents-global.yaml

# All skills in a category
yq eval '.agents[] | select(.category[] == "Security")' ~/.agents/agents-global.yaml
```

### Using Tags

```bash
# Show available tags
yq eval '.tags | keys' ~/.agents/agents-global.yaml

# Find skills by tag
yq eval '.tags.kubernetes' ~/.agents/agents-global.yaml
```

## Skill Categories

The configuration includes skills organized by these categories:

### Curated Skills (39+)
- **Automation** — Agent orchestration, skill adoption
- **Architecture & Design** — System design, ADRs, information architecture
- **Development** — Code quality, debugging, TDD
- **Git & Version Control** — Conventional commits, branch workflows
- **Infrastructure & Cloud** — Terraform family, cloud migrations
- **Testing & QA** — E2E testing, visual testing, quality strategy
- **Security** — Compliance, best practices, security ops
- **Design & UI** — Figma, AG Grid, prompt engineering

### System Skills (70+)
- **GuicedEE Framework** (25+ modules) — Full JavaEE microservices stack
- **JWebMP Framework** (44+ modules) — Rich UI toolkit with data grids, charts, components
- **ActivityMaster** — Enterprise resource management
- **EntityAssist** — Reactive CRUD persistence
- **Platform & Tooling** — Skill creation and installation

## Common Tasks

### Add a New Skill

Use the `skill-creator` skill:

```
Access: $AIRULES_HOME/skills/.system/skill-creator/SKILL.md
Reference: senior-architect skill for system design task
```

### Configure a New Tool Integration

1. Create config directory: `mkdir -p ~/.agents/config/my-tool`
2. Copy a sample config and adapt it
3. Update skill discovery method if needed
4. Test with `test-agents-config.sh`

### Update Skills from Source

```bash
cd $AIRULES_HOME
git pull origin main

# Configuration auto-reloads if SKILLS_AUTO_RELOAD=true
```

### Export Skills List

```bash
# JSON format
yq eval -o json '.agents[]' ~/.agents/agents-global.yaml > skills.json

# CSV format (requires additional tools)
yq eval '.agents[] | [.id, .name, .category[0]] | @csv' ~/.agents/agents-global.yaml

# Markdown format for documentation
yq eval '.agents[] | "- **\(.name)** (\(.id)): \(.description)"' ~/.agents/agents-global.yaml
```

## Troubleshooting

### Skills not discovered in AI tool

1. Verify setup completed: `bash test-agents-config.sh`
2. Check file exists: `ls -la ~/.agents/agents-global.yaml`
3. Validate YAML: `yq eval . ~/.agents/agents-global.yaml > /dev/null`
4. Source environment: `source ~/.agents/.agents.env`

### Cannot access AIRules skills

1. Check AIRules path: `echo $AIRULES_HOME`
2. Verify symlink: `ls -la ~/.agents/skills/airules`
3. Test direct path: `ls -la $AIRULES_HOME/skills/.curated/`

### Tool-specific configuration issues

1. Check tool config exists: `ls ~/.agents/config/[tool-name]/`
2. Validate YAML syntax: `yq eval . ~/.agents/config/[tool-name]/[tool].yaml`
3. Review tool documentation: See tool-specific config files

### Environment variables not set

1. Verify .agents.env exists: `ls -la ~/.agents/.agents.env`
2. Source manually: `source ~/.agents/.agents.env`
3. Check for syntax errors: `bash -n ~/.agents/.agents.env`
4. Add to shell profile for auto-loading

## Updating Configuration

### Update Master Registry

When new skills are added to AIRules:

```bash
# Pull latest from AIRules
cd $AIRULES_HOME && git pull

# Regenerate agents-global.yaml (if script provided)
# Or manually update ~/.agents/agents-global.yaml from AIRules version
cp $AIRULES_HOME/agents-global.yaml ~/.agents/agents-global.yaml
```

### Add Custom Skills

Create a custom skill following the standard anatomy in `$AIRULES_HOME/skills/.system/skill-creator/SKILL.md`, then add an entry to agents-global.yaml.

## Integration Examples

### GitHub Copilot in VS Code

Add to `settings.json`:

```json
{
  "github.copilot.enable": {
    "*": true
  }
}
```

Reference skills in prompts:
```
"Using the terraform-validator skill, validate this configuration"
```

### Claude in Browser

Reference the skill catalog URL when asking questions:
```
"Background context: I have 100+ enterprise skills at ~/.agents/agents-global.yaml
Please help me use the 'senior-architect' skill for this design"
```

### Cursor + Custom Commands

Create `.cursor/commands/skills.txt`:
```
@agents Use skill
  - Access ~/.agents/agents-global.yaml
  - Find by category or tag
  - Reference SKILL.md files
```

## Next Steps

1. ✅ **Install** — Run `bash setup-agents.sh`
2. ✅ **Verify** — Run `bash test-agents-config.sh`
3. ✅ **Configure Shell** — Add to `~/.bashrc` or `~/.zshrc`:
   ```bash
   [ -f ~/.agents/.agents.env ] && source ~/.agents/.agents.env
   ```
4. ✅ **Test Discovery** — Run `yq eval '.agents | length' ~/.agents/agents-global.yaml`
5. ✅ **Use in Tools** — Reference skills by ID in your AI tool prompts

## Support & Resources

- **AIRules Repository** — https://github.com/GuicedEE/ai-rules
- **Skill Documentation** — `$AIRULES_HOME/README.md`
- **Skill Creator Guide** — `$AIRULES_HOME/skills/.system/skill-creator/SKILL.md`
- **Skills Index** — `~/.agents/SKILLS-INDEX.txt` (after setup)

## Version Information

- **Configuration Version**: 1.0.0
- **Last Updated**: 2026-06-08
- **Total Skills**: 100+
- **Curated**: 39+
- **System**: 70+

---

**Setup by**: Global Agents Configuration System  
**Maintained by**: Enterprise Skills Repository  
**License**: See AIRules repository

