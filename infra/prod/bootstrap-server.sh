#!/usr/bin/env bash
# ==============================================================================
# Project Aegis — Oracle Cloud Always Free VM Bootstrap Script
# Tested on: Ubuntu 22.04 / 24.04 LTS (ARM64 / Ampere A1)
# ==============================================================================

set -euo pipefail

echo "==> [1/5] Updating system packages..."
sudo apt-get update -y
sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release ufw git

echo "==> [2/5] Installing Docker Engine and Compose Plugin..."
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> [3/5] Configuring user permissions for Docker..."
sudo usermod -aG docker "$USER"
sudo systemctl enable docker
sudo systemctl start docker

echo "==> [4/5] Configuring Firewall (UFW & Oracle Cloud iptables)..."
# Oracle Cloud Ubuntu images include strict default iptables rules that block port 80/443.
# Open inbound ports 22, 80, 443 in iptables:
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
sudo netfilter-persistent save || true

# UFW setup
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable

echo "==> [5/5] Creating application workspace at /opt/aegis..."
sudo mkdir -p /opt/aegis
sudo chown -R "$USER:$USER" /opt/aegis

echo "=============================================================================="
echo "  Oracle Cloud VM Bootstrap Complete!"
echo "  - Docker & Docker Compose: Installed and active"
echo "  - Ports Open: 22 (SSH), 80 (HTTP), 443 (HTTPS)"
echo "  - Workspace: /opt/aegis"
echo "=============================================================================="
