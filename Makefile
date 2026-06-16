# 知识炼金炉 —— 本地编译 / 启动命令汇总
# 用法:make <目标>;make help 查看全部。
# 先决:JDK 21(后端)、Node 20+ 与 pnpm 10+(前端,corepack enable 即可)。

SERVER_DIR := server
FRONTEND_DIR := frontend

.DEFAULT_GOAL := help
.PHONY: help install \
        server server-build server-test \
        web web-build extension-build frontend-build frontend-typecheck \
        build clean

help: ## 显示本帮助
	@echo "知识炼金炉 本地命令:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

install: ## 安装前端依赖(后端走 Gradle Wrapper 按需自动拉取)
	cd $(FRONTEND_DIR) && pnpm install

# ---- 后端(Spring Boot)----
server: ## 启动后端开发服务(localhost:8080,本地用 H2)
	cd $(SERVER_DIR) && ./gradlew bootRun

server-build: ## 编译并打包后端 jar
	cd $(SERVER_DIR) && ./gradlew build

server-test: ## 跑后端测试
	cd $(SERVER_DIR) && ./gradlew test

# ---- 前端(pnpm monorepo)----
web: ## 启动 Web 阅读端开发服务(localhost:5173,/api 代理到 8080)
	cd $(FRONTEND_DIR) && pnpm dev:web

web-build: ## 构建 Web 阅读端(产物 frontend/apps/web/dist)
	cd $(FRONTEND_DIR) && pnpm build:web

extension-build: ## 构建浏览器插件(产物 frontend/apps/extension/dist,加载到浏览器)
	cd $(FRONTEND_DIR) && pnpm build:extension

frontend-build: ## 构建全部前端包与端
	cd $(FRONTEND_DIR) && pnpm -r build

frontend-typecheck: ## 前端类型检查
	cd $(FRONTEND_DIR) && pnpm -r typecheck

# ---- 组合 ----
build: server-build frontend-build ## 编译后端 + 全部前端

clean: ## 清理构建产物
	cd $(SERVER_DIR) && ./gradlew clean
	rm -rf $(FRONTEND_DIR)/apps/*/dist
