# remimho

## Whiteboard Pong Game

A unique twist on classic ping-pong with drawing capabilities

### Features

- Classic Pong gameplay
- Draw obstacles
- Speed and line thickness
- Score tracking
- Pause/resume

### Controls

- **Arrow Up/Down**: Move player paddle
- **Right Mouse Button**: Draw on the canvas
- **Space**: Pause/resume game

### Technical Stack

- **Language**: Kotlin 2.2.20
- **JVM**: Java 23
- **UI Framework**: VueJS
- **Dependency Injection**: Quarkus 3.28.2
- **Build System**: Gradle (Kotlin DSL)
- **Native compilation**: GraalVM

### Prerequisites

- JDK 21+
- Gradle 8.0+

### How to Run

1. Clone the repository
2. Build with Gradle
3. Run the main class
4. ./gradlew build
5. ./gradlew quarkusDev

#### Windows

```batch
cd D:\rep\java\remimho
gradlew createFatJar
copy build\jpackage-libs\remimho-1.0.0-all.jar .
run-remimho.bat
```

#### Linux / macOS
```bash
cd ~/remimho
./gradlew createFatJar
cp build/jpackage-libs/remimho-1.0.0-all.jar .
chmod +x run-remimho.sh
./run-remimho.sh
```

### Native Build Configuration
#### 1. Required Software

- **Visual Studio Build Tools 2022** (required for Windows native compilation)
- **GraalVM 25** or later
- **Gradle** (handled by wrapper)
- **JDK 21** or later

#### 2. Visual Studio Setup

1. Install **Visual Studio Community 2022** or **Build Tools for Visual Studio 2022**
2. Select components:
    - **MSVC v143 - VS 2022 C++ x64/x86 build tools (latest)**
    - **Windows 11 SDK (10.0.22621.0)** or later
    - **CMake tools for Visual Studio**

####  3: Set Up Build the Native Application Environment

**CRITICAL:** You must use the x64 Native Tools Command Prompt for Visual Studio:

1. Open **x64 Native Tools Command Prompt for VS 2022**
    - Press `Win + R` → Search for "x64 Native Tools Command Prompt"
    - Or navigate via Start Menu → Visual Studio 2022 → x64 Native Tools Command Prompt

2. Alternatively, set up environment manually:
```cmd
call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
```

####  4: Navigate to Project Directory

```cmd
cd D:\path\to\your\project\remimho
```

####  5: Clean and Build

```cmd

gradle clean

gradle nativeBuild
```

### Screenshot

![img.png](img.png)

