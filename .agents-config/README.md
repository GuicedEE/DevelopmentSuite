# Global Agents & Skills Configuration

Complete enterprise skills catalog and agent configuration system for deploying 100+ AI skills to `~/.agents` for use across GitHub Copilot, Claude, Cursor, and other AI development tools.

## 🚀 Quick Install

```bash
# Navigate to this directory
cd /home/gedmarc/java/devsuite/.agents-config

# Run automated setup
bash setup-agents.sh

# Verify installation
bash test-agents-config.sh

# Load environment
source ~/.agents/.agents.env
```

**That's it!** You now have 100+ enterprise skills configured and ready to use.

## 📂 What's in This Directory

| File | Purpose |
|------|---------|
| **agents-global.yaml** | Master registry of 100+ skills and agents (curated + system) |
| **setup-agents.sh** | Automated installation script (deploys to ~/.agents) |
| **test-agents-config.sh** | Verification and integration tests |
| **Makefile** | Convenient command shortcuts |
| **SETUP.md** | Detailed setup and configuration guide |
| **INSTALL-GUIDE.md** | Installation instructions and usage examples |
| **README.md** | This file |

## 🎯 What Gets Installed

Running `setup-agents.sh` installs to `~/.agents`:

```
~/.agents/
├── agents-global.yaml               # 100+ skills registry
├── .agents.env                      # Environment variables (auto-source)
├── README.md                        # Quick start guide
├── SKILLS-INDEX.txt                 # Searchable skill index
│
├── skills/
│   └── airules/ → (symlink)         # Points to AIRules repository
│
└── config/
    ├── github-copilot/copilot.yaml
    ├── claude/claude.yaml
    └── cursor/cursor.yaml
```

## 📊 Catalog Contents

### Curated Skills (39+)
General-purpose skills for any project:
- **Architecture** — System design, diagramming, ADRs
- **Development** — Code review, debugging, TDD
- **Testing** — E2E testing, visual testing, QA strategy
- **Git & Workflow** — Commits, branching, CI/CD
- **Infrastructure** — Terraform (9 variants), ARM migration, cloud
- **Security** — Compliance, best practices, security ops
- **UI & Design** — Figma, AG Grid, prompt engineering

### System Skills (70+)
Project-specific skills for GuicedEE ecosystem:

**GuicedEE (25 modules)**
- Authentication, CDI, REST, Persistence
- Messaging (Kafka, RabbitMQ, IBM MQ)
- Reactive (Vert.x), Telemetry, Health Checks

**JWebMP (44 modules)**
- Core framework, data grids, charts, UI components
- Icons, animations, utilities

**Enterprise (2 modules)**
- ActivityMaster (FSDM resource management)
- EntityAssist (Reactive CRUD)

## 💡 Usage Examples

### View Available Skills
```bash
# List all skill IDs
yq eval '.agents[].id' ~/.agents/agents-global.yaml

# Count total skills
yq eval '.agents | length' ~/.agents/agents-global.yaml

# List skill index
cat ~/.agents/SKILLS-INDEX.txt
```

### Find Skills by Category
```bash
# Architecture skills
yq eval '.agents[] | select(.category[] == "Architecture").name' ~/.agents/agents-global.yaml

# All Terraform skills
make find-skill PATTERN=terraform

# Security skills
make list-by-tag TAG=security
```

### Use in AI Tools

**GitHub Copilot:**
```python
# Use skill: senior-architect
# Design the authentication system
```

**Claude:**
```
"What skills do you have for REST APIs?"
"Apply the guicedee-rest skill to help with this endpoint"
```

**Cursor:**
```
Access via command palette: "Skills: List All"
```

## 🛠️ Make Commands

```bash
# Setup
make install                 # Full installation
make setup-env              # Configuration setup
make setup-configs          # Tool-specific configs

# Discovery
make list-skills            # All skills
make list-curated           # Curated only
make list-system            # System only
make find-skill PATTERN=foo # Search by pattern
make list-categories        # Show all categories
make list-by-tag TAG=security # Filter by tag

# Verification
make test                   # Run all tests
make verify                 # Check installation status
make check-yaml             # Validate YAML syntax

# Management
make update                 # Update from AIRules
make export-json            # Export as JSON
make export-csv             # Export as CSV
make clean                  # Remove installation
```

