FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    wget unzip openjdk-17-jdk \
    build-essential cmake \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# 安装 Android SDK
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip commandlinetools-linux-*.zip && \
    mv cmdline-tools latest && \
    mkdir -p cmdline-tools && \
    mv latest cmdline-tools/ && \
    rm commandlinetools-linux-*.zip

RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" \
               "platforms;android-34" \
               "build-tools;34.0.0" \
               "ndk;26.1.10909125" \
               "cmake;3.22.1"

WORKDIR /workspace
COPY . .

RUN chmod +x gradlew

CMD ["./gradlew", "clean", "assembleDebug"]
