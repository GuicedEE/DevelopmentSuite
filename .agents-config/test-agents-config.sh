#!/bin/bash
# Integration tests for global agents and skills configuration

set -e

AGENTS_DIR="${AGENTS_DIR:-$HOME/.agents}"
AIRULES_HOME="${AIRULES_HOME:-/home/gedmarc/java/devsuite/AIRules}"

echo "Testing Global Agents Configuration"
echo "===================================="
echo ""

# Test 1: Check configuration file exists
echo "▫ Test 1: Configuration file exists..."
if [ ! -f "$AGENTS_DIR/agents-global.yaml" ]; then
    echo "✗ FAIL: agents-global.yaml not found at $AGENTS_DIR"
    exit 1
fi
echo "✓ PASS"

# Test 2: Check environment file exists
echo "▫ Test 2: Environment file exists..."
if [ ! -f "$AGENTS_DIR/.agents.env" ]; then
    echo "✗ FAIL: .agents.env not found"
    exit 1
fi
echo "✓ PASS"

# Test 3: Check skills symlink
echo "▫ Test 3: Skills symlink exists..."
if [ ! -L "$AGENTS_DIR/skills/airules" ]; then
    echo "✗ FAIL: Symlink to AIRules not found"
    exit 1
fi
echo "✓ PASS"

# Test 4: Check AIRules path
echo "▫ Test 4: AIRules path accessible..."
if [ ! -d "$AIRULES_HOME/skills" ]; then
    echo "✗ FAIL: AIRules skills directory not found at $AIRULES_HOME"
    exit 1
fi
echo "✓ PASS"

# Test 5: Count agents in configuration
echo "▫ Test 5: Counting agents in configuration..."
if ! command -v yq &> /dev/null; then
    echo "⚠ WARN: yq not installed, skipping agent count"
else
    AGENT_COUNT=$(yq eval '.agents | length' "$AGENTS_DIR/agents-global.yaml" 2>/dev/null || echo "N/A")
    echo "  Found $AGENT_COUNT agents in configuration"
    echo "✓ PASS"
fi

# Test 6: Check for sample skills
echo "▫ Test 6: Sample skills accessible..."
SAMPLE_SKILLS=("senior-architect" "guicedee-rest" "jwebmp-core")
for skill in "${SAMPLE_SKILLS[@]}"; do
    if grep -q "id: \"$skill\"" "$AGENTS_DIR/agents-global.yaml"; then
        echo "  ✓ Found $skill"
    else
        echo "  ✗ Missing $skill"
        exit 1
    fi
done
echo "✓ PASS"

# Test 7: Check tool configurations
echo "▫ Test 7: Tool-specific configurations..."
for tool in github-copilot claude cursor; do
    if [ -f "$AGENTS_DIR/config/$tool/${tool}.yaml" ]; then
        echo "  ✓ $tool configuration found"
    else
        echo "  ✗ $tool configuration missing"
        exit 1
    fi
done
echo "✓ PASS"

# Test 8: Environment variables
echo "▫ Test 8: Environment variables..."
source "$AGENTS_DIR/.agents.env" 2>/dev/null
if [ -z "$AIRULES_HOME" ]; then
    echo "✗ FAIL: AIRULES_HOME not exported"
    exit 1
fi
echo "  AIRULES_HOME=$AIRULES_HOME"
echo "✓ PASS"

echo ""
echo "===================================="
echo "✅ All tests passed!"
echo "===================================="
echo ""
echo "Configuration is ready for use."
echo "Run: source ~/.agents/.agents.env"