## ✅ Installation Checklist

- [ ] Navigate to `.agents-config` directory
- [ ] Run `bash setup-agents.sh`
- [ ] Run `bash test-agents-config.sh` (should pass all tests)
- [ ] Run `source ~/.agents/.agents.env`
- [ ] Verify with `make list-skills` or `yq eval '.agents | length' ~/.agents/agents-global.yaml`
- [ ] Add `source ~/.agents/.agents.env` to `~/.bashrc` or `~/.zshrc`
- [ ] Restart your AI tool (GitHub Copilot, Claude, etc.)

## 🌍 Environment Variables

After setup, these are available:

```bash
AIRULES_HOME              # /home/gedmarc/java/devsuite/AIRules
GUICEDEE_HOME            # /home/gedmarc/java/devsuite/GuicedEE
JWEBMP_HOME              # /home/gedmarc/java/devsuite/JWebMP
ACTIVITYMASTER_HOME      # /home/gedmarc/java/devsuite/ActivityMaster
AGENTS_CONFIG_DIR        # ~/.agents
AGENTS_SKILLS_PATH       # ~/.agents/skills/airules
```

## 📚 Documentation

- **INSTALL-GUIDE.md** — Step-by-step installation and usage
- **SETUP.md** — Detailed configuration reference
- **~/.agents/README.md** — Quick reference (installed)
- **~/.agents/SKILLS-INDEX.txt** — Searchable index (installed)

## 🔍 Common Tasks

### Find a Specific Skill
```bash
make find-skill PATTERN=terraform
yq eval '.agents[] | select(.id == "senior-architect")' ~/.agents/agents-global.yaml
```

### View Skill Details
```bash
# Get full details for a skill
yq eval '.agents[] | select(.id == "guicedee-rest")' ~/.agents/agents-global.yaml

# Access skill documentation
cat $AIRULES_HOME/skills/.curated/senior-architect/SKILL.md
```

### Export Skills
```bash
make export-json           # Export as JSON
make export-csv            # Export as CSV
```

## ✨ Features

✅ **100+ enterprise skills** — Comprehensive catalog of curated and system skills  
✅ **Automated installation** — One command setup to `~/.agents`  
✅ **Multiple AI tool support** — GitHub Copilot, Claude, Cursor, etc.  
✅ **Full discovery** — Find skills by ID, category, tag, or search pattern  
✅ **Make commands** — Convenient shortcuts for all operations  
✅ **YAML validation** — Built-in configuration validation  
✅ **Environment variables** — Auto-loadable configuration  
✅ **Documentation** — Comprehensive guides and quick references  
✅ **Symlink to AIRules** — Always uses latest skills repository  
✅ **Zero dependencies** — Uses standard tools (bash, yq, make)

## 🚦 Status

- ✅ Configuration: Ready for deployment
- ✅ Installation: Fully automated
- ✅ Verification: Complete test suite
- ✅ Documentation: Comprehensive guides
- ✅ Version: 1.0.0
- ✅ Last Updated: 2026-06-08

## 📞 Support

- **Repository**: https://github.com/GuicedEE/ai-rules
- **AIRules Docs**: `/home/gedmarc/java/devsuite/AIRules/README.md`
- **Skill Creator**: `/home/gedmarc/java/devsuite/AIRules/skills/.system/skill-creator/SKILL.md`

## 🎓 Getting Started

1. **Install** → `bash setup-agents.sh`
2. **Verify** → `bash test-agents-config.sh`
3. **Load** → `source ~/.agents/.agents.env`
4. **Explore** → `make list-skills`
5. **Use** → Reference skills by ID in your AI tool

---

**Created**: 2026-06-08  
**Version**: 1.0.0  
**Total Skills**: 100+  
**Status**: ✅ Ready for Use

For detailed instructions, see **INSTALL-GUIDE.md**

