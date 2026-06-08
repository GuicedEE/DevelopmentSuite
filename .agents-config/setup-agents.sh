#!/bin/bash
# Setup script for global agents and skills configuration
# Installs enterprise skills catalog to ~/.agents

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENTS_DIR="${AGENTS_DIR:-$HOME/.agents}"
AIRULES_HOME="${AIRULES_HOME:-/home/gedmarc/java/devsuite/AIRules}"
CONFIG_SOURCE="${SCRIPT_DIR}"

echo "═══════════════════════════════════════════════════════════════════"
echo "Global Agents & Skills Configuration Setup"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "Target: $AGENTS_DIR"
echo "AIRules: $AIRULES_HOME"
echo ""

# Create ~/.agents directory if it doesn't exist
mkdir -p "$AGENTS_DIR"
mkdir -p "$AGENTS_DIR/skills"
mkdir -p "$AGENTS_DIR/config"

# Copy global configuration
echo "📋 Installing global agent configuration..."
cp "$CONFIG_SOURCE/agents-global.yaml" "$AGENTS_DIR/agents-global.yaml"
echo "✓ agents-global.yaml installed"

# Create symlink to AIRules skills
echo "🔗 Linking AIRules skills..."
rm -f "$AGENTS_DIR/skills/airules"
ln -s "$AIRULES_HOME/skills" "$AGENTS_DIR/skills/airules"
echo "✓ AIRules skills linked"

# Create environment variables file
echo "🔧 Creating environment configuration..."
cat > "$AGENTS_DIR/.agents.env" << 'EOF'
#!/bin/bash
# Global agent environment variables

# AIRules and Project Paths
export AIRULES_HOME="${AIRULES_HOME:-/home/gedmarc/java/devsuite/AIRules}"
export GUICEDEE_HOME="${GUICEDEE_HOME:-/home/gedmarc/java/devsuite/GuicedEE}"
export JWEBMP_HOME="${JWEBMP_HOME:-/home/gedmarc/java/devsuite/JWebMP}"
export ACTIVITYMASTER_HOME="${ACTIVITYMASTER_HOME:-/home/gedmarc/java/devsuite/ActivityMaster}"

# Agent Configuration
export AGENTS_CONFIG_DIR="${HOME}/.agents"
export AGENTS_GLOBAL_CONFIG="${AGENTS_CONFIG_DIR}/agents-global.yaml"
export AGENTS_SKILLS_PATH="${AGENTS_CONFIG_DIR}/skills/airules"

# Skill Discovery
export SKILLS_DISCOVERY_ENABLED=true
export SKILLS_CACHE_ENABLED=true
export SKILLS_AUTO_RELOAD=true

# Agent Behavior
export AGENT_FALLBACK_MODE="direct_skill_load"
export AGENT_VERBOSE_LOGGING=false
EOF
chmod +x "$AGENTS_DIR/.agents.env"
echo "✓ Environment configuration created"

# Create GitHub Copilot configuration
echo "⚙️  Creating GitHub Copilot configuration..."
mkdir -p "$AGENTS_DIR/config/github-copilot"
cat > "$AGENTS_DIR/config/github-copilot/copilot.yaml" << 'EOF'
# GitHub Copilot Agent Configuration
# Reference: agents-global.yaml

version: "1.0"
agent: "github-copilot"

skills:
  catalog: "${AGENTS_CONFIG_DIR}/agents-global.yaml"
  discovery_method: "id_based"

activation:
  triggers:
    - pattern: "use skill"
    - pattern: "skills available"
    - pattern: "which skill for"

suggestions:
  enabled: true
  max_skills: 5
  categories: ["Architecture", "Development", "DevOps", "Security"]
EOF
echo "✓ GitHub Copilot configuration created"

# Create Claude configuration
echo "⚙️  Creating Claude configuration..."
mkdir -p "$AGENTS_DIR/config/claude"
cat > "$AGENTS_DIR/config/claude/claude.yaml" << 'EOF'
# Claude Agent Configuration
# Reference: agents-global.yaml

version: "1.0"
agent: "claude"

skills:
  catalog: "${AGENTS_CONFIG_DIR}/agents-global.yaml"
  discovery_method: "semantic"

activation:
  triggers:
    - pattern: "skill"
    - pattern: "help with"
    - pattern: "how to"

