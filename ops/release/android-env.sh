# Source from the repository root: source ops/release/android-env.sh
# Local user-space installation; no credentials or signing keys.
export JAVA_HOME="$HOME/.local/share/expense-tracker-toolchain/jdk17"
export ANDROID_HOME="$HOME/.local/share/expense-tracker-toolchain/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
