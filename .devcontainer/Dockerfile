# Reproducible build env: same JDK + Android SDK on laptop and CI.
# Base pinned by digest (tag can be re-pushed; digest cannot).
FROM eclipse-temurin:17-jdk-noble@sha256:1bedb04434bd275ef072355e9caec2a22be5240fbe82ad51a59323f2bbcab4a6

RUN apt-get update \
    && apt-get install -y --no-install-recommends git unzip \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# sha256 computed locally; sha1 matched Google's repository2-3.xml.
ARG CMDLINE_TOOLS=16111833
ARG CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
RUN curl -fsSLo /tmp/tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip \
    && echo "${CMDLINE_TOOLS_SHA256}  /tmp/tools.zip" | sha256sum -c - \
    && mkdir -p $ANDROID_HOME/cmdline-tools \
    && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
    && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
    && rm /tmp/tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "platforms;android-37.0" "build-tools;36.0.0" \
    && rm -rf /root/.android \
    && chown -R ubuntu:ubuntu $ANDROID_HOME

# Non-root user with UID 1000 = your host UID, so files written to the
# bind-mounted repo stay owned by you, not root.
USER ubuntu
# Pre-create so the named volume mounted here inherits ubuntu ownership.
RUN mkdir -p /home/ubuntu/.gradle
