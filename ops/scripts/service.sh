#!/usr/bin/env bash
# ops/scripts/service.sh —— cnotes 服务生命周期控制(systemd 包装)。
# 用法: ./service.sh {start|stop|restart|status|logs}
set -euo pipefail
UNIT=cnotes

case "${1:-}" in
  start)
    sudo systemctl start "$UNIT"
    sleep 2
    sudo systemctl --no-pager -l status "$UNIT"
    ;;
  stop)
    sudo systemctl stop "$UNIT"
    ;;
  restart)
    sudo systemctl restart "$UNIT"
    sleep 2
    sudo systemctl --no-pager -l status "$UNIT"
    ;;
  status)
    sudo systemctl --no-pager -l status "$UNIT"
    ;;
  logs)
    sudo journalctl -u "$UNIT" -f --since "10 min ago"
    ;;
  *)
    echo "用法: $0 {start|stop|restart|status|logs}"
    exit 1
    ;;
esac
