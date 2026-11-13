# Maven 配置契约（v2.0）

## 1. 适用范围

本契约适用于 `SeckillCloud` 所有模块的 `pom.xml`。任何新增依赖、升级版本、调整插件配置的操作，均须遵守本契约。违反契约的配置不得合入主分支。

## 2. 版本管理：`${revision}` 单点模式

### 2.1 唯一版本入口

**全项目所有模块共享同一个版本号，由根 POM 的 `<properties>` 中的 `<revision>` 属性唯一定义。** 根 POM 的 `<version>`、所有子模块 `<parent>` 的 `<version>`、以及模块间依赖的 `<version>`，一律引用 `${revision}` 或 `${project.version}`，不得出现任何硬编码的模块版本号。

```xml
<!-- 根 POM -->
<version>${revision}</version>
<properties>
    <revision>0.0.1</revision>
</properties>

<!-- 子模块 POM -->
<parent>
    <groupId>org.sangoria</groupId>
    <artifactId>SeckillCloud</artifactId>
    <version>${revision}</version>
</parent>
<artifactId>seckill-common</artifactId>
<!-- 自身 version 省略，继承 parent 的 ${revision} -->

<!-- 模块间依赖 -->
<dependency>
    <groupId>org.sangoria</groupId>
    <artifactId>seckill-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

模块间依赖统一使用 `${project.version}`，它在构建期解析为 `${revision}` 展开后的实际值，语义最清晰。子模块自身 `<version>` 省略，默认继承 parent 版本，避免重复声明。

### 2.2 版本升级操作

升级全项目版本时，**只修改根 POM `<properties>` 中的 `<revision>` 值**，不修改任何子模块。修改后执行 `mvn clean install -DskipTests` 验证全量构建通过。禁止使用 `versions:set` 逐模块改写——那会破坏 `${revision}` 的引用结构。

### 2.3 `flatten-maven-plugin` 为必需配套

引入 `${revision}` 后，`install`/`deploy` 出去的 POM 必须展开占位符，否则下游项目无法解析版本。根 POM 的 `<build><plugins>` 中必须声明 `flatten-maven-plugin`，所有子模块自动继承执行：

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>flatten-maven-plugin</artifactId>
    <version>${flatten-maven-plugin.version}</version>
    <configuration>
        <updatePomFile>true</updatePomFile>
        <flattenMode>resolveCiFriendliesOnly</flattenMode>
    </configuration>
    <executions>
        <execution>
            <id>flatten</id>
            <phase>process-resources</phase>
            <goals><goal>flatten</goal></goals>
        </execution>
        <execution>
            <id>flatten.clean</id>
            <phase>clean</phase>
            <goals><goal>clean</goal></goals>
        </execution>
    </executions>
</plugin>
```

`resolveCiFriendliesOnly` 模式只替换 `${revision}`、`${sha1}`、`${changelist}` 等 CI 友好变量，保留 POM 其余结构，是最省心的模式。`flatten-maven-plugin` 的版本同样在根 POM `<properties>` 中用属性管理。

> **注意**：`${revision}` 依赖 Maven 3.5.0+（当前 3.9.9 满足）。IDEA 对 `${revision}` 的模块依赖解析在较新版本才完善，若出现子模块间依赖在 IDEA 中标红但命令行构建正常，属 IDE 解析问题，以 Maven 命令行为准。

## 3. 版本权威来源

根 POM 的 `<properties>` 是所有第三方依赖版本号的唯一声明位置。子模块不得出现任何硬编码的依赖版本号或插件版本号。BOM 导入（`<scope>import</scope>`）覆盖的依赖，不得在 `dependencyManagement` 中重复覆盖版本，除非有明确的兼容性理由并记录在案。

当前版本属性基线：

| 属性 | 值 | 说明 |
|---|---|---|
| `revision` | `0.0.1` | 全项目模块版本，单点升级 |
| `spring-boot.version` | `4.0.1` | 已从 4.0.0 升级，消除 MyBatis-Plus 传递请求的版本冲突 |
| `spring-cloud.version` | `2025.1.0` | 对齐 Spring Boot 兼容矩阵 |
| `spring-cloud-alibaba.version` | `2025.1.0.0` | 对齐 Spring Boot 兼容矩阵 |
| `mybatis-plus.version` | `3.5.17` | 传递请求 Spring Boot 4.0.1 组件 |
| `lombok.version` | `1.18.42` | 与 Spring Boot 4.0.1 管理的版本一致 |
| `flatten-maven-plugin.version` | `1.8.0` | 配合 `${revision}` |
| `maven-enforcer-plugin.version` | `3.6.2` | 版本收敛校验 |

