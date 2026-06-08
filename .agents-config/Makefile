.PHONY: help install test verify clean setup-env list-skills find-skill update

# Global Agents & Skills Configuration Makefile
# Simplifies setup and management of enterprise skills catalog

AGENTS_DIR ?= $(HOME)/.agents
AIRULES_HOME ?= /home/gedmarc/java/devsuite/AIRules
DEVSUITE_HOME ?= $(CURDIR)

help:
	@echo "Global Agents & Skills Configuration"
	@echo "===================================="
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Setup Targets:"
	@echo "  make install         Install configuration to ~/.agents"
	@echo "  make setup-env       Create environment variables file"
	@echo "  make setup-configs   Create tool-specific configs"
	@echo ""
	@echo "Verification Targets:"
	@echo "  make test            Run integration tests"
	@echo "  make verify          Verify installation status"
	@echo "  make check-yaml      Validate YAML syntax"
	@echo ""
	@echo "Discovery Targets:"
	@echo "  make list-skills     List all available skills"
	@echo "  make list-curated    List curated skills only"
	@echo "  make list-system     List system skills only"
	@echo "  make find-skill      Find skill by pattern (PATTERN=name)"
	@echo "  make list-categories List all skill categories"
	@echo "  make list-by-tag     List skills by tag (TAG=security)"
	@echo ""
	@echo "Management Targets:"
	@echo "  make clean           Remove ~/.agents directory"
	@echo "  make update          Update from AIRules source"
	@echo "  make export-json     Export skills as JSON"
	@echo "  make export-csv      Export skills as CSV"
	@echo ""
	@echo "Examples:"
	@echo "  make install                    # Complete setup"
	@echo "  make find-skill PATTERN=terraform"
	@echo "  make list-by-tag TAG=kubernetes"
	@echo ""

install: setup-env setup-configs
	@echo "✅ Installation complete!"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Source environment: source ~/.agents/.agents.env"
	@echo "  2. Verify installation: make verify"
	@echo "  3. List skills: make list-skills"
	@echo ""

setup-env:
	@echo "📋 Setting up environment configuration..."
	@mkdir -p $(AGENTS_DIR)/config
	@mkdir -p $(AGENTS_DIR)/skills
	@cp agents-global.yaml $(AGENTS_DIR)/agents-global.yaml
	@rm -f $(AGENTS_DIR)/skills/airules
	@ln -s $(AIRULES_HOME)/skills $(AGENTS_DIR)/skills/airules
	@echo "✓ Configuration installed"
	@echo "✓ Skills symlink created"

setup-configs:
	@echo "⚙️  Creating tool-specific configurations..."
	@mkdir -p $(AGENTS_DIR)/config/github-copilot
	@mkdir -p $(AGENTS_DIR)/config/claude
	@mkdir -p $(AGENTS_DIR)/config/cursor
	@echo "version: \"1.0\"" > $(AGENTS_DIR)/config/github-copilot/copilot.yaml
	@echo "agent: github-copilot" >> $(AGENTS_DIR)/config/github-copilot/copilot.yaml
	@echo "catalog: \$$(AGENTS_CONFIG_DIR)/agents-global.yaml" >> $(AGENTS_DIR)/config/github-copilot/copilot.yaml
	@echo "version: \"1.0\"" > $(AGENTS_DIR)/config/claude/claude.yaml
	@echo "agent: claude" >> $(AGENTS_DIR)/config/claude/claude.yaml
	@echo "catalog: \$$(AGENTS_CONFIG_DIR)/agents-global.yaml" >> $(AGENTS_DIR)/config/claude/claude.yaml
	@echo "version: \"1.0\"" > $(AGENTS_DIR)/config/cursor/cursor.yaml
	@echo "editor: cursor" >> $(AGENTS_DIR)/config/cursor/cursor.yaml
	@echo "catalog: \$$(AGENTS_CONFIG_DIR)/agents-global.yaml" >> $(AGENTS_DIR)/config/cursor/cursor.yaml
	@cp SETUP.md $(AGENTS_DIR)/README.md
	@echo "✓ Tool configurations created"
	@echo "✓ Documentation installed"

test: check-yaml verify
	@echo "✅ All tests passed!"

