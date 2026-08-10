#! /bin/bash

# Build Jami daemon and client APK for Android
# Flags:
  # --test: build in test mode
  # --release: build in release mode
  # --daemon: Only build the daemon for the selected archs

# Fail fast: abort on the first failing command and on failures inside pipes
# so a broken SWIG/daemon/gradle step surfaces immediately instead of being
# masked by a later, more confusing error. -u is intentionally omitted because
# this script probes optional environment variables (DAEMON_DIR, ANDROID_ABI,
# RING_BUILD_FIREBASE) that are legitimately allowed to be unset.
set -eo pipefail

TEST=0
RELEASE=0
DAEMON_ONLY=0
for i in "${@}"; do
    case "$i" in
        test|--test)
        TEST=1
        ;;
        release|--release)
        RELEASE=1
        ;;
        daemon|--daemon)
        DAEMON_ONLY=1
        ;;
        *)
        ;;
    esac
done

export RELEASE

if [ -z "$DAEMON_DIR" ]; then
    DAEMON_DIR="$(pwd)/daemon"
    echo "DAEMON_DIR not provided attempting to find it in $DAEMON_DIR"
fi
if [ ! -d "$DAEMON_DIR" ]; then
    echo 'Daemon not found.'
    echo 'If you cloned the daemon in a custom location override DAEMON_DIR to point to it'
    echo "You can also use our meta repo which contains both:
          https://review.jami.net/admin/repos/jami-project"
    exit 1
fi
export DAEMON_DIR

JNIDIR=$DAEMON_DIR/bin/jni
ANDROID_TOPLEVEL_DIR="$(pwd)"
ANDROID_APP_DIR="${ANDROID_TOPLEVEL_DIR}/jami-android"
GRADLE_PROPERTIES=
if [ -n "$ANDROID_ABI" ]; then
    GRADLE_PROPERTIES="-Parchs=${ANDROID_ABI}"
fi

# Generate JNI interface
cd "$JNIDIR" || { echo "JNI directory not found at $JNIDIR (is the daemon built/checked out?)"; exit 1; }
if ! PACKAGEDIR=$ANDROID_APP_DIR/libjamiclient/src/main/java ./make-swig.sh; then
    echo "Failed to generate the SWIG JNI interface in $JNIDIR"
    exit 1
fi

if [[ $DAEMON_ONLY -eq 0 ]]; then
    if [ -z "$RING_BUILD_FIREBASE" ]; then
        echo "Building without Firebase support"
    else
        GRADLE_PROPERTIES="$GRADLE_PROPERTIES -PbuildFirebase"
        echo "Building with Firebase support"
    fi
    if [[ $RELEASE -eq 1 ]]; then
        echo "Building in release mode"
        cd "$ANDROID_APP_DIR"  && ./gradlew $GRADLE_PROPERTIES assembleRelease bundleRelease
    elif [[ $TEST -eq 1 ]]; then
        echo "Building in test mode"
        cd "$ANDROID_APP_DIR" && ./gradlew $GRADLE_PROPERTIES assembleDebug assembleAndroidTest
    else
        echo "Building in debug mode"
        echo "$GRADLE_PROPERTIES" assembleDebug
        cd "$ANDROID_APP_DIR" && ./gradlew $GRADLE_PROPERTIES assembleDebug
    fi
else
    echo "Building daemon only"
    cd "$ANDROID_APP_DIR" && ./gradlew $GRADLE_PROPERTIES buildCMakeDebug
fi