## 4. 依赖 scope 规范

| 依赖类型 | scope | 说明 |
|---|---|---|
| Lombok | `provided` | 编译期需要，运行时不打包。**全项目统一 `provided`，禁止使用 `optional` 替代**。 |
| `jakarta.servlet-api` | `provided` | 由容器提供运行时实现。**`provided` 不传递，直接引用该 API 的模块必须各自声明**。 |
| 纯编译期注解处理器 | `provided` 或仅列入 `annotationProcessorPaths` | 不进入运行时类路径。 |
| Spring Boot 启动类模块的 starter | 默认 `compile` | 正常传递。 |
| 测试依赖 | `test` | 不得出现在 `compile` 作用域中。 |

`optional` 只用于表达“此依赖对下游模块可选”的语义，不得用于替代 `provided`。`provided` 不传递是 Maven 的硬约束：模块 A 声明了 `provided` 依赖，模块 B 的源码直接引用了该依赖中的类，模块 B 必须自行声明同样的 `provided` 依赖。

## 5. 注解处理器规范

所有使用 Lombok（或任何注解处理器）的模块，必须在**自身** POM 的 `<build><plugins>` 中声明 `maven-compiler-plugin`，不写版本号和 `<configuration>`，完全继承根 POM 的 `pluginManagement`：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

根 POM 的 `pluginManagement` 中统一配置 `<annotationProcessorPaths>`，Lombok 版本**推荐省略 `<version>`**，由 `dependencyManagement` 解析（要求 `maven-compiler-plugin` ≥ 3.12.0）。若因故显式写版本，必须与 `dependencyManagement` 中的 `lombok.version` 严格一致，并在升级时同步修改两处。

**禁止依赖 Maven 的隐式注解处理器发现机制。** JDK 21 + `maven-compiler-plugin` 3.13.0 组合下隐式发现不可靠，`annotationProcessorPaths` 是唯一可靠的注册方式。`pluginManagement` 只定义配置，不激活插件；子模块不声明 `maven-compiler-plugin`，注解处理器就不会运行。

## 6. 构建插件规范

- `spring-boot-maven-plugin` **仅允许出现在包含主类的启动模块**的 `<build><plugins>` 中。纯库模块（`common`、`api` 等）不得声明，否则 `repackage` 绑定到 `package` 阶段会因找不到主类而失败。版本继承根 POM 的 `pluginManagement`，不写 `<version>`。
- `maven-enforcer-plugin` 和 `flatten-maven-plugin` 声明在**根 POM 的 `<build><plugins>`** 中，所有子模块继承执行。
- `maven-compiler-plugin` 的版本与配置在根 POM `pluginManagement`，由需要的子模块显式声明激活。
- 新增构建插件时，先在根 POM `<pluginManagement>` 中锁定版本，再在需要它的模块中声明。禁止在子模块中直接写插件版本号。

## 7. 版本收敛强制校验

根 POM 的 `<build><plugins>` 中激活 `maven-enforcer-plugin`，在 `validate` 阶段执行 `dependencyConvergence` 和 `requireUpperBoundDeps`：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>${maven-enforcer-plugin.version}</version>
    <executions>
        <execution>
            <id>enforce-convergence</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <dependencyConvergence />
                    <requireUpperBoundDeps />
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

任何导致传递依赖版本分裂的变更，必须在 CI 中阻断。Enforcer 报错时的处理优先级：排除冲突依赖 > 在 `dependencyManagement` 中统一版本 > 调整引入方式。**禁止通过关闭 Enforcer 规则来“解决”问题。**

Enforcer 报出的 `RequireUpperBoundDeps` 典型场景：`dependencyManagement` 锁定的版本低于某传递依赖请求的版本。处理方式是提升对应 BOM 的版本属性（如 `spring-boot.version`），让 BOM 统一拉齐，而非在 `dependencyManagement` 中逐项覆盖。

## 8. 新增依赖操作清单

1. 在根 POM `<properties>` 中定义版本属性（若该依赖不在已有 BOM 管理范围）。
2. 在根 POM `<dependencyManagement>` 中声明坐标和版本（或确认 BOM 已覆盖）。
3. 在**需要该依赖的模块**的 `<dependencies>` 中声明，**不写 `<version>`**。
4. 按依赖类型选择正确的 scope（`provided` / `test` / 默认 `compile`）。
5. 若该依赖含注解处理器，同步在根 POM 的 `annotationProcessorPaths` 中加入路径，并在模块中声明 `maven-compiler-plugin`。
6. 执行 `mvn clean validate` 确认 Enforcer 通过，再执行 `mvn clean install -DskipTests` 确认全量编译通过。

## 9. 版本升级操作清单

