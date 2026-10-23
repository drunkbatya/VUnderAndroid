FROM eclipse-temurin:21-jdk-noble

ARG CMDLINE_TOOLS_VERSION=16111833
ARG PLATFORM=android-37.0
ARG BUILD_TOOLS=37.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

RUN apt-get update \
    && apt-get install -y --no-install-recommends git unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}" "platform-tools" \
    && chmod -R a+rwX "$ANDROID_HOME"

RUN git config --system --add safe.directory '*'

WORKDIR /workspace