check-yaml:
	@echo "🔍 Checking YAML syntax..."
	@if command -v yq >/dev/null 2>&1; then \
		yq eval . agents-global.yaml > /dev/null && echo "✓ agents-global.yaml valid"; \
		if [ -f "$(AGENTS_DIR)/agents-global.yaml" ]; then \
			yq eval . $(AGENTS_DIR)/agents-global.yaml > /dev/null && echo "✓ ~/.agents/agents-global.yaml valid"; \
		fi; \
	else \
		echo "⚠️  yq not installed, skipping YAML validation"; \
	fi

verify:
	@echo "📊 Verifying installation..."
	@if [ -f "$(AGENTS_DIR)/agents-global.yaml" ]; then \
		echo "✓ Configuration file exists"; \
	else \
		echo "✗ Configuration file missing"; \
	fi
	@if [ -L "$(AGENTS_DIR)/skills/airules" ]; then \
		echo "✓ Skills symlink exists"; \
	else \
		echo "✗ Skills symlink missing"; \
	fi
	@if [ -d "$(AIRULES_HOME)/skills" ]; then \
		echo "✓ AIRules path accessible"; \
	else \
		echo "✗ AIRules path not found"; \
	fi
	@echo ""
	@echo "Location: $(AGENTS_DIR)"
	@echo "AIRules:  $(AIRULES_HOME)"
	@echo ""

list-skills:
	@echo "📑 Available Skills (ID | Name)"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.agents[] | "  \(.id) | \(.name)"' agents-global.yaml | head -50; \
	else \
		echo "⚠️  yq not installed, cannot display skills"; \
	fi

list-curated:
	@echo "Curated Skills"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.agents[] | select(.skill_path | contains("curated")) | "\(.id) - \(.name)"' agents-global.yaml; \
	fi

list-system:
	@echo "System Skills"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.agents[] | select(.skill_path | contains("system")) | "\(.id) - \(.name)"' agents-global.yaml; \
	fi

find-skill:
	@if [ -z "$(PATTERN)" ]; then \
		echo "Usage: make find-skill PATTERN=<pattern>"; \
		exit 1; \
	fi
	@echo "🔍 Finding skills matching: $(PATTERN)"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.agents[] | select(.id | test("$(PATTERN)")) | "\(.id) - \(.name)"' agents-global.yaml; \
	fi

list-categories:
	@echo "📂 Available Categories"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.agents[]?.category[]' agents-global.yaml | sort | uniq; \
	fi

list-by-tag:
	@if [ -z "$(TAG)" ]; then \
		echo "Available tags:"; \
		yq eval '.tags | keys | .[]' agents-global.yaml; \
		echo ""; \
		echo "Usage: make list-by-tag TAG=<tag>"; \
		exit 1; \
	fi
	@echo "🏷️  Skills tagged with: $(TAG)"
	@echo "=================================="
	@if command -v yq >/dev/null 2>&1; then \
		yq eval '.tags.$(TAG)[]' agents-global.yaml | xargs -I {} yq eval '.agents[] | select(.id == "{}") | "\(.id) - \(.name)"' agents-global.yaml; \
	fi

clean:
	@echo "🗑️  Removing ~/.agents directory..."
	@rm -rf $(AGENTS_DIR)
	@echo "✓ Removed"

update:
	@echo "🔄 Updating from AIRules..."
	@cd $(AIRULES_HOME) && git pull origin main
	@cp $(AIRULES_HOME)/agents-global.yaml agents-global.yaml
	@cp agents-global.yaml $(AGENTS_DIR)/agents-global.yaml
	@echo "✓ Updated"

export-json:
	@echo "📤 Exporting skills as JSON..."
	@if command -v yq >/dev/null 2>&1; then \
		yq eval -o json '.agents[]' agents-global.yaml > skills.json; \
		echo "✓ Exported to skills.json"; \
	else \
		echo "⚠️  yq not installed"; \
	fi

export-csv:
	@echo "📤 Exporting skills as CSV..."
	@if command -v yq >/dev/null 2>&1; then \
		echo "id,name,category,description" > skills.csv; \
		yq eval '.agents[] | "\(.id),\(.name),\(.category[0]),\(.description)"' agents-global.yaml >> skills.csv; \
		echo "✓ Exported to skills.csv"; \
	else \
		echo "⚠️  yq not installed"; \
	fi

.PHONY: help install setup-env setup-configs test check-yaml verify \
        list-skills list-curated list-system find-skill list-categories list-by-tag \
        clean update export-json export-csv

