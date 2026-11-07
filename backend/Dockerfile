# ---- 构建阶段：Maven + JDK 21 打出可执行 jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- 运行阶段：仅 JRE 21，减小镜像体积 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/interview-system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Render 注入 PORT 环境变量；免费内存有限，限制堆上限避免 OOM
ENTRYPOINT ["sh", "-c", "java -Xmx256m -Dserver.port=${PORT:-8080} -jar app.jar"]
