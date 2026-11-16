# Shortcuts for building and checking the app.
# Locally every command runs inside Docker. On CI (no Docker wrapper) use: make <target> RUN=
# App parameters (APP_NAME, APP_ID, APP_URL, APP_VERSION_NAME, APP_VERSION_CODE) are read from the
# environment, so:  APP_URL=https://example.com make debug

RUN    ?= docker compose run --rm dev
GRADLE  = $(RUN) ./gradlew

.DEFAULT_GOAL := help
.PHONY: help image check check-release test lint debug install release adb-devices clean shell

help: ## List the available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-12s %s\n", $$1, $$2}'

image: ## Build the Docker dev image (once, and after Dockerfile changes)
	docker compose build

check: ## Standard check: unit tests and lint
	$(GRADLE) testDebugUnitTest lint

check-release: ## Release lint and R8 shrinking (needs no signing)
	$(GRADLE) lintRelease minifyReleaseWithR8

test: ## Unit tests only
	$(GRADLE) testDebugUnitTest

lint: ## Android lint only
	$(GRADLE) lint

debug: ## Build the debug APK
	$(GRADLE) assembleDebug

install: ## Build and install the debug APK on a device/emulator
	$(GRADLE) assembleDebug installDebug

release: ## Build the signed release APK and AAB (needs signing, see README)
	$(GRADLE) assembleRelease bundleRelease

adb-devices: ## List devices seen by adb inside the container
	$(RUN) adb devices

clean: ## Remove build outputs
	$(GRADLE) clean

shell: ## Open a shell in the dev container
	$(RUN) bash