suggestions:
  enabled: true
  max_skills: 5
  categories: ["Architecture", "Development", "DevOps"]
EOF
echo "✓ Claude configuration created"

# Create Cursor configuration
echo "⚙️  Creating Cursor configuration..."
mkdir -p "$AGENTS_DIR/config/cursor"
cat > "$AGENTS_DIR/config/cursor/cursor.yaml" << 'EOF'
# Cursor Editor Configuration
# Reference: agents-global.yaml

version: "1.0"
editor: "cursor"

skills:
  catalog: "${AGENTS_CONFIG_DIR}/agents-global.yaml"
  integration: "inline"

activation:
  shortcuts:
    - key: "ctrl+shift+a"
      action: "list_skills"
    - key: "ctrl+shift+s"
      action: "skill_search"
EOF
echo "✓ Cursor configuration created"

# Create quick reference guide
echo "📖 Creating quick reference guide..."
cat > "$AGENTS_DIR/README.md" << 'EOF'
# Global Agents & Skills Configuration

Enterprise-wide AI skills catalog and agent configuration.

## Quick Start

### 1. Load Environment
```bash
source ~/.agents/.agents.env
```

### 2. View Available Skills
```bash
yq eval '.agents[] | .id' ~/.agents/agents-global.yaml
```

### 3. Find Skills by Category
```bash
yq eval '.agents[] | select(.category[] | contains("Architecture")).name' ~/.agents/agents-global.yaml
```

### 4. Access Skill Documentation
```bash
cat $AIRULES_HOME/skills/.curated/senior-architect/SKILL.md
```

## Catalog Structure

```
~/.agents/
├── agents-global.yaml          # Global agent registry
├── .agents.env                  # Environment variables
├── skills/
│   └── airules/                 # Linked AIRules skills repository
└── config/
    ├── github-copilot/
    ├── claude/
    └── cursor/
```

## Agent Categories

- **Curated Skills** (39+): General-purpose, community-quality skills
- **System Skills** (70+): GuicedEE, JWebMP, ActivityMaster ecosystem
- **Platform Skills**: Terraform, Cloud, Infrastructure
- **Development Skills**: Testing, Code Quality, Git workflows
- **Enterprise Skills**: Authentication, Messaging, Persistence

## Common Tasks

### Find Skills by Category
```bash
# Architecture skills
grep -r "category:" ~/.agents/agents-global.yaml | grep Architecture

# All Terraform skills
grep -r "terraform" ~/.agents/agents-global.yaml
```

### View Skill Details
```bash
# List all ag
ent IDs
yq eval '.agents[].id' ~/.agents/agents-global.yaml

# Show specific skill details
yq eval '.agents[] | select(.id == "senior-architect")' ~/.agents/agents-global.yaml
```

### Integration with Tools

**GitHub Copilot**: Refer to skill by `id` in prompts
```
"Use the 'senior-architect' skill to design this system"
```

**Claude**: Ask about skills by name or category
```
"What skills do you have for testing?"
"Use the architecture skill for this design"
```

**Cursor**: Access via quick reference or inline hints

## Environment Variables

- `AIRULES_HOME`: Path to AIRules repository
- `GUICEDEE_HOME`: Path to GuicedEE project
- `JWEBMP_HOME`: Path to JWebMP project
- `ACTIVITYMASTER_HOME`: Path to ActivityMaster project
- `AGENTS_CONFIG_DIR`: Path to agents configuration (~/.agents)
- `AGENTS_SKILLS_PATH`: Path to linked skills catalog

## Updating Configuration

To update agents and skills:

```bash
# Update from AIRules repository
cd $AIRULES_HOME
git pull origin main

# Configuration auto-reloads if SKILLS_AUTO_RELOAD=true
```

## Support

For issues or questions, refer to:
- AIRules Repository: https://github.com/GuicedEE/ai-rules
- Skills Documentation: `$AIRULES_HOME/README.md`
- Skill Creator Guide: `$AIRULES_HOME/skills/.system/skill-creator/SKILL.md`
EOF
echo "✓ Quick reference guide created"

# Create index file for easy discovery
echo "📑 Creating skill index..."
cat > "$AGENTS_DIR/SKILLS-INDEX.txt" << 'EOF'
================================================================================
Global Skills Index
================================================================================

CURATED SKILLS (39+)
================================================================================

