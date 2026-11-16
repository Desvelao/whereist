# Pinned by digest so the toolchain does not change under us; bump it on purpose (docker pull, then docker image inspect).
FROM eclipse-temurin:17-jdk-jammy@sha256:76751c16a2a07ec0960e659a2603491f8c79d95592173f6c0df8bf8ebab257cc
ARG CMDLINE_TOOLS_BUILD=15859902
# sha256 of commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip: change both together.
ARG CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_USER_HOME=/gradle
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends curl unzip git \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p $ANDROID_HOME/cmdline-tools /gradle && chmod 777 /gradle \
 && curl -fsSL -o /tmp/clt.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip \
 && echo "${CMDLINE_TOOLS_SHA256}  /tmp/clt.zip" | sha256sum -c - \
 && unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/clt.zip
RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" \
 && chmod -R a+rwX $ANDROID_HOME
WORKDIR /workspace
