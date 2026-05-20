# Nacos Stress Test Tool

针对 **Nacos 3.1.1** 配置中心的读写压测工具，支持多线程并发、指标采集、HTML 可视化报告。

## 功能特性

- ✅ 支持 Nacos 配置的**并发读写压测**
- ✅ 可配置读写比例（如 70% 读 / 30% 写）
- ✅ HdrHistogram 高精度延迟统计（P50/P90/P95/P99/Max）
- ✅ 实时 TPS、错误率监控
- ✅ 生成 **HTML 可视化报告**（Chart.js 图表）
- ✅ 支持预热期、线程渐进启动（ramp-up）
- ✅ 可配置配置项数量、内容大小、测试时长
- ✅ 优雅关闭（Ctrl+C 安全停止）

## 环境要求

| 组件 | 版本要求 |
|------|---------|
| JDK | 11 或更高 |
| Maven | 3.6+ （仅编译时需要） |
| Nacos Server | 3.1.1 （兼容 2.x） |

## 快速开始

### 1. 编译打包

```bash
git clone https://github.com/f1shb0t/nacos-stresstest-her.git
cd nacos-stresstest-her
mvn clean package -DskipTests
```

编译成功后会在 `target/` 目录生成 fat jar：`nacos-stresstest-1.0.0.jar`

### 2. 基本运行

```bash
# 使用脚本（推荐）
./scripts/run.sh -s 192.168.1.100:8848

# 或直接运行 jar
java -jar target/nacos-stresstest-1.0.0.jar -s 192.168.1.100:8848
```

### 3. 完整参数示例

```bash
java -jar target/nacos-stresstest-1.0.0.jar \
  --server 192.168.1.100:8848 \
  --namespace public \
  --username nacos \
  --password nacos \
  --group STRESS_TEST_GROUP \
  --configs 200 \
  --threads 20 \
  --duration 120 \
  --read-ratio 70 \
  --warmup 10 \
  --content-size 2048 \
  --ramp-up 5 \
  --report-interval 5 \
  --client-pool 50 \
  --think-time 100 \
  --max-think-time 500 \
  --output report.html
```

### 4. 高并发仿真示例（模拟3000台机器）

```bash
java -Xmx8g -Xss512k -XX:+UseG1GC \
  -jar target/nacos-stresstest-1.0.0.jar \
  -s nacos-cluster.internal:8848 \
  -t 3000 \
  --client-pool 200 \
  --configs 500 \
  -d 300 \
  -r 80 \
  --think-time 100 \
  --max-think-time 500 \
  --warmup 15 \
  --ramp-up 30 \
  --content-size 2048 \
  -o high-concurrency-report.html
```

## 参数说明

### 基础参数

| 参数 | 短参数 | 说明 | 默认值 |
|------|--------|------|--------|
| `--server` | `-s` | Nacos 服务器地址 | `127.0.0.1:8848` |
| `--namespace` | `-n` | 命名空间 | `public` |
| `--username` | `-u` | 用户名 | `nacos` |
| `--password` | `-p` | 密码 | `nacos` |
| `--group` | `-g` | 配置分组 | `STRESS_TEST_GROUP` |
| `--configs` | `-c` | 配置项数量 | `100` |
| `--threads` | `-t` | 并发线程数 | `10` |
| `--duration` | `-d` | 压测持续时间（秒） | `60` |
| `--read-ratio` | `-r` | 读操作比例（0-100） | `70` |
| `--warmup` | `-w` | 预热时间（秒） | `5` |
| `--content-size` | | 配置内容大小（字节） | `1024` |
| `--ramp-up` | | 线程渐进启动时间（秒） | `0` |
| `--report-interval` | | 报告采样间隔（秒） | `5` |
| `--output` | `-o` | 报告输出路径 | `stress-test-report.html` |
| `--help` | `-h` | 打印帮助信息 | - |

### 仿真参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--client-pool` | 模拟独立客户端数（独立连接） | `50` |
| `--think-time` | 操作间最小思考时间（毫秒） | `100` |
| `--max-think-time` | 操作间最大思考时间（毫秒） | `500` |
| `--no-jitter` | 禁用随机抖动（默认开启） | 开启 |
| `--conn-timeout` | 连接超时（毫秒） | `5000` |
| `--req-timeout` | 请求超时（毫秒） | `3000` |

> **仿真说明**：`--client-pool 200 --threads 3000` 表示模拟 200 台独立机器，每台机器约 15 个并发线程访问配置。读操作遵循 Zipf 分布（20% 热点配置承受 80% 读流量），更贴近生产环境。