1. 第三方依赖：只修改根 POM `<properties>` 中对应属性。
2. 项目自身版本：只修改根 POM `<properties>` 中的 `<revision>`。
3. 若 `annotationProcessorPaths` 中显式写了版本，同步修改。
4. 执行 `mvn clean validate`（Enforcer 暴露版本升级引入的传递依赖冲突）。
5. 执行 `mvn clean install -DskipTests`，确认全量编译通过。
6. 升级 Spring Boot / Spring Cloud 的 BOM 版本时，先对齐官方兼容性矩阵，再逐项处理传递依赖变化。

## 10. 禁止事项

- **禁止**在子模块中出现硬编码的依赖版本号、插件版本号或模块版本号。
- **禁止**在纯库模块中声明 `spring-boot-maven-plugin`。
- **禁止**用 `optional` 替代 `provided` 声明 Lombok 或 Servlet API 等编译期依赖。
- **禁止**依赖 Maven 的隐式注解处理器发现机制。
- **禁止**用 `versions:set` 逐模块改写版本，破坏 `${revision}` 引用结构。
- **禁止**通过关闭 Enforcer 规则绕过版本冲突。
- **禁止**用 IDEA 的编译结果替代 Maven 命令行的全量构建验证。

## 11. 验证清单

```bash
# 1. 版本收敛与 POM 结构校验
mvn validate

# 2. 全量清理并编译
mvn clean install -DskipTests

# 3. 确认 seckill-common 编译类路径上 Lombok 与 Servlet API 存在
#    PowerShell 下 -Dincludes 的逗号需整体加引号
mvn -pl seckill-common dependency:tree "-Dincludes=org.projectlombok:lombok,jakarta.servlet:jakarta.servlet-api"

# 4. 确认纯库模块未声明 spring-boot-maven-plugin，启动模块已声明
mvn help:effective-pom -pl seckill-common | Select-String spring-boot-maven-plugin
mvn help:effective-pom -pl seckill-user   | Select-String spring-boot-maven-plugin

# 5. 确认 install 后的 POM 已展开 ${revision}
mvn -pl seckill-common install -DskipTests
Get-Content "$env:USERPROFILE\.m2\repository\org\sangoria\seckill-common\0.0.1\seckill-common-0.0.1.pom" | Select-String revision
```

第 5 步应无输出——若安装后的 POM 中仍出现 `${revision}` 字面量，说明 `flatten-maven-plugin` 未生效，必须排查后重新 `install`。

## 附录：根 POM 关键骨架

```xml
<project ...>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.sangoria</groupId>
    <artifactId>SeckillCloud</artifactId>
    <version>${revision}</version>
    <packaging>pom</packaging>

    <modules><!-- ... --></modules>

    <properties>
        <revision>0.0.1</revision>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <spring-boot.version>4.0.1</spring-boot.version>
        <spring-cloud.version>2025.1.0</spring-cloud.version>
        <spring-cloud-alibaba.version>2025.1.0.0</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <lombok.version>1.18.42</lombok.version>

        <flatten-maven-plugin.version>1.8.0</flatten-maven-plugin.version>
        <maven-enforcer-plugin.version>3.6.2</maven-enforcer-plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- BOM imports ... -->
            <dependency>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
                <scope>provided</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <release>${maven.compiler.release}</release>
                        <parameters>true</parameters>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <!-- version 省略，由 dependencyManagement 解析 -->
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>

        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>flatten-maven-plugin</artifactId>
                <version>${flatten-maven-plugin.version}</version>
                <configuration>
                    <updatePomFile>true</updatePomFile>
                    <flattenMode>resolveCiFriendliesOnly</flattenMode>
                </configuration>
                <executions>
                    <execution>
                        <id>flatten</id>
                        <phase>process-resources</phase>
                        <goals><goal>flatten</goal></goals>
                    </execution>
                    <execution>
                        <id>flatten.clean</id>
                        <phase>clean</phase>
                        <goals><goal>clean</goal></goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>${maven-enforcer-plugin.version}</version>
                <executions>
                    <execution>
                        <id>enforce-convergence</id>
                        <goals><goal>enforce</goal></goals>
                        <configuration>
                            <rules>
                                <dependencyConvergence />
                                <requireUpperBoundDeps />
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

**契约变更记录**

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | — | 初始契约：Lombok 处理器、scope 规范、Enforcer 校验 |
| v2.0 | 2026-11-13 | 版本管理改为 `${revision}` 单点模式；Spring Boot 升级至 4.0.1 消除 Enforcer 冲突；新增 `flatten-maven-plugin` 配套；补充模块间依赖使用 `${project.version}` 的规则 |