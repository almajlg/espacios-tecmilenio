# 1. Etapa de compilación
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN mkdir -p bin && javac -encoding UTF-8 -d bin -sourcepath backend/src backend/src/com/universidad/espacios/Main.java

# 2. Etapa de ejecución ligera
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/bin ./bin
COPY --from=build /app/backend/campus_espacios_data.json ./backend/campus_espacios_data.json
COPY --from=build /app/frontend ./frontend

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-Dfile.encoding=UTF-8", "-cp", "bin", "com.universidad.espacios.Main"]
