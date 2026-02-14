# Glenn - Health Monitoring Platform

<p align="center">
  <img src="https://img.shields.io/badge/version-0.0.1--SNAPSHOT-blue" alt="Version">
  <img src="https://img.shields.io/badge/java-21-orange" alt="Java Version">
  <img src="https://img.shields.io/badge/spring%20boot-3.3.2-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/reactive-r2dbc-purple" alt="Reactive">
</p>

<p align="center">
  <i>A reactive health monitoring platform named after Glenn, the compassionate healer from "Monster Girl Doctor"</i>
</p>

## 📋 Overview

Glenn is a reactive health monitoring platform that continuously checks the availability and performance of your applications. Named after the dedicated healer from the light novel "Monster Girl Doctor," Glenn watches over your digital ecosystem with the same care and attention.

### ✨ Key Features

- **🔄 Continuous Monitoring**: Automatically checks your applications at configurable intervals
- **📊 Real-time Dashboard**: Live view of all monitored applications with status indicators
- **⚡ Reactive Architecture**: Built with Spring WebFlux and R2DBC for non-blocking operations
- **📈 Response Time Tracking**: Monitor performance metrics over time
- **🏷️ Category Management**: Organize applications by categories with filtering
- **📱 Responsive Design**: Works on desktop and mobile devices
- **🔔 Status History**: Complete history of all health checks
- **⚙️ Configurable Status Codes**: Define which HTTP codes indicate a healthy application
- **🌐 Live Updates**: Server-Sent Events (SSE) for real-time dashboard updates

## 🏗️ Architecture

Glenn is built with a modern reactive stack:

- **Backend**: Spring Boot 3.3.2 with WebFlux
- **Database**: PostgreSQL with R2DBC for reactive data access
- **Migrations**: Liquibase for database schema management
- **Frontend**: Thymeleaf with Bootstrap 5 and Chart.js
- **Real-time**: Server-Sent Events (SSE) for live updates
- **Build Tool**: Maven

## 🚀 Getting Started

### Prerequisites

- Java 21 or higher
- PostgreSQL 14 or higher
- Maven 3.8+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/AZIRARM/Glenn.git
   cd glenn
   ```

2. **Configure the database**

   Create a PostgreSQL database:
   ```sql
   CREATE DATABASE glenn;
   ```

3. **Configure environment variables**

   Create a `.env` file at the project root:
   ```properties
   POSTGRES_URL=jdbc:postgresql://localhost:5432/glenn
   POSTGRES_REACTIVE_URL=r2dbc:postgresql://localhost:5432/glenn
   POSTGRES_USERNAME=postgres
   POSTGRES_PWD=yourpassword
   ```

4. **Build the application**
   ```bash
   mvn clean package
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   Or with environment variables:
   ```bash
   set -a; source .env; set +a; mvn spring-boot:run
   ```

6. **Access the dashboard**

   Open your browser and navigate to `http://localhost:1080`

## 📖 Usage Guide

### Adding an Application

