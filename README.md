# 🎥 V-Meet - Video Meeting Application

A real-time video conferencing application built with Spring Boot and WebRTC.

## ✨ Features

- 🎥 Real-time video and audio calls
- 💬 Live chat messaging
- 🖥️ Screen sharing
- 👥 Participant management
- 🔐 User authentication
- 📱 Responsive design

## 🛠️ Tech Stack

**Backend:** Java 21, Spring Boot 3.5.6, PostgreSQL, WebSocket  
**Frontend:** Thymeleaf, Bootstrap 5, WebRTC, JavaScript

## 📦 Prerequisites

- Java 21
- PostgreSQL 12+
- Maven

## 🚀 Quick Start

### 1. Clone Repository
```bash
git clone https://github.com/yourusername/goconnect.git
cd goconnect
```

### 2. Setup Database
```bash
# Login to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE GoConnect;
```

### 3. Configure Application
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/V-Meet
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
```

### 4. Run Application
```bash
# Windows
mvnw.cmd spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

Application starts at: `http://localhost:8080`

## 📖 Usage

### Create Meeting
1. Sign up/Sign in at `http://localhost:8080`
2. Click "Create Meeting"
3. Share the meeting code with participants

### Join Meeting
1. Go to "Join Meeting"
2. Enter meeting code and your name
3. Click "Join"

### Meeting Controls
- 🎤 Mute/Unmute microphone
- 🎥 Turn camera on/off
- 🖥️ Share screen
- 💬 Open chat
- 👥 View participants
- 🔚 Leave mueeting

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/dharaneesh/video_meeting/
│   │   ├── config/          # WebSocket & Security config
│   │   ├── controller/      # REST & WebSocket controllers
│   │   ├── model/           # Entity classes
│   │   ├── repository/      # Database repositories
│   │   └── service/         # Business logic
│   └── resources/
│       ├── static/
│       │   ├── css/         # Stylesheets
│       │   └── js/          # JavaScript (WebRTC, Chat)
│       └── templates/       # HTML pages
└── test/                    # Test files
```

## Common Issues

**Database Connection Error:**
- Ensure PostgreSQL is running
- Check credentials in `application.properties`

**Port Already in Use:**
- Change port: `server.port=8081` in `application.properties`

**Camera/Microphone Not Working:**
- Grant browser permissions
- Use Chrome, Firefox, or Edge