Automation & Orchestration:
  - dispatching-parallel-agents       Parallel agent orchestration
  - skill-adopter                      Adopt enterprise skills

Architecture & Design:
  - senior-architect                  System design and ADRs
  - senior-backend                    Backend engineering
  - senior-devops                     DevOps and infrastructure
  - information-architect             Information architecture
  - security-ownership-map            Security ownership mapping

Development & Code Quality:
  - code-reviewer                      Structured code review
  - systematic-debugging              Debugging methodology
  - test-driven-development           Red-green-refactor TDD
  - security-best-practices           Security review

Git & Version Control:
  - git-commit-helper                 Conventional commits
  - git-commit-signing                GPG commit signing
  - finishing-a-development-branch    Branch completion
  - using-git-worktrees               Git worktrees
  - changelog-generator               Changelog automation
  - gh-address-comments               GitHub PR comments
  - gh-fix-ci                         GitHub CI diagnosis

Infrastructure & Cloud:
  - arm-to-terraform-migration        ARM to Terraform
  - terraform-code-generator          Terraform code gen
  - terraform-doc-generator           Terraform docs
  - terraform-module-scaffold         Terraform modules
  - terraform-plan-analyzer           Plan analysis
  - terraform-project-generator       Project generation
  - terraform-resource-fetch          Resource fetching
  - terraform-security-scanner        Security scanning
  - terraform-state-manager           State management
  - terraform-validator               Configuration validation

Testing & QA:
  - playwright                         E2E test authoring
  - screenshot                         Visual comparison
  - senior-qa                          QA strategy

Security:
  - security-compliance               Compliance auditing
  - senior-secops                     Security operations

Design & UI:
  - figma                              Figma integration
  - aggrid                             AG Grid integration
  - senior-prompt-engineer            Prompt optimization

SYSTEM SKILLS (70+)
================================================================================

Platform & Tooling:
  - skill-creator                      Create new skills
  - skill-installer                    Install skills

Enterprise Resource Management:
  - activitymaster                     FSDM domain services
  - entityassist                       Reactive CRUD with Mutiny

GuicedEE Framework (25+ modules):
  - guicedee-auth                      OAuth2, JWT, LDAP auth
  - guicedee-cdi                       CDI dependency injection
  - guicedee-cerial                    Serialization framework
  - guicedee-client                    HTTP client integration
  - guicedee-cloud-app                 Cloud app scaffolding
  - guicedee-config                    MicroProfile Config
  - guicedee-creator                   Project scaffolding
  - guicedee-health                    Health checks
  - guicedee-inject                    Guice DI
  - guicedee-installer                 Module installation
  - guicedee-jpms-shade                JPMS shading
  - guicedee-jwt                       JWT tokens
  - guicedee-kafka                     Kafka messaging
  - guicedee-hazelcast                 Hazelcast clustering
  - guicedee-ibmmq                     IBM MQ messaging
  - guicedee-mail-client               SMTP mail
  - guicedee-metrics                   MicroProfile Metrics
  - guicedee-openapi                   OpenAPI/Swagger
  - guicedee-persistence               JPA/Hibernate
  - guicedee-rabbitmq                  RabbitMQ messaging
  - guicedee-rest                      JAX-RS endpoints
  - guicedee-rest-client               REST Client
  - guicedee-service-registry          Service discovery
  - guicedee-swagger-ui                Swagger UI
  - guicedee-telemetry                 OpenTelemetry
  - guicedee-vertx                     Vert.x reactive
  - guicedee-web                       Web configuration
  - guicedee-webservices               SOAP/XML services
  - guicedee-websockets                WebSocket integration