1. Click the **"Add Application"** button on the dashboard
2. Fill in the application details:
    - **Name**: A descriptive name (e.g., "Production API")
    - **Category**: Optional category for filtering (e.g., "Backend", "Frontend")
    - **Description**: Optional description of the application's purpose
    - **URL**: The endpoint to monitor (must start with http:// or https://)
3. Select which HTTP status codes indicate a healthy application
    - Use preset buttons for quick selection (2xx Success, 3xx Redirect)
    - Default selection includes common success codes
4. Click **"Add Application"** to start monitoring

### Dashboard Features

- **Status Cards**: Visual cards showing application name, current status, response time, and HTTP code
- **Statistics Bar**: Overview of total apps, healthy/unhealthy counts, and global uptime
- **Category Filtering**: Click on category badges to filter applications by category
- **Status Filtering**: Filter by all, healthy, or unhealthy applications
- **Live Updates**: Dashboard updates automatically when new health checks are performed
- **Quick Actions**: Pause/resume monitoring or delete applications directly from the dashboard

### Application Details

Click the **"Details"** button on any application card to access:
- Complete application information
- Uptime statistics (24h, 7d, 30d)
- Response time chart
- Complete status history
- Edit application configuration

### Editing an Application

Click the pencil icon (✏️) on an application card or in the details view to:
- Modify application name, category, or description
- Update the monitored URL
- Change accepted status codes

## ⚙️ Configuration

### Application Properties

| Property | Description | Default |
|----------|-------------|---------|
| `monitoring.interval` | Interval between health checks (ms) | 30000 |
| `monitoring.timeout` | HTTP request timeout (ms) | 5000 |
| `server.port` | Web server port | 1080 |

### Monitoring Configuration

The monitoring service automatically:
- Checks all active applications at the configured interval
- Records status, response time, and any errors
- Updates the dashboard in real-time via SSE
- Maintains complete history for uptime calculations

## 🛠️ Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Core Framework** | Spring Boot 3.3.2 | Application foundation |
| **Reactive Web** | Spring WebFlux | Non-blocking HTTP requests |
| **Database** | PostgreSQL | Data persistence |
| **Reactive DB** | R2DBC | Non-blocking database access |
| **Migrations** | Liquibase | Database version control |
| **Template Engine** | Thymeleaf | Server-side HTML rendering |
| **Frontend** | Bootstrap 5 + Chart.js | UI components and charts |
| **Real-time** | SSE (Server-Sent Events) | Live dashboard updates |
| **Build Tool** | Maven | Dependency management |

## 📁 Project Structure

```
glenn/
├── src/
│   ├── main/
│   │   ├── java/org/azirar/glenn/
│   │   │   ├── endpoints/        # Web controllers
│   │   │   ├── handlers/         # Business logic
│   │   │   ├── models/           # Domain entities
│   │   │   └── repositories/     # Data access
│   │   └── resources/
│   │       ├── db/changelog/     # Liquibase migrations
│   │       ├── static/           # CSS, JS, images
│   │       ├── templates/        # Thymeleaf templates
│   │       └── application.yml   # Application config
│   └── test/                     # Unit tests
├── pom.xml                        # Maven configuration
└── README.md                      # This file
```

## 🌟 Why "Glenn"?

Glenn is named after the compassionate healer from the light novel and anime "Monster Girl Doctor." Just as Glenn treats and cares for monster girls with dedication and expertise, this platform monitors and cares for your applications, ensuring they stay healthy and performant.

## 📊 Roadmap

- [x] Basic health monitoring
- [x] Real-time dashboard with SSE
- [x] Category filtering
- [x] Response time tracking
- [x] Application editing
- [ ] Email/Slack notifications
- [ ] Advanced uptime SLAs
- [ ] Custom monitoring intervals per application
- [ ] API for external integrations
- [ ] Prometheus metrics export
- [ ] Grafana dashboards

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the GNU GENERAL PUBLIC LICENSE - see the LICENSE file for details.

## 👏 Acknowledgments

- Inspired by the need for simple, reactive health monitoring
- Named after Glenn Litbeit from "Monster Girl Doctor" (Kensuke Koba)
- Built with Spring Boot and the amazing Java ecosystem



### Glenn vs Other Monitoring Solutions

#### Why Glenn Stands Out

| Feature | Glenn | Uptime Kuma | Gatus | UptimeRobot |
| :--- | :--- | :--- | :--- | :--- |
| **Modern Tech Stack** | ✅ Spring WebFlux/R2DBC (Reactive) | ❌ Node.js (Blocking) | ✅ Go (Efficient) | ❌ Legacy PHP |
| **Reactive Architecture** | ✅ Non-blocking, High Concurrency | ❌ Synchronous I/O | ✅ Lightweight | ❌ Traditional |
| **Database** | ✅ PostgreSQL (Enterprise-grade) | ❌ SQLite (File-based) | ❌ No persistence | ✅ Cloud |
| **Stream Monitoring** | ✅ PostgreSQL, MySQL, MongoDB, SSH, Redis, RabbitMQ | ❌ HTTP/HTTPS only | ❌ HTTP/HTTPS only | ❌ HTTP/HTTPS only |
| **Real-time Updates** | ✅ SSE (Server-Sent Events) | ✅ WebSockets | ❌ Polling | ❌ Polling |
| **History Retention** | ✅ Full SQL database (years) | ✅ File-based (limited) | ❌ In-memory only | ⚠️ Limited (paid) |
| **Self-hosted** | ✅ Docker, easy setup | ✅ Docker | ✅ Single binary | ❌ SaaS only |
| **Category Filtering** | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| **Response Time Tracking** | ✅ Detailed per check | ✅ Basic | ✅ Basic | ✅ Basic |

#### 🎯 Perfect for Java Developers

| Aspect | Glenn | Others |
| :--- | :--- | :--- |
| **Tech Stack Familiarity** | ✅ Java 21, Spring Ecosystem | ❌ Node.js, Go, PHP |
| **Code Customization** | ✅ Full Java control | ❌ Limited by design |
| **Integration** | ✅ Easy with Java services | ⚠️ Requires adapters |
| **Learning Curve** | ✅ Natural for Java devs | ⚠️ New languages/frameworks |

#### 📊 Performance & Scalability

| Metric | Glenn | Uptime Kuma | Gatus | UptimeRobot |
| :--- | :--- | :--- | :--- | :--- |
| **Architecture** | Reactive, non-blocking | Event-loop (Node.js) | Concurrent (Go) | Traditional |
| **Database** | PostgreSQL connection pool | SQLite (single-threaded) | None | Cloud |
| **Concurrent checks** | ⚡ Excellent | ⚠️ Moderate | ✅ Good | ✅ Excellent |
| **Memory usage** | ~150-250MB | ~50-100MB | ~10-20MB | N/A |

#### 🚀 What Makes Glenn Unique

##### 1. **Stream Protocol Support**
Unlike competitors limited to HTTP/HTTPS, Glenn monitors:
- PostgreSQL databases
- MySQL/MariaDB
- MongoDB
- SSH services
- Redis instances
- RabbitMQ/AMQP

##### 2. **Enterprise-Grade Database**
- PostgreSQL persistence means:
   - Years of history
   - Complex queries
   - Data integrity
   - Backup/restore capabilities

##### 3. **Reactive from the Core**
- Spring WebFlux + R2DBC = truly non-blocking
- Handles thousands of concurrent checks
- Efficient resource usage
- Perfect for microservices environments

##### 4. **Developer-Friendly**
- Pure Java - easy to extend
- Familiar Spring ecosystem
- Clean, maintainable codebase
- Perfect for teams already using Java

#### 📈 Ideal Use Cases

| Scenario | Glenn | Other Solutions |
| :--- | :--- | :--- |
| **Java/Spring ecosystem** | ✅ Perfect fit | ⚠️ Foreign stack |
| **Mixed protocols (HTTP + DB)** | ✅ One tool for everything | ❌ Need multiple tools |
| **Long-term historical analysis** | ✅ SQL-powered | ⚠️ Limited retention |
| **High-frequency checks** | ✅ Reactive architecture | ⚠️ May struggle |
| **Self-hosted with PostgreSQL** | ✅ Native support | ⚠️ SQLite limitations |

#### 🎓 Comparison Summary

| Aspect | Glenn's Advantage |
| :--- | :--- |
| **Technology** | Most modern stack (Spring WebFlux + R2DBC) |
| **Protocol Support** | Widest (HTTP + Databases + SSH + Message queues) |
| **Data Persistence** | Enterprise-grade (PostgreSQL) |
| **Developer Experience** | Best for Java/Spring developers |
| **Architecture** | Truly reactive, non-blocking |
| **Customization** | Full control via Java code |

#### 💡 When to Choose Glenn

- ✅ You're a Java/Spring developer
- ✅ You need to monitor databases and services, not just HTTP
- ✅ You want long-term history in PostgreSQL
- ✅ You prefer self-hosted solutions
- ✅ You need a lightweight but powerful monitoring tool

#### 🔮 Future Potential

| Coming Soon | Status |
| :--- | :--- |
| **Notifications** (Slack, Discord, Email) | 🚧 Planned |
| **Public Status Pages** | 🚧 Planned |
| **SSL Certificate Monitoring** | 🚧 Planned |
| **REST API** | 🚧 Planned |
| **Authentication & Multi-user** | 🚧 Planned |


**Glenn isn't just another uptime monitor. It's a modern, reactive health monitoring platform built for Java developers who need more than just HTTP checks.**
## 📧 Contact

Project Link: [Glenn on Github](https://github.com/azirarm/glenn)

