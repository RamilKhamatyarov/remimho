# remimho

## Whiteboard Pong Game

A unique twist on classic ping-pong with drawing capabilities

### Features

- Classic Pong gameplay with AI opponent
- Interactive whiteboard to draw obstacles
- Adjustable game speed and line thickness
- Score tracking for both players
- Pause/resume functionality

### Controls

- **Arrow Up/Down**: Move player paddle
- **Right Mouse Button**: Draw on the canvas
- **Space**: Pause/resume game

### UI Controls

- **Reset Game**: Resets puck and paddle positions
- **Clear Drawings**: Removes all drawn lines
- **Pause/Resume**: Toggles game state
- **Speed Slider**: Adjusts game speed (0.5x-3.0x)
- **Thickness Slider**: Changes drawing line width (1-20px)

### Technical Stack

- **Language**: Kotlin 1.9.22
- **JVM**: Java 17
- **UI Framework**: JavaFX 17.0.8
- **Dependency Injection**: Quarkus 3.10.0
- **Build System**: Gradle (Kotlin DSL)

### Prerequisites

- JDK 17+
- Gradle 8.0+

### How to Run

1. Clone the repository
2. Build with Gradle
3. Run the main class `WhiteboardApplication`
4. ./gradlew build
5. ./gradlew run

### Screenshot

![img.png](img.png)