## 压测流程

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  参数解析    │───▶│  配置预填充  │───▶│  预热阶段    │───▶│  正式压测    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                                                                │
                                                                ▼
                   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
                   │  打开报告    │◀───│  生成报告    │◀───│  汇总指标    │
                   └─────────────┘    └─────────────┘    └─────────────┘
```

1. **参数解析** - 读取命令行参数或使用默认值
2. **配置预填充** - 向 Nacos 写入指定数量的配置项作为测试数据
3. **预热阶段** - 运行 warmup 秒，此期间的数据不计入统计
4. **正式压测** - 多线程并发执行读写操作，实时采集指标
5. **汇总指标** - 计算 TPS、延迟百分位、错误率等
6. **生成报告** - 输出 HTML 可视化报告

## 报告说明

生成的 HTML 报告包含以下内容：

### 概览面板
- 测试配置摘要（服务器、线程数、持续时间等）
- 总操作数、成功率、平均 TPS

### 图表
1. **TPS 时间序列图** - 读/写 TPS 随时间变化曲线
2. **延迟百分位图** - 读/写 P99 延迟随时间变化
3. **错误率图** - 错误率随时间变化趋势

### 统计表格
- 读操作：总量、成功数、失败数、TPS、P50/P90/P95/P99/Max 延迟
- 写操作：总量、成功数、失败数、TPS、P50/P90/P95/P99/Max 延迟

## 部署指南

### 方式一：直接部署到压测机

```bash
# 1. 确保压测机有 JDK 11+
java -version

# 2. 上传 jar 到压测机
scp target/nacos-stresstest-1.0.0.jar user@stress-host:/opt/nacos-stress/

# 3. 运行压测
ssh user@stress-host
cd /opt/nacos-stress
java -jar nacos-stresstest-1.0.0.jar -s <nacos-server>:8848 -t 50 -d 300
```

### 方式二：Docker 部署

```dockerfile
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/nacos-stresstest-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# 构建镜像
docker build -t nacos-stresstest .

# 运行
docker run --rm -v $(pwd)/reports:/app/reports \
  nacos-stresstest -s 192.168.1.100:8848 -t 20 -d 120 -o /app/reports/report.html
```

### 方式三：在 EC2/ECS 上运行

```bash
# EC2 上安装 JDK
sudo yum install -y java-11-amazon-corretto

# 或 Amazon Linux 2023
sudo dnf install -y java-11-amazon-corretto

# 运行（建议使用与 Nacos 同 VPC 的实例以减少网络延迟）
java -Xmx2g -jar nacos-stresstest-1.0.0.jar \
  -s nacos-cluster.internal:8848 \
  -t 100 -d 600 --configs 500
```

## 压测建议

### 典型场景配置

| 场景 | 线程数 | 配置项 | 时长 | 读写比 |
|------|--------|--------|------|--------|
| 轻量验证 | 5 | 50 | 30s | 80:20 |
| 标准压测 | 20 | 200 | 120s | 70:30 |
| 高并发测试 | 100 | 500 | 300s | 70:30 |
| 写入密集 | 50 | 200 | 120s | 30:70 |
| 纯读测试 | 100 | 1000 | 180s | 100:0 |

### 注意事项

1. **网络位置** - 压测机应与 Nacos Server 在同一内网/VPC，避免网络延迟影响结果
2. **JVM 内存** - 高并发时建议增加堆内存：`java -Xmx4g -jar ...`
3. **Nacos 集群** - 集群模式下的地址格式：`192.168.1.1:8848,192.168.1.2:8848`
4. **预热时间** - 建议预热 10 秒以上，让 JIT 充分优化
5. **清理数据** - 压测创建的配置会在 `STRESS_TEST_GROUP` 分组下，可按组清理
6. **操作系统调优** - 高并发时注意调大文件描述符限制：`ulimit -n 65535`

## 项目结构

```
nacos-stresstest-her/
├── pom.xml                          # Maven 构建文件
├── README.md                        # 本文档
├── config/
│   └── stress-test.properties.example  # 配置示例
├── scripts/
│   ├── run.sh                       # Linux/Mac 启动脚本
│   └── run.bat                      # Windows 启动脚本
└── src/main/java/com/nacos/stresstest/
    ├── NacosStressTestMain.java     # 程序入口
    ├── StressTestConfig.java        # 配置参数
    ├── StressTestRunner.java        # 核心压测执行器
    ├── MetricsCollector.java        # 指标采集器
    └── ReportGenerator.java         # HTML 报告生成器
```

## License

MIT