JWebMP Framework (44+ modules):
  Core:
    - jwebmp-core                      HTML, CSS, events
    - jwebmp-client                    Client module
    - jwebmp-vertx                     Vert.x integration
    - jwebmp-tsclient                  TypeScript generation

  Data & Analytics:
    - jwebmp-aggrid                    AG Grid community
    - jwebmp-aggrid-enterprise         AG Grid enterprise
    - jwebmp-agcharts                  AG Charts
    - jwebmp-agcharts-enterprise       AG Charts enterprise
    - jwebmp-chartjs                   Chart.js
    - jwebmp-c3                        C3 charting
    - jwebmp-d3                        D3.js visualization
    - jwebmp-datatables                DataTables
    - jwebmp-jqplot                    jqPlot charting
    - jwebmp-easy-pie-chart            Pie charts

  UI Frameworks:
    - jwebmp-angular                   Angular framework
    - jwebmp-angular-forms             Angular forms
    - jwebmp-angular-graphql           Apollo GraphQL
    - jwebmp-angular-material          Material Design
    - jwebmp-bootstrap                 Bootstrap CSS
    - jwebmp-webawesome                Web Awesome
    - jwebmp-webawesome-pro            Web Awesome pro

  Calendars & Scheduling:
    - jwebmp-fullcalendar              FullCalendar
    - jwebmp-fullcalendar-pro          FullCalendar pro

  Icons & Typography:
    - jwebmp-fontawesome               Font Awesome
    - jwebmp-fontawesome-pro           Font Awesome pro
    - jwebmp-material-design-icons     Material icons
    - jwebmp-material-icons            Material icons
    - jwebmp-glyph-icons               Glyph icons
    - jwebmp-themify-icons             Themify icons
    - jwebmp-weather-icons             Weather icons
    - jwebmp-skycons                   Dynamic weather

  Libraries & Effects:
    - jwebmp-jquery                    jQuery library
    - jwebmp-jquery-ui                 jQuery UI
    - jwebmp-easing                    Animations
    - jwebmp-local-storage             Browser storage
    - jwebmp-session-storage           Session storage
    - jwebmp-waves-effect              Material ripple

  Utilities:
    - jwebmp-markdown                  Markdown rendering
    - jwebmp-toastr                    Toast notifications
    - jwebmp-prism                     Syntax highlighting
    - jwebmp-prettify                  Code highlighting
    - jwebmp-globalize                 i18n support
    - jwebmp-waypoints                 Scroll detection
    - jwebmp-plus-as-tab               Tab navigation

================================================================================

QUICK LOOKUP BY TASK
================================================================================

System Architecture:
  → senior-architect, information-architect

Backend Development:
  → guicedee-rest, guicedee-persistence, senior-backend

Frontend Development:
  → jwebmp-core, jwebmp-angular, jwebmp-aggrid

Testing & Quality:
  → playwright, test-driven-development, senior-qa, code-reviewer

Security & Compliance:
  → security-best-practices, guicedee-auth, security-compliance

DevOps & Infrastructure:
  → senior-devops, terraform-*, guicedee-cloud-app

Messaging & Integration:
  → guicedee-kafka, guicedee-rabbitmq, guicedee-ibmmq

Data Grids & Visualization:
  → jwebmp-aggrid, jwebmp-agcharts, jwebmp-d3, jwebmp-chartjs

Git & Version Control:
  → git-commit-helper, finishing-a-development-branch, gh-fix-ci

API Development:
  → guicedee-rest, guicedee-openapi, api-integration-specialist

Debugging & Troubleshooting:
  → systematic-debugging, gh-fix-ci

================================================================================
EOF
echo "✓ Skill index created"

# Summary
echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "✅ Setup Complete!"
echo "═══════════════════════════════════════════════════════════════════"
echo ""
echo "📍 Installed to: $AGENTS_DIR"
echo ""
echo "Next steps:"
echo "  1. Source environment: source ~/.agents/.agents.env"
echo "  2. View skills: yq eval '.agents[].id' ~/.agents/agents-global.yaml"
echo "  3. Read docs: cat ~/.agents/README.md"
echo "  4. Reference: cat ~/.agents/SKILLS-INDEX.txt"
echo ""
echo "Configuration files:"
echo "  • ~/.agents/agents-global.yaml         (Master agent registry)"
echo "  • ~/.agents/.agents.env                 (Environment variables)"
echo "  • ~/.agents/config/*/                   (Tool-specific configs)"
echo "  • ~/.agents/README.md                   (Documentation)"
echo "  • ~/.agents/SKILLS-INDEX.txt            (Quick reference)"
echo ""
echo "Environment Variables:"
echo "  AIRULES_HOME=$AIRULES_HOME"
echo "  GUICEDEE_HOME=$GUICEDEE_HOME"
echo "  JWEBMP_HOME=$JWEBMP_HOME"
echo "  ACTIVITYMASTER_HOME=$ACTIVITYMASTER_HOME"
echo ""


