# INSTALLATION & USAGE GUIDE

## 🎯 Quick Start (2 minutes)

### Step 1: Run Setup
```bash
cd /home/gedmarc/java/devsuite/.agents-config
bash setup-agents.sh
```

### Step 2: Verify Installation
```bash
bash test-agents-config.sh
```

### Step 3: Source Environment
```bash
source ~/.agents/.agents.env
```

### Step 4: Start Using Skills
```bash
# List all skills
yq eval '.agents[].id' ~/.agents/agents-global.yaml

# Find skills by category
yq eval '.agents[] | select(.category[] == "Architecture").name' ~/.agents/agents-global.yaml
```

---

## 📦 What Gets Installed

When you run `setup-agents.sh`, it creates:

```
~/.agents/
├── agents-global.yaml                 # 100+ skills registry
├── .agents.env                        # Environment variables
├── README.md                          # Quick reference
├── SKILLS-INDEX.txt                   # Searchable index
│
├── skills/
│   └── airules/ → (symlink)           # AIRules skills repository
│
└── config/
    ├── github-copilot/copilot.yaml
    ├── claude/claude.yaml
    └── cursor/cursor.yaml
```

**Total**: ~100+ agents/skills across 2 catalogs
- Curated: 39+ general-purpose skills
- System: 70+ GuicedEE/JWebMP/ActivityMaster skills

---

## 🚀 Using Skills in AI Tools

### GitHub Copilot
Reference skills by ID in code comments or prompts:
```python
# Use skill: senior-architect
# Design the authentication system
```

### Claude
Ask about skills:
```
"Which skill should I use for system architecture?"
"Apply the terraform-validator skill to this config"
```

### Cursor Editor
Access skills via command palette or inline hints

---

## 🔍 Finding the Right Skill

### By Task Type

| Task | Skill | Path |
|------|-------|------|
| System Design | `senior-architect` | `.curated/senior-architect/` |
| REST APIs | `guicedee-rest` | `.system/guicedee-rest/` |
| Testing | `playwright` | `.curated/playwright/` |
| Terraform | `terraform-*` | `.curated/terraform-*/` |
| Database | `guicedee-persistence` | `.system/guicedee-persistence/` |
| UI Development | `jwebmp-core` | `.system/jwebmp-core/` |

### By Category
```bash
# Show all categories
yq eval '.agents[]?.category[]' ~/.agents/agents-global.yaml | sort | uniq

# Find by category
yq eval '.agents[] | select(.category[] == "Backend").name' ~/.agents/agents-global.yaml
```

### By Keyword
```bash
# Find terraform skills
grep -l terraform ~/.agents/agents-global.yaml

# Find security skills
yq eval '.agents[] | select(.tags[] == "security").name' ~/.agents/agents-global.yaml
```

---

## 📚 Skill Catalogs

### Curated Skills (39+)

**Architecture & Design**
- `senior-architect` — System design, ADRs, diagrams
- `senior-backend` — Backend programming patterns
- `senior-devops` — Infrastructure and deployment
- `information-architect` — Information architecture

**Development**
- `code-reviewer` — Code quality and review
- `systematic-debugging` — Debugging methodology
- `test-driven-development` — TDD workflow
- `security-best-practices` — Security practices

**Git & Workflow**
- `git-commit-helper` — Conventional commits
- `finishing-a-development-branch` — Branch workflows
- `gh-fix-ci` — GitHub Actions debugging
- `changelog-generator` — Automated changelogs

**Infrastructure & Cloud**
- `terraform-code-generator` through `terraform-validator` (9 skills)
- `arm-to-terraform-migration` — Azure ARM to TF

**Testing & QA**
- `playwright` — E2E testing
- `senior-qa` — QA strategy

**UI & Design**
- `figma` — Design integration
- `aggrid` — Data grid integration

**And More**: 20+ additional curated skills

### System Skills (70+)

**GuicedEE (25+ modules)**
- Authentication, CDI, REST, Persistence
- Messaging (Kafka, RabbitMQ, IBM MQ)
- Reactive (Vert.x), Telemetry, Health

**JWebMP (44+ modules)**
- Core HTML/CSS/Events framework
- Data grids (AG Grid, DataTables)
- Charts (AG Charts, Chart.js, D3, C3)
- UI frameworks (Angular, Bootstrap, Web Awesome)
- Icons (Font Awesome, Material, Themify)
- Utilities (Markdown, Prism, i18n)

