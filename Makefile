JAR_NAME=link-analysis.jar

.DEFAULT_GOAL := help

TODAY=`date +'%Y-%m-%d__%H_%M_%S'`

.PHONY: compile
compile: ## Compile source code
	mvn compile

.PHONY: conf-mvnw
conf-mvnw: ## Set mvnw configuration
	chmod +x mvnw

.PHONY: dev
dev: conf-mvnw ## Run application in dev mode
	./mvnw quarkus:dev

.PHONY: dev-build
dev-build: conf-mvnw ## Build and then run application in dev mode
	./mvnw compile quarkus:dev

.PHONY: build
build: ## Build jar
	mvn package

.PHONY: build-native
build-native: conf-mvnw ## Build a native executable
	./mvnw package -Dnative

.PHONY: install
install: ## Install Maven dependencies
	mvn install 

.PHONY: reinstall
reinstall: ## Regenerate JavaCPP and rebuild project
	mvn clean install

.PHONY: deploy
deploy: ## Use maven to deploy artifacts to Azure Artifacts repository
	mvn deploy

.PHONY: test
test: ## Run Junit tests
	mvn clean test

.PHONY: clear-cache
clear-cache: ## Clear Maven cache
	mvn dependency:purge-local-repository

.PHONY: tester
tester: # make test
	echo "test-$(TODAY)"

.PHONY: help
help: ## Show this help.
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'
 