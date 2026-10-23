IMAGE        := vunder-android-build
GRADLE_CACHE := vunder-gradle-cache
APK_DIR      := app/build/outputs/apk

DOCKER_RUN := docker run --rm \
	-u $(shell id -u):$(shell id -g) \
	-e GRADLE_USER_HOME=/gradle \
	-e VUNDER_KEYSTORE -e VUNDER_KEYSTORE_PASSWORD -e VUNDER_KEY_ALIAS -e VUNDER_KEY_PASSWORD \
	-v $(GRADLE_CACHE):/gradle \
	-v $(CURDIR):/workspace \
	$(IMAGE)

.PHONY: all image debug release test lint shell clean

all: debug

image:
	docker build -t $(IMAGE) docker

debug: image
	$(DOCKER_RUN) ./gradlew --no-daemon assembleDebug
	@ls -la $(APK_DIR)/debug/*.apk

release: image
	$(DOCKER_RUN) ./gradlew --no-daemon assembleRelease
	@ls -la $(APK_DIR)/release/*.apk

test: image
	$(DOCKER_RUN) ./gradlew --no-daemon testDebugUnitTest

lint: image
	$(DOCKER_RUN) ./gradlew --no-daemon lintDebug

shell: image
	docker run --rm -it -u $(shell id -u):$(shell id -g) -e GRADLE_USER_HOME=/gradle -v $(GRADLE_CACHE):/gradle -v $(CURDIR):/workspace $(IMAGE) bash

clean:
	rm -rf build app/build .gradle .kotlin generated
