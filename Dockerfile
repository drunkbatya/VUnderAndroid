FROM eclipse-temurin:21-jdk-noble AS builder

ARG CMDLINE_TOOLS_VERSION=16111833
ARG PLATFORM=android-37.0
ARG BUILD_TOOLS=37.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

RUN apt update \
    && apt install -y --no-install-recommends git unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
RUN mkdir -p "$ANDROID_HOME/cmdline-tools"
RUN unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
RUN mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
RUN rm /tmp/cmdline-tools.zip
RUN yes | sdkmanager --licenses > /dev/null
RUN sdkmanager "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}" "platform-tools"
RUN chmod -R a+rwX "$ANDROID_HOME"

RUN git config --system --add safe.directory '*'
RUN mkdir -m 777 /gradle /home/build

ENV HOME=/home/build
ENV GRADLE_USER_HOME=/gradle

WORKDIR /workspace

FROM builder AS build
ADD . /workspace/
#RUN --mount=type=cache,target=/gradle ./gradlew --no-daemon assembleDebug
#RUN ls app/build/outputs/apk/debug/app-debug.apk

ENV ANDROID_KEYSTORE=/tmp/keystore
ENV ANDROID_KEY_ALIAS=vunder
RUN --mount=type=cache,target=/gradle \
    --mount=type=secret,id=keystore,target=/tmp/keystore \
    --mount=type=secret,id=keystorepass,env=ANDROID_KEYSTORE_PASSWORD \
    ./gradlew --no-daemon assembleRelease
RUN ls app/build/outputs/apk/release/app-release.apk

FROM scratch AS out
#COPY --from=build /workspace/app/build/outputs/apk/debug/app-debug.apk /
COPY --from=build /workspace/app/build/outputs/apk/release/app-release.apk /
