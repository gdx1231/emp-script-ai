---
name: dual-servlet-profiles
description: 通过 Maven profiles 构建同时支持 javax.servlet-api 和 jakarta.servlet-api 的双版本 JAR，包含构建目录切换、finalName 自定义、依赖 jar 复制重命名
source: auto-skill
extracted_at: '2026-06-29T08:46:44.229Z'
---

# Dual Servlet Profiles (javax / jakarta)

当项目需要在单代码仓库中同时产出 `javax.servlet` 和 `jakarta.servlet` 两个版本的 JAR 时，通过 Maven profiles 实现。

## 适用场景

- 上游框架仍使用 Tomcat 8/9（`javax.servlet`），下游迁移到 Tomcat 10+（`jakarta.servlet`）
- 希望用同一份源码编译出两个不同 servlet API 版本的构建产物
- 输出目录和 JAR 文件名需要区分（如 `target/` vs `target17/`）

## 实现步骤

### 1. pom.xml 删除硬编码 servlet 依赖

将原来写在 `<dependencies>` 中的 servlet-api 删除，改为 profile 内的依赖。

### 2. 创建 javax profile（默认激活）

```xml
<profile>
    <id>javax</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <dependencies>
        <dependency>
            <groupId>javax.servlet</groupId>
            <artifactId>javax.servlet-api</artifactId>
            <version>4.0.1</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</profile>
```

- `activeByDefault` 确保不传 `-P` 时默认编译 javax 版本
- `scope=provided`：运行时由 servlet 容器提供

### 3. 创建 jakarta profile（输出到独立目录）

```xml
<profile>
    <id>jakarta</id>
    <build>
        <directory>target17</directory>     <!-- 独立的输出目录 -->
        <finalName>emp-script-ai-jdk17-${project.version}</finalName>
    </build>
    <dependencies>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</profile>
```

关键点：
- `<directory>` 覆盖默认的 `target/`，使 jakarta 产物不会覆盖 javax 产物
- `<finalName>` 修改 jar 文件名，添加 `-jdk17` 后缀便于区分

### 4. （可选）复制并重命名依赖 jar

当依赖的 artifact 也需要带 `-jdk17` 后缀时，在 jakarta profile 中添加：

```xml
<plugins>
    <!-- 复制特定依赖 jar 到输出目录 -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
            <execution>
                <id>copy-dep-emp</id>
                <phase>package</phase>
                <goals><goal>copy-dependencies</goal></goals>
                <configuration>
                    <outputDirectory>${project.build.directory}</outputDirectory>
                    <includeArtifactIds>emp-script,emp-script-utils</includeArtifactIds>
                    <excludeTransitive>true</excludeTransitive>
                </configuration>
            </execution>
        </executions>
    </plugin>
    <!-- 重命名为 *-jdk17-<version>.jar -->
    <plugin>
        <artifactId>maven-antrun-plugin</artifactId>
        <executions>
            <execution>
                <id>rename-dep-emp-jdk17</id>
                <phase>package</phase>
                <configuration>
                    <target>
                        <move todir="${project.build.directory}" verbose="true">
                            <fileset dir="${project.build.directory}">
                                <include name="emp-script-*.jar" />
                                <include name="emp-script-utils-*.jar" />
                                <exclude name="${project.build.finalName}.jar" />
                                <exclude name="*-last.jar" />
                            </fileset>
                            <mapper type="regexp"
                                from="^(emp-script(?:-utils)?)-(\d+\.\d+\.\d+.*?)(?:-jdk17)?\.jar$$"
                                to="\1-jdk17-\2.jar" />
                        </move>
                    </target>
                </configuration>
                <goals><goal>run</goal></goals>
            </execution>
        </executions>
    </plugin>
</plugins>
```

注意点：
- 依赖 jar 的版本已含 `-jdk17`（如 `emp-script-1.1.10-jdk17.jar`）时，正则 `(?:-jdk17)?` 会 strip 掉再重新添加，确保统一为 `emp-script-jdk17-1.1.10.jar` 格式
- 排除 `${project.build.finalName}.jar` 和 `*-last.jar`，避免误处理主 jar

### 5. 修复硬编码的路径

主 build 中若使用了硬编码 `target/` 路径（如 `copy-rename-maven-plugin` 的 `sourceFile`），应改为 `${project.build.directory}/`，以便 profile 切换目录时自动适应。

## 构建命令

```bash
# javax 版本 → target/emp-script-ai-1.0.0.jar
mvn -DskipTests package

# jakarta 版本 → target17/emp-script-ai-jdk17-1.0.0.jar
mvn -Pjakarta -DskipTests package

# 可与 release profile 叠加
mvn -Pjakarta,release -B package
```

---

## 方案二：分离为两个独立 pom 文件

当不希望记忆 `-P` 参数时，可将双 profile 拆分为两个独立 pom 文件，各专注一个 servlet 版本：

| 文件 | servlet 版本 | 输出目录 |
|------|-------------|----------|
| `pom.xml` | javax （默认激活） | `target/` |
| `pomjdk17.xml` | jakarta （默认激活） | `target17/` |

### 操作步骤

1. 复制 `pom.xml` → `pomjdk17.xml`
2. `pom.xml` 只保留 javax profile，去除 jakarta profile
3. `pomjdk17.xml` 只保留 jakarta profile（加 `activeByDefault`），去除 javax profile

### 构建命令

```bash
# javax 版本 → target/emp-script-ai-1.0.0.jar
mvn -f pom.xml -DskipTests package

# jakarta 版本 → target17/emp-script-ai-jdk17-1.0.0.jar
mvn -f pomjdk17.xml -DskipTests package
```

### 注意事项

- `pomjdk17.xml` 应与 `pom.xml` 保持相同的 `<groupId>`、`<artifactId>`、`<version>`，Maven 通过 `<directory>` 和 `<finalName>` 区分产物
- jakarta profile 内若包含 `maven-dependency-plugin`（复制依赖 jar）和 `maven-antrun-plugin`（重命名），确保它们的 execution id 不与主 build 中的 execution 冲突
- `.gitignore` 需添加 `/target17/`

## 已知陷阱

| 陷阱 | 解决 |
|------|------|
| `copy-rename-maven-plugin` 硬编码 `target/` | 替换为 `${project.build.directory}/` |
| 依赖版本已含 `jdk17` 字符串，rename 后双倍 | 正则用 `(?:-jdk17)?` 可选匹配并 strip |
| 多个 plugin execution ID 冲突（如两次 `copy-lib-src-webapps`） | 给 profile 内的 execution 不同 ID |
| `maven-antrun-plugin` 在主 build 和 profile 中同时声明 | 版本号只需在主 build 声明一次，profile 内用 `<artifactId>maven-antrun-plugin</artifactId>`（无 version）自动继承 |