**Enterprise (2+ modules)**
- ActivityMaster — FSDM resource management
- EntityAssist — Reactive CRUD

---

## 🛠️ Using Make Commands

```bash
cd /home/gedmarc/java/devsuite/.agents-config

# Install everything
make install

# List all skills
make list-skills

# Find terraform skills
make find-skill PATTERN=terraform

# Find skills by tag
make list-by-tag TAG=security

# Export as JSON
make export-json

# Clean up
make clean
```

---

## 🔌 Tool Integration

### GitHub Copilot Integration

1. Skills are referenced by `id` in prompts
2. Copilot will suggest relevant skills based on context
3. Load skill details with: `Load skill: senior-architect`

### Claude Integration

1. Ask Claude about available skills
2. Reference skills by name: `"Use the senior-architect skill"`
3. Skills auto-load from configuration

### Cursor Integration

1. Skills available in command palette
2. Reference via inline hints
3. Quick access commands

---

## 🌍 Environment Variables

After running `setup-agents.sh`, these are available:

```bash
# Source them:
source ~/.agents/.agents.env

# Then use in scripts:
export AIRULES_HOME              # AIRules repository
export GUICEDEE_HOME             # GuicedEE project
export JWEBMP_HOME               # JWebMP project
export ACTIVITYMASTER_HOME       # ActivityMaster project
export AGENTS_CONFIG_DIR         # ~/.agents
export AGENTS_SKILLS_PATH        # Skills catalog path
```

Add to shell profile for automatic loading:
```bash
# ~/.bashrc or ~/.zshrc
[ -f ~/.agents/.agents.env ] && source ~/.agents/.agents.env
```

---

## ✅ Verification

### Check Installation
```bash
# Should exist:
ls ~/.agents/agents-global.yaml
ls ~/.agents/.agents.env
ls -L ~/.agents/skills/airules

# Should contain 100+ agents:
yq eval '.agents | length' ~/.agents/agents-global.yaml
```

### Validate Configuration
```bash
# Run tests:
bash ~/.agents/../.agents-config/test-agents-config.sh

# Or using make:
cd /home/gedmarc/java/devsuite/.agents-config
make test
```

### Check Specific Skills
```bash
# Find senior-architect:
yq eval '.agents[] | select(.id == "senior-architect")' ~/.agents/agents-global.yaml

# List all REST-related skills:
yq eval '.agents[] | select(.id | contains("rest")).name' ~/.agents/agents-global.yaml
```

---

## 🐛 Troubleshooting

### Skills Not Found
```bash
# Verify config exists:
ls -la ~/.agents/agents-global.yaml

# Validate YAML:
yq eval . ~/.agents/agents-global.yaml > /dev/null

# Reload environment:
source ~/.agents/.agents.env
```

### Path Issues
```bash
# Check AIRules location:
echo $AIRULES_HOME
ls $AIRULES_HOME/skills

# Verify symlink:
ls -la ~/.agents/skills/airules
```

### Tool Not Recognizing Skills
1. Restart your AI tool
2. Ensure environment is sourced
3. Check tool-specific config files
4. Verify YAML syntax

---

## 📖 Next Steps

1. ✅ Run `bash setup-agents.sh`
2. ✅ Run `bash test-agents-config.sh`
3. ✅ Add to shell profile: `source ~/.agents/.agents.env`
4. ✅ Explore skills: `make list-skills`
5. ✅ Read skill documentation: `$AIRULES_HOME/skills/.curated/senior-architect/SKILL.md`
6. ✅ Use in your AI tool by referencing skill ID

---

## 📞 Support

- **Repository**: https://github.com/GuicedEE/ai-rules
- **Documentation**: `$AIRULES_HOME/README.md`
- **Skill Guide**: `$AIRULES_HOME/skills/.system/skill-creator/SKILL.md`
- **Config Location**: `~/.agents/README.md`

---

## 📋 Files in This Directory

| File | Purpose |
|------|---------|
| `agents-global.yaml` | Master skill registry (100+ entries) |
| `setup-agents.sh` | Installation script |
| `test-agents-config.sh` | Verification script |
| `Makefile` | Command shortcuts |
| `SETUP.md` | Detailed setup guide |
| `INSTALL-GUIDE.md` | This file |

---

**Version**: 1.0.0  
**Last Updated**: 2026-06-08  
**Total Skills**: 100+  
**Status**: Ready for deployment

